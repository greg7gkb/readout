package com.greg7gkb.readout.screen

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the system-managed [ReadoutAccessibilityService] instance to
 * Hilt-injected consumers. The service registers itself here when the
 * system binds it (`onServiceConnected`) and clears it on unbind, so
 * [AccessibilityScreenReader] can fetch the live service on demand
 * without going through static state.
 *
 * `@Volatile` because the service writes from its main thread while
 * `snapshot()` reads from an IO worker.
 */
@Singleton
class ReadoutAccessibilityServiceHolder @Inject constructor() {

    @Volatile
    var service: ReadoutAccessibilityService? = null
        internal set
}
