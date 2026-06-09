package com.greg7gkb.readout.wake

import com.greg7gkb.readout.common.model.Activation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of [Activation]s for the session orchestrator, merging every
 * input modality into one stream:
 *  - [WakeWordEngine] detections → [Activation.WakeWord]
 *  - [ManualActivator] taps + notification actions pass through unchanged
 *
 * The collection lifecycle drives the wake engine — when the orchestrator
 * collects [activations] (i.e. while the foreground service is up), the
 * engine's [WakeWordEngine.events] flow is collected as part of [merge],
 * which is what starts AudioRecord. When the orchestrator cancels its scope,
 * the engine's audio source releases automatically.
 *
 * Mic contention with STT is handled separately via
 * [WakeWordEngine.pause]/[WakeWordEngine.resume] — see `SessionOrchestrator`.
 */
@Singleton
class CompositeActivator @Inject constructor(
    private val wakeWordEngine: WakeWordEngine,
    private val manualActivator: ManualActivator,
) : Activator {

    override fun activations(): Flow<Activation> = merge(
        wakeWordEngine.events().map { event ->
            Activation.WakeWord(
                timestampMillis = event.timestampMillis,
                confidence = event.confidence,
            )
        },
        manualActivator.activations(),
    )
}
