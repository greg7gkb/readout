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
 */
interface WakeWordEngine {
    fun events(): Flow<WakeEvent>
}
