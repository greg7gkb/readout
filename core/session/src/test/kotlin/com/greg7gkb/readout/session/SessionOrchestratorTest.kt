package com.greg7gkb.readout.session

import com.greg7gkb.readout.audio.SpeechRecognizer
import com.greg7gkb.readout.audio.TtsEngine
import com.greg7gkb.readout.common.model.Activation
import com.greg7gkb.readout.common.model.Answer
import com.greg7gkb.readout.common.model.ScreenInspection
import com.greg7gkb.readout.common.model.Transcript
import com.greg7gkb.readout.common.model.TtsPrefs
import com.greg7gkb.readout.llm.LlmClient
import com.greg7gkb.readout.screen.ScreenReadResult
import com.greg7gkb.readout.screen.ScreenReader
import com.greg7gkb.readout.wake.Activator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the Layer 1 fail-closed contract: when [ScreenReader.inspect] returns
 * [ScreenReadResult.Unavailable] the orchestrator must NOT call the LLM and
 * must speak a deterministic message instead. Letting the LLM improvise on an
 * empty / absent inspection wastes tokens and risks confidently-wrong answers
 * that depend on model judgment we shouldn't lean on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionOrchestratorTest {

    @Test
    fun `Unavailable SERVICE_NOT_BOUND short-circuits the LLM and speaks the re-enable message`() = runTest {
        val activations = Channel<Activation>(Channel.BUFFERED)
        val llm = ExplodingLlmClient()
        val tts = RecordingTtsEngine()
        val orchestrator = SessionOrchestrator(
            activator = ChannelActivator(activations),
            speechRecognizer = StubSpeechRecognizer(Transcript("hello", isFinal = true)),
            screenReader = StubScreenReader(
                ScreenReadResult.Unavailable(ScreenReadResult.Unavailable.Reason.SERVICE_NOT_BOUND),
            ),
            llmClient = llm,
            ttsEngine = tts,
        )

        orchestrator.start(this)
        activations.send(Activation.Tap(timestampMillis = 0L))
        advanceUntilIdle()

        assertEquals("LLM must not be called on Unavailable", 0, llm.callCount)
        assertEquals(1, tts.spoken.size)
        // Don't pin the exact string — the orchestrator owns the wording. Pin
        // the bits a user reads to know what to do next, so the test catches
        // a future refactor that accidentally drops the actionable guidance.
        val spoken = tts.spoken[0].lowercase()
        assertTrue("expected actionable guidance, got: ${tts.spoken[0]}", "accessibility" in spoken)
        assertTrue("expected settings hint, got: ${tts.spoken[0]}", "settings" in spoken)

        orchestrator.stop()
    }

    @Test
    fun `Unavailable ROOT_NOT_AVAILABLE speaks a transient retry message, not the re-enable one`() = runTest {
        val activations = Channel<Activation>(Channel.BUFFERED)
        val llm = ExplodingLlmClient()
        val tts = RecordingTtsEngine()
        val orchestrator = SessionOrchestrator(
            activator = ChannelActivator(activations),
            speechRecognizer = StubSpeechRecognizer(Transcript("hello", isFinal = true)),
            screenReader = StubScreenReader(
                ScreenReadResult.Unavailable(ScreenReadResult.Unavailable.Reason.ROOT_NOT_AVAILABLE),
            ),
            llmClient = llm,
            ttsEngine = tts,
        )

        orchestrator.start(this)
        activations.send(Activation.Tap(timestampMillis = 0L))
        advanceUntilIdle()

        assertEquals(0, llm.callCount)
        assertEquals(1, tts.spoken.size)
        val spoken = tts.spoken[0].lowercase()
        assertTrue("expected retry guidance, got: ${tts.spoken[0]}", "try again" in spoken)
        // Specifically NOT the settings message — that would be misleading for
        // a transient root-null state caused by a window transition.
        assertTrue("should not point user to settings for ROOT_NOT_AVAILABLE", "settings" !in spoken)

        orchestrator.stop()
    }

    @Test
    fun `Available routes through LLM and speaks its answer`() = runTest {
        val activations = Channel<Activation>(Channel.BUFFERED)
        val inspection = ScreenInspection(
            foregroundPackage = "com.example.weather",
            foregroundAppLabel = "Weather",
            timestampMillis = 0L,
            nodes = emptyList(),
        )
        val llm = RecordingLlmClient(Answer(text = "62 degrees and sunny", latencyMillis = 100L))
        val tts = RecordingTtsEngine()
        val orchestrator = SessionOrchestrator(
            activator = ChannelActivator(activations),
            speechRecognizer = StubSpeechRecognizer(Transcript("how is the weather", isFinal = true)),
            screenReader = StubScreenReader(ScreenReadResult.Available(inspection)),
            llmClient = llm,
            ttsEngine = tts,
        )

        orchestrator.start(this)
        activations.send(Activation.Tap(timestampMillis = 0L))
        advanceUntilIdle()

        assertEquals(1, llm.calls.size)
        assertEquals("how is the weather", llm.calls[0].question)
        assertEquals(inspection, llm.calls[0].screen)
        assertEquals(listOf("62 degrees and sunny"), tts.spoken)

        orchestrator.stop()
    }
}

// --- test doubles ---

private class ChannelActivator(private val source: Channel<Activation>) : Activator {
    override fun activations(): Flow<Activation> = source.consumeAsFlow()
}

private class StubSpeechRecognizer(private val final: Transcript) : SpeechRecognizer {
    override fun listen(): Flow<Transcript> = flowOf(final)
}

private class StubScreenReader(
    private val result: ScreenReadResult,
) : ScreenReader {
    // Availability isn't read by the orchestrator (only the per-call result is),
    // so a constant flow is sufficient for these tests. UI tests would assert
    // on this; we don't.
    override val availability: StateFlow<Boolean> =
        MutableStateFlow(result is ScreenReadResult.Available)

    override suspend fun inspect(): ScreenReadResult = result
}

private class ExplodingLlmClient : LlmClient {
    var callCount = 0
    override suspend fun answer(question: String, screen: ScreenInspection, appName: String): Answer {
        callCount++
        // Fail loudly rather than throwing — the orchestrator wraps the pipeline
        // in try/catch and would swallow an exception, so the assertion would
        // only fire as Error state we'd then have to check separately. A
        // counter check on llm.callCount is the cleaner contract.
        return Answer(text = "should-not-have-been-called", latencyMillis = 0L)
    }
}

private class RecordingLlmClient(private val canned: Answer) : LlmClient {
    data class Call(val question: String, val screen: ScreenInspection, val appName: String)
    val calls = mutableListOf<Call>()
    override suspend fun answer(question: String, screen: ScreenInspection, appName: String): Answer {
        calls.add(Call(question, screen, appName))
        return canned
    }
}

private class RecordingTtsEngine : TtsEngine {
    val spoken = mutableListOf<String>()
    override suspend fun speak(text: String, prefs: TtsPrefs) {
        spoken.add(text)
    }
}
