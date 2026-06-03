package com.greg7gkb.readout.session

import android.util.Log
import com.greg7gkb.readout.audio.SpeechRecognizer
import com.greg7gkb.readout.audio.TtsEngine
import com.greg7gkb.readout.common.model.Activation
import com.greg7gkb.readout.common.model.Session
import com.greg7gkb.readout.llm.LlmClient
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
        try {
            _state.value = SessionState.Listening(session)
            val transcript = speechRecognizer.listen().first { it.isFinal }
            Log.i(TAG, "[${session.id}] transcript=${transcript.text}")

            _state.value = SessionState.Thinking(session, transcript.text)
            val inspection = screenReader.inspect()
            val answer = llmClient.answer(
                question = transcript.text,
                screen = inspection,
                appName = inspection.foregroundPackage,
            )
            Log.i(TAG, "[${session.id}] answer=${answer.text} latencyMs=${answer.latencyMillis}")

            _state.value = SessionState.Speaking(session, answer.text)
            ttsEngine.speak(answer.text)

            _state.value = SessionState.Idle
            Log.i(TAG, "[${session.id}] complete")
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            Log.w(TAG, "[${session.id}] failed: $msg")
            _state.value = SessionState.Error(session, msg)
        }
    }

    private companion object {
        const val TAG = "Readout/Session"
    }
}
