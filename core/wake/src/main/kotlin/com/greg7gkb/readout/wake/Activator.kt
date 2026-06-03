package com.greg7gkb.readout.wake

import com.greg7gkb.readout.common.model.Activation
import kotlinx.coroutines.flow.Flow

/**
 * Unified source of session activations regardless of input modality.
 * Wake-word detections, tap-to-talk presses, and notification-action triggers
 * all surface here so the session orchestrator listens to one stream.
 *
 * Implementations:
 *  - [ManualActivator] — UI button / notification action drives [trigger]
 *  - (future) WakeWordActivator — wraps [WakeWordEngine.events]
 *  - (future) CompositeActivator — merges multiple sources into one stream
 */
interface Activator {
    fun activations(): Flow<Activation>
}
