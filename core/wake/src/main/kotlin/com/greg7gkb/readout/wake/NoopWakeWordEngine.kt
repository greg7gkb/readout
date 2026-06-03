package com.greg7gkb.readout.wake

import com.greg7gkb.readout.common.model.WakeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

/**
 * Phase 1 placeholder. Real wake-word detection ships in Phase 4 with the
 * Porcupine integration.
 */
class NoopWakeWordEngine @Inject constructor() : WakeWordEngine {
    override fun events(): Flow<WakeEvent> = emptyFlow()
}
