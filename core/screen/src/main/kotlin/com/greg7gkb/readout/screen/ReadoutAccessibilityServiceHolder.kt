package com.greg7gkb.readout.screen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the system-managed [ReadoutAccessibilityService] instance to
 * Hilt-injected consumers. The service registers itself here when the
 * system binds it (`onServiceConnected`) and clears it on unbind, so
 * [AccessibilityScreenReader] can fetch the live service on demand
 * without going through static state.
 *
 * Exposes a [StateFlow] so consumers can observe bind/unbind transitions —
 * the UI uses this to show a "reader is off" banner when the service is
 * enabled in Settings but the process isn't currently bound (after a
 * force-stop or low-memory kill, for instance).
 */
@Singleton
class ReadoutAccessibilityServiceHolder @Inject constructor() {

    private val _service = MutableStateFlow<ReadoutAccessibilityService?>(null)
    val service: StateFlow<ReadoutAccessibilityService?> = _service.asStateFlow()

    internal fun bind(service: ReadoutAccessibilityService) {
        _service.value = service
    }

    internal fun unbind(service: ReadoutAccessibilityService) {
        // Guard against an old onUnbind firing after a fresh bind has already
        // happened (legal in Android's service lifecycle, though rare).
        if (_service.value === service) _service.value = null
    }
}
