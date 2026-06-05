package com.greg7gkb.readout.session

import android.util.Log
import com.greg7gkb.readout.audio.SpeechRecognizer
import com.greg7gkb.readout.audio.TtsEngine
import com.greg7gkb.readout.common.model.Activation
import com.greg7gkb.readout.common.model.Session
import com.greg7gkb.readout.llm.LlmClient
import com.greg7gkb.readout.screen.ScreenReadResult
import com.greg7gkb.readout.screen.ScreenReader
import com.greg7gkb.readout.wake.Activator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 pipeline: each [Activation] runs through
 *   SpeechRecognizer → ScreenReader → LlmClient → TtsEngine,
 * emitting [SessionState] transitions for UI consumption along the way.
 *
 * Owned by the foreground service (Step 11) — the service calls [start]
 * with its own scope and cancels the scope on shutdown.
 */
@Singleton
class SessionOrchestrator @Inject constructor(
    private val activator: Activator,
    private val speechRecognizer: SpeechRecognizer,
    private val screenReader: ScreenReader,
    private val llmClient: LlmClient,
    private val ttsEngine: TtsEngine,
) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    /**
     * True between a successful [start] and the corresponding scope-completion.
     * Drives UI affordances ("Start session" vs "Stop session") so they reflect
     * actual orchestrator liveness even when the service was torn down via the
     * notification action rather than the activity button.
     */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var collectionJob: Job? = null

    /**
     * Begin orchestrating in [scope]. Each Activation from the configured
     * [Activator] runs the full pipeline once. Idempotent — repeat calls
     * return the existing job rather than spawning duplicate collectors.
     * Cancel [scope] or call [stop] to end.
     */
    fun start(scope: CoroutineScope): Job {
        collectionJob?.takeIf { it.isActive }?.let { return it }
        val job = scope.launch {
            activator.activations().collect { activation ->
                runPipeline(activation)
            }
        }
        collectionJob = job
        _isRunning.value = true
        job.invokeOnCompletion {
            // Fires whether stop() was called or [scope] was cancelled out from
            // under us (service destroyed). Reset everything visible to the UI.
            _isRunning.value = false
            _state.value = SessionState.Idle
        }
        return job
    }

    /** Cancel the active collector if any. Safe to call when not started. */
    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
    }

    private suspend fun runPipeline(activation: Activation) {
        val session = Session()
        Log.i(TAG, "[${session.id}] start via $activation")
        val t0 = System.currentTimeMillis()
        var sttMs: Long? = null
        var inspectMs: Long? = null
        var llmMs: Long? = null
        var ttsMs: Long? = null
        try {
            _state.value = SessionState.Listening(session)
            val transcript = speechRecognizer.listen().first { it.isFinal }
            sttMs = System.currentTimeMillis() - t0
            Log.i(TAG, "[${session.id}] transcript=${transcript.text}")

            _state.value = SessionState.Thinking(session, transcript.text)
            val inspectStart = System.currentTimeMillis()
            val inspection = when (val result = screenReader.inspect()) {
                is ScreenReadResult.Available -> {
                    inspectMs = System.currentTimeMillis() - inspectStart
                    result.inspection
                }
                is ScreenReadResult.Unavailable -> {
                    inspectMs = System.currentTimeMillis() - inspectStart
                    // Fail closed: speak a deterministic message and skip the LLM
                    // call. Letting the model improvise on an empty screen wastes
                    // tokens and risks a confident wrong answer.
                    val msg = unavailableMessage(result.reason)
                    Log.w(TAG, "[${session.id}] screen unavailable reason=${result.reason}")
                    _state.value = SessionState.Speaking(session, msg)
                    val ttsStart = System.currentTimeMillis()
                    ttsEngine.speak(msg)
                    ttsMs = System.currentTimeMillis() - ttsStart
                    _state.value = SessionState.Idle
                    Log.i(
                        TAG,
                        "[${session.id}] complete (screen unavailable) " +
                            formatSummary(t0, sttMs, inspectMs, llmMs, ttsMs) +
                            " reason=${result.reason}",
                    )
                    return
                }
            }
            val llmStart = System.currentTimeMillis()
            val answer = llmClient.answer(
                question = transcript.text,
                screen = inspection,
                appName = inspection.foregroundPackage,
            )
            llmMs = System.currentTimeMillis() - llmStart
            Log.i(TAG, "[${session.id}] answer=${answer.text} latencyMs=${answer.latencyMillis}")

            _state.value = SessionState.Speaking(session, answer.text)
            val ttsStart = System.currentTimeMillis()
            ttsEngine.speak(answer.text)
            ttsMs = System.currentTimeMillis() - ttsStart

            _state.value = SessionState.Idle
            Log.i(TAG, "[${session.id}] complete " + formatSummary(t0, sttMs, inspectMs, llmMs, ttsMs))
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            // Include whatever stage timings completed before the failure — the
            // partial summary tells you which stage was last to finish and
            // therefore most likely where the error came from.
            Log.w(TAG, "[${session.id}] failed: $msg " + formatSummary(t0, sttMs, inspectMs, llmMs, ttsMs))
            _state.value = SessionState.Error(session, msg)
        }
    }

    private fun unavailableMessage(reason: ScreenReadResult.Unavailable.Reason): String = when (reason) {
        ScreenReadResult.Unavailable.Reason.SERVICE_NOT_BOUND ->
            "I can't read the screen right now. Please re-enable accessibility access for Readout in Settings."
        ScreenReadResult.Unavailable.Reason.ROOT_NOT_AVAILABLE ->
            "I can't see the screen right now. Try again in a moment."
    }

    /**
     * One-line per-session timing summary. Stages that didn't run (because the
     * pipeline short-circuited or failed before reaching them) are omitted, so
     * the line tells you both what happened and how long each completed stage
     * took. `total` is always wall-clock from activation to whichever exit
     * point this is called from — useful for tracking the ~3s end-to-end
     * budget without summing per-stage values.
     */
    private fun formatSummary(
        t0: Long,
        sttMs: Long?,
        inspectMs: Long?,
        llmMs: Long?,
        ttsMs: Long?,
    ): String = buildString {
        sttMs?.let { append("stt=${it}ms ") }
        inspectMs?.let { append("inspect=${it}ms ") }
        llmMs?.let { append("llm=${it}ms ") }
        ttsMs?.let { append("tts=${it}ms ") }
        append("total=${System.currentTimeMillis() - t0}ms")
    }

    private companion object {
        const val TAG = "Readout/Session"
    }
}
