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
 * Accessibility-button shortcut: explored and dropped. The native Android
 * behavior makes it impossible to have both (a) a working button click
 * callback and (b) shortcut-toggle independent of service-toggle, within
 * one accessibility service. With `flagRequestAccessibilityButton` set the
 * callback fires, but Pixel Settings cascades shortcut-off → service-off
 * (kills screen reading). Without the flag the callback never fires —
 * Android falls back to a default "toggle the service" behavior on button
 * tap. The clean fixes (two services, or a Quick Settings tile) weren't
 * worth the carry right now; the foreground-service notification's
 * Trigger action covers the always-available trigger use case.
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
