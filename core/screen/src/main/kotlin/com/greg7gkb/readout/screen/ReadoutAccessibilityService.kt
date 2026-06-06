package com.greg7gkb.readout.screen

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.greg7gkb.readout.wake.WindowStateActivator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Anchor point for accessibility-driven concerns:
 *  - [AccessibilityScreenReader] pulls the active window's view tree on
 *    demand via the [holder]; this class doesn't walk trees itself
 *    (per-event walks would burn CPU on screens that update constantly).
 *  - [WindowStateActivator] is fed every `TYPE_WINDOW_STATE_CHANGED` event so
 *    the notification-shade Trigger action can defer its activation until
 *    the shade is dismissed and the underlying app's window is active.
 *
 * Note: the service deliberately does NOT declare
 * `flagRequestAccessibilityButton`. On Android 14+ Pixel devices the
 * Settings UI treats "Accessibility shortcut" as the single enable/disable
 * toggle for the whole service — turning off the shortcut disables
 * accessibility too, which kills screen reading. Trigger surfaces other
 * than the in-app button live elsewhere: the foreground-service
 * notification's Trigger action is the always-available path.
 */
@AndroidEntryPoint
class ReadoutAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var holder: ReadoutAccessibilityServiceHolder

    @Inject
    lateinit var windowStateActivator: WindowStateActivator

    override fun onServiceConnected() {
        holder.bind(this)
        Log.i(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Feeds the shade-detection logic. The activator no-ops if no
            // trigger is pending, so the hot path here stays cheap.
            windowStateActivator.onWindowChanged(event.packageName?.toString())
        }
        // Snapshots are still pulled on demand via getRootInActiveWindow();
        // we don't walk trees here.
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
