package com.greg7gkb.readout.wake

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Defers a [ManualActivator] trigger until the accessibility service observes
 * the active window changing away from SystemUI.
 *
 * Why: tapping a Trigger action in the notification shade with the shade still
 * pulled down would point Readout at the shade's view tree, not the underlying
 * app the user wanted to ask about. By the time `getRootInActiveWindow` returns
 * the app's tree, the shade has dismissed and a `TYPE_WINDOW_STATE_CHANGED`
 * event has fired for the app's package — that's the signal that it's safe to
 * activate. Polling with a fixed delay (an earlier attempt) was racy on slow
 * shade-collapse animations.
 *
 * Semantics:
 *  - [arm] sets a one-shot pending trigger with an expiry timestamp. Re-arming
 *    while already armed extends the deadline rather than queuing.
 *  - [onWindowChanged] is called for every window-state event. SystemUI events
 *    are ignored (shade just opened or quick settings appeared). Any other
 *    package consumes the pending trigger (atomically — only the first
 *    qualifying event fires) and calls [ManualActivator.trigger].
 *  - A pending trigger older than its deadline is silently dropped on the next
 *    [onWindowChanged] call. The default deadline of 10s covers a user who
 *    taps Trigger and then takes a moment to swipe the shade closed; longer
 *    than that and the intent is stale.
 *
 * Thread-safety: writes happen on the binder/main thread (accessibility events
 * and broadcast receivers). All state is held in a single [Long] guarded by
 * `@Volatile`; the read-then-clear in [onWindowChanged] is racy by design — if
 * two events arrive simultaneously, at most one trigger fires, which is the
 * intended single-shot behavior.
 */
@Singleton
class WindowStateActivator @Inject constructor(
    private val manualActivator: ManualActivator,
) {

    @Volatile
    private var armedUntilMillis: Long = 0L

    /** Set a pending trigger to fire on the next non-SystemUI window event
     *  within [timeoutMillis]. Idempotent — re-arming extends the deadline. */
    fun arm(timeoutMillis: Long = DEFAULT_TIMEOUT_MS) {
        armedUntilMillis = System.currentTimeMillis() + timeoutMillis
    }

    /** Hook for [ReadoutAccessibilityService] to call on every
     *  [android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]. */
    fun onWindowChanged(packageName: String?) {
        if (packageName == null || packageName == SYSTEM_UI_PACKAGE) return
        val deadline = armedUntilMillis
        if (deadline == 0L) return
        armedUntilMillis = 0L
        if (System.currentTimeMillis() > deadline) return
        manualActivator.trigger(ManualActivator.Source.NotificationAction)
    }

    /** For tests / debug surfaces — true if a trigger is pending and not yet
     *  expired. Not used for control flow. */
    fun isArmed(nowMillis: Long = System.currentTimeMillis()): Boolean =
        armedUntilMillis != 0L && nowMillis <= armedUntilMillis

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
