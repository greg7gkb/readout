package com.greg7gkb.readout.wake

import com.greg7gkb.readout.common.model.WakeEvent
import kotlinx.coroutines.flow.Flow

/**
 * Continuously listens for a wake word and emits [WakeEvent]s on detection.
 *
 * Implementations:
 *  - [NoopWakeWordEngine] — Phase 1 placeholder; emits nothing
 *  - PorcupineWakeWordEngine — Phase 4 real impl backed by Picovoice Porcupine
 *  - (alternative) OpenWakeWordEngine if Porcupine licensing blocks the Play Store
 *
 * Engines are expected to run continuously while the foreground service is
 * active and stop when their collector is cancelled.
 */
interface WakeWordEngine {
    fun events(): Flow<WakeEvent>
}
