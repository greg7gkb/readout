package com.greg7gkb.readout.wake

import com.greg7gkb.readout.common.model.WakeEvent
import kotlinx.coroutines.flow.Flow

/**
 * Continuously listens for a wake word and emits [WakeEvent]s on detection.
 *
 * Current implementation: [OpenWakeWordEngine] (Apache-2.0; "Hey Jarvis").
 *
 * Engines are expected to run continuously while their collector is active
 * (i.e. while the foreground service is up) and release their AudioRecord
 * cleanly when the collector cancels.
 *
 * Microphone coordination: while a [WakeWordEngine] is actively listening it
 * holds the device microphone, which conflicts with Android's
 * `SpeechRecognizer`. Callers running a pipeline that needs the mic (the
 * session orchestrator's STT step) MUST [pause] the engine for the duration
 * of that pipeline and [resume] when done.
 */
interface WakeWordEngine {
    fun events(): Flow<WakeEvent>

    /**
     * Pause active listening, releasing the microphone. Suspends until the
     * engine has actually stopped consuming audio — when this returns,
     * `SpeechRecognizer` may safely take the mic.
     *
     * Safe to call when [events] isn't being collected; returns immediately
     * in that case. Idempotent — successive calls without an intervening
     * [resume] are no-ops.
     */
    suspend fun pause()

    /**
     * Resume listening after a [pause]. Reopens the underlying audio source
     * (if [events] is being collected) and continues classifying. Idempotent.
     * Does not block on the audio source being live — once this returns,
     * detection may take ~80 ms to come back online.
     */
    suspend fun resume()
}
