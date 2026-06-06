package com.greg7gkb.readout.screen

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.greg7gkb.readout.wake.ManualActivator
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
 *  - [onAccessibilityButtonClicked] handles the user-configured Accessibility
 *    Shortcut (floating button / volume-keys / 2-finger gesture, picked in
 *    system Settings → Accessibility → Shortcut → Readout).
 */
@AndroidEntryPoint
class ReadoutAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var holder: ReadoutAccessibilityServiceHolder

    @Inject
    lateinit var windowStateActivator: WindowStateActivator

    @Inject
    lateinit var manualActivator: ManualActivator

    override fun onServiceConnected() {
        holder.bind(this)
        // Register the accessibility-button callback. The shortcut surface
        // (floating button, volume-keys hold, or 2-finger gesture — picked
        // by the user in system Settings → Accessibility → Shortcut → Readout)
        // routes through the same controller callback regardless of modality.
        accessibilityButtonController.registerAccessibilityButtonCallback(
            object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    Log.i(TAG, "accessibility shortcut invoked")
                    manualActivator.trigger(ManualActivator.Source.Tap)
                }
            }
        )
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
