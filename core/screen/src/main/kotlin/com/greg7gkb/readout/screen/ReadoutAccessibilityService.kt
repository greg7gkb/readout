package com.greg7gkb.readout.screen

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Reads the active window's view hierarchy when [AccessibilityScreenReader]
 * asks for a snapshot. This class is just a thin lifecycle owner — the
 * actual node walk happens on the IO dispatcher inside the screen reader,
 * not in `onAccessibilityEvent` (snapshot timing is on-demand by design;
 * walking on every event would burn CPU on screens that update constantly).
 */
@AndroidEntryPoint
class ReadoutAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var holder: ReadoutAccessibilityServiceHolder

    override fun onServiceConnected() {
        holder.bind(this)
        Log.i(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally a no-op. Snapshots are pulled on demand via
        // getRootInActiveWindow(), not driven by event-by-event walks.
    }

    override fun onInterrupt() {
        Log.i(TAG, "service interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        holder.unbind(this)
        Log.i(TAG, "service unbound")
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "Readout/Screen"
    }
}
