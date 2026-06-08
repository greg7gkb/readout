package com.greg7gkb.readout.debug

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import com.greg7gkb.readout.audio.TtsEngine
import com.greg7gkb.readout.llm.LlmClient
import com.greg7gkb.readout.screen.ReadoutAccessibilityServiceHolder
import com.greg7gkb.readout.screen.ScreenReadResult
import com.greg7gkb.readout.screen.ScreenReader
import com.greg7gkb.readout.wake.ManualActivator
import com.greg7gkb.readout.wake.WakeWordEngine
import com.greg7gkb.readout.wake.WindowStateActivator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generalized dispatcher for ADB- and notification-driven debug commands.
 *
 * Adding a new command is a one-line entry in [commands] — a name and a
 * [DebugCommand] lambda. The lambda gets the originating [Intent] so it
 * can read extras (e.g. `--es app com.example.weather`) for future
 * commands that need parameters.
 *
 * Commands run on a singleton-owned IO scope: invocations are
 * fire-and-forget from the receiver's point of view (which is on the
 * main thread and must return quickly).
 *
 * Invocation paths:
 *   - [DebugCommandReceiver] receives `am broadcast` from adb
 *   - The session-notification "Inspect" action fires the same broadcast
 */
@Singleton
class DebugCommandDispatcher @Inject constructor(
    private val screenReader: ScreenReader,
    private val llmClient: LlmClient,
    private val ttsEngine: TtsEngine,
    private val manualActivator: ManualActivator,
    private val windowStateActivator: WindowStateActivator,
    private val accessibilityServiceHolder: ReadoutAccessibilityServiceHolder,
    private val wakeWordEngine: WakeWordEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Currently-running `wake start` collector, if any. Held here (not in a
     *  command-local val) so a follow-up `wake stop` can cancel it. Reads and
     *  writes only happen from the IO scope; race window is benign — at worst
     *  a second `start` will log "already running" and no-op. */
    @Volatile
    private var wakeCollectorJob: Job? = null

    private val commands: Map<String, DebugCommand> = mapOf(
        // `trigger` fires a tap-to-talk activation. Routing:
        //   - Notification "Trigger" action fires this broadcast while the
        //     shade is still the focused window. The dispatcher arms the
        //     [WindowStateActivator] and explicitly dismisses the shade —
        //     the activation fires once the underlying app's window becomes
        //     active (so the orchestrator inspects the real app, not the
        //     shade's view tree).
        //   - ADB broadcast or any other path with no shade open fires the
        //     activation immediately via [ManualActivator].
        CMD_TRIGGER to DebugCommand { _ ->
            val service = accessibilityServiceHolder.service.value
            val focused = service?.rootInActiveWindow?.packageName?.toString()
            if (service != null && focused == SYSTEM_UI_PACKAGE) {
                Log.i(TAG, "trigger: shade is open — arming and dismissing")
                windowStateActivator.arm()
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
                Log.i(TAG, "trigger: firing immediately focused=$focused")
                manualActivator.trigger(ManualActivator.Source.NotificationAction)
            }
        },
        CMD_INSPECT to DebugCommand { _ ->
            when (val result = screenReader.inspect()) {
                is ScreenReadResult.Available -> {
                    val inspection = result.inspection
                    Log.i(
                        TAG,
                        "inspect pkg=${inspection.foregroundPackage} nodes=${inspection.nodes.size}",
                    )
                    inspection.nodes.forEachIndexed { i, node ->
                        Log.i(TAG, "  [$i] text=${node.text} desc=${node.contentDescription} cls=${node.className}")
                    }
                }
                is ScreenReadResult.Unavailable ->
                    Log.w(TAG, "inspect unavailable reason=${result.reason}")
            }
        },
        // `ask` bypasses STT for emulator validation and for Step 6's
        // query-variants pass (run the same question across providers without
        // re-recording the transcript each time). Skips activation + STT;
        // otherwise mirrors the orchestrator: inspect -> LlmClient -> TTS.
        //
        //   adb shell am broadcast \
        //     -a com.greg7gkb.readout.action.DEBUG_COMMAND \
        //     --es cmd ask --es q "what version of Android am I running?" \
        //     -p com.greg7gkb.readout.cloud
        //
        // Add `--ez speak false` to skip TTS (useful for batch runs).
        CMD_ASK to DebugCommand { intent ->
            val question = intent.getStringExtra(EXTRA_QUESTION)
            if (question.isNullOrBlank()) {
                Log.w(TAG, "ask: missing --es $EXTRA_QUESTION extra")
                return@DebugCommand
            }
            val speak = intent.getBooleanExtra(EXTRA_SPEAK, true)
            val t0 = System.currentTimeMillis()
            var inspectMs: Long? = null
            var llmMs: Long? = null
            var ttsMs: Long? = null
            val inspectStart = System.currentTimeMillis()
            val inspection = when (val result = screenReader.inspect()) {
                is ScreenReadResult.Available -> {
                    inspectMs = System.currentTimeMillis() - inspectStart
                    result.inspection
                }
                is ScreenReadResult.Unavailable -> {
                    inspectMs = System.currentTimeMillis() - inspectStart
                    // Match the orchestrator's fail-closed behavior: spoken
                    // deterministic message, no LLM call, no tokens spent on
                    // an empty screen.
                    val msg = unavailableMessage(result.reason)
                    Log.w(TAG, "ask unavailable reason=${result.reason} q=\"$question\"")
                    if (speak) {
                        val ttsStart = System.currentTimeMillis()
                        ttsEngine.speak(msg)
                        ttsMs = System.currentTimeMillis() - ttsStart
                    }
                    Log.i(
                        TAG,
                        "ask summary " + formatSummary(t0, inspectMs, llmMs, ttsMs) +
                            " reason=${result.reason}",
                    )
                    return@DebugCommand
                }
            }
            Log.i(TAG, "ask q=\"$question\" pkg=${inspection.foregroundPackage} nodes=${inspection.nodes.size} inspectMs=$inspectMs")
            val llmStart = System.currentTimeMillis()
            val answer = llmClient.answer(
                question = question,
                screen = inspection,
                appName = inspection.foregroundPackage,
            )
            llmMs = System.currentTimeMillis() - llmStart
            Log.i(TAG, "ask answer=\"${answer.text}\" llmMs=$llmMs")
            if (speak) {
                val ttsStart = System.currentTimeMillis()
                ttsEngine.speak(answer.text)
                ttsMs = System.currentTimeMillis() - ttsStart
            }
            Log.i(TAG, "ask summary " + formatSummary(t0, inspectMs, llmMs, ttsMs))
        },
        // `wake start` and `wake stop`: ad-hoc validation entry for the OWW
        // engine before the Phase 4.5 service-owned lifecycle lands. Starts
        // collecting WakeEvents and logging each detection; stop cancels the
        // collector and releases AudioRecord.
        //
        //   adb shell am broadcast \
        //     -a com.greg7gkb.readout.action.DEBUG_COMMAND --es cmd wake-start \
        //     -p com.greg7gkb.readout.cloud
        //
        // Say "Hey Jarvis" — expect a Readout/Wake "DETECTED" log line.
        CMD_WAKE_START to DebugCommand { _ ->
            if (wakeCollectorJob?.isActive == true) {
                Log.w(TAG, "wake-start: already running")
                return@DebugCommand
            }
            Log.i(TAG, "wake-start: collecting WakeEvents — say 'Hey Jarvis'")
            wakeCollectorJob = scope.launch {
                try {
                    wakeWordEngine.events().collect { event ->
                        Log.i(
                            TAG,
                            "wake event: ts=${event.timestampMillis} conf=${event.confidence}",
                        )
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "wake collector failed", t)
                } finally {
                    Log.i(TAG, "wake collector exited")
                }
            }
        },
        CMD_WAKE_STOP to DebugCommand { _ ->
            val job = wakeCollectorJob
            if (job == null || !job.isActive) {
                Log.w(TAG, "wake-stop: nothing running")
                wakeCollectorJob = null
                return@DebugCommand
            }
            Log.i(TAG, "wake-stop: cancelling collector")
            job.cancel()
            wakeCollectorJob = null
        },
    )

    private fun unavailableMessage(reason: ScreenReadResult.Unavailable.Reason): String = when (reason) {
        ScreenReadResult.Unavailable.Reason.SERVICE_NOT_BOUND ->
            "I can't read the screen right now. Please re-enable accessibility access for Readout in Settings."
        ScreenReadResult.Unavailable.Reason.ROOT_NOT_AVAILABLE ->
            "I can't see the screen right now. Try again in a moment."
    }

    /** Same shape as [SessionOrchestrator]'s summary minus the STT stage (ask
     *  skips activation + STT). Stages that didn't run are omitted. */
    private fun formatSummary(
        t0: Long,
        inspectMs: Long?,
        llmMs: Long?,
        ttsMs: Long?,
    ): String = buildString {
        inspectMs?.let { append("inspect=${it}ms ") }
        llmMs?.let { append("llm=${it}ms ") }
        ttsMs?.let { append("tts=${it}ms ") }
        append("total=${System.currentTimeMillis() - t0}ms")
    }

    fun dispatch(cmd: String?, intent: Intent) {
        if (cmd == null) {
            Log.w(TAG, "missing $EXTRA_CMD extra")
            return
        }
        val handler = commands[cmd]
        if (handler == null) {
            Log.w(TAG, "unknown cmd=$cmd. known=${commands.keys}")
            return
        }
        scope.launch { handler.execute(intent) }
    }

    companion object {
        const val EXTRA_CMD = "cmd"
        const val EXTRA_QUESTION = "q"
        const val EXTRA_SPEAK = "speak"
        const val CMD_INSPECT = "inspect"
        const val CMD_ASK = "ask"
        const val CMD_TRIGGER = "trigger"
        const val CMD_WAKE_START = "wake-start"
        const val CMD_WAKE_STOP = "wake-stop"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val TAG = "Readout/Debug"
    }
}

fun interface DebugCommand {
    suspend fun execute(intent: Intent)
}
