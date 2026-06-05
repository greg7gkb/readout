package com.greg7gkb.readout.debug

import android.content.Intent
import android.util.Log
import com.greg7gkb.readout.audio.TtsEngine
import com.greg7gkb.readout.llm.LlmClient
import com.greg7gkb.readout.screen.ScreenReadResult
import com.greg7gkb.readout.screen.ScreenReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val commands: Map<String, DebugCommand> = mapOf(
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
            val start = System.currentTimeMillis()
            val inspection = when (val result = screenReader.inspect()) {
                is ScreenReadResult.Available -> result.inspection
                is ScreenReadResult.Unavailable -> {
                    // Match the orchestrator's fail-closed behavior: spoken
                    // deterministic message, no LLM call, no tokens spent on
                    // an empty screen.
                    val msg = unavailableMessage(result.reason)
                    Log.w(TAG, "ask unavailable reason=${result.reason} q=\"$question\"")
                    if (speak) ttsEngine.speak(msg)
                    return@DebugCommand
                }
            }
            val inspectMs = System.currentTimeMillis() - start
            Log.i(TAG, "ask q=\"$question\" pkg=${inspection.foregroundPackage} nodes=${inspection.nodes.size} inspectMs=$inspectMs")
            val answer = llmClient.answer(
                question = question,
                screen = inspection,
                appName = inspection.foregroundPackage,
            )
            Log.i(TAG, "ask answer=\"${answer.text}\" llmMs=${answer.latencyMillis}")
            if (speak) ttsEngine.speak(answer.text)
        },
    )

    private fun unavailableMessage(reason: ScreenReadResult.Unavailable.Reason): String = when (reason) {
        ScreenReadResult.Unavailable.Reason.SERVICE_NOT_BOUND ->
            "I can't read the screen right now. Please re-enable accessibility access for Readout in Settings."
        ScreenReadResult.Unavailable.Reason.ROOT_NOT_AVAILABLE ->
            "I can't see the screen right now. Try again in a moment."
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
        private const val TAG = "Readout/Debug"
    }
}

fun interface DebugCommand {
    suspend fun execute(intent: Intent)
}
