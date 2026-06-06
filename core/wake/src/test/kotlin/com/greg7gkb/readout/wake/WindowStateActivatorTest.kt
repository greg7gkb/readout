package com.greg7gkb.readout.wake

import com.greg7gkb.readout.common.model.Activation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The intent of [WindowStateActivator] is "wait for the shade to close, then
 * activate." These tests pin the resulting state machine:
 *  - arm + non-SystemUI event → trigger
 *  - arm + SystemUI event then non-SystemUI event → trigger fires on the
 *    second event (the shade-just-opened case shouldn't drain the pending)
 *  - arm + delay past timeout + non-SystemUI event → no trigger
 *  - non-armed event stream → no trigger ever
 *  - two non-SystemUI events after a single arm → at most one trigger
 *    (single-shot semantics — important so a window-shuffle doesn't double-fire)
 *
 * The subscribe-first / fire-second pattern below is load-bearing:
 * [ManualActivator] uses `MutableSharedFlow(replay = 0)`, so an emission made
 * before any subscriber attaches is dropped. In production this is fine — the
 * orchestrator collects from session start. In tests we mirror that by
 * launching a collector first, yielding for it to attach, then firing events.
 */
class WindowStateActivatorTest {

    private suspend fun collectingActivations(
        scope: CoroutineScope,
        sink: MutableList<Activation>,
        manual: ManualActivator,
    ): Job {
        val job = scope.launch { manual.activations().collect { sink += it } }
        // Give the collector a turn to actually attach to the SharedFlow.
        yield()
        return job
    }

    @Test
    fun `arm then non-systemui event triggers ManualActivator`() = runBlocking {
        val manual = ManualActivator()
        val activator = WindowStateActivator(manual)
        val received = mutableListOf<Activation>()
        val collector = collectingActivations(this, received, manual)

        assertFalse(activator.isArmed())
        activator.arm()
        assertTrue(activator.isArmed())
        activator.onWindowChanged("com.android.settings")
        delay(50)

        assertEquals(1, received.size)
        assertTrue("trigger should be tagged NotificationAction", received[0] is Activation.NotificationAction)
        assertFalse("isArmed should clear after fire", activator.isArmed())
        collector.cancel()
    }

    @Test
    fun `SystemUI event does not fire, subsequent non-SystemUI event does`() = runBlocking {
        val manual = ManualActivator()
        val activator = WindowStateActivator(manual)
        val received = mutableListOf<Activation>()
        val collector = collectingActivations(this, received, manual)

        activator.arm()
        activator.onWindowChanged("com.android.systemui")
        assertTrue("SystemUI event should not consume the pending trigger", activator.isArmed())
        assertEquals(0, received.size)

        activator.onWindowChanged("com.android.settings")
        delay(50)

        assertEquals(1, received.size)
        collector.cancel()
    }

    @Test
    fun `arm with already-expired deadline does not trigger`() = runBlocking {
        val manual = ManualActivator()
        val activator = WindowStateActivator(manual)
        val received = mutableListOf<Activation>()
        val collector = collectingActivations(this, received, manual)

        activator.arm(timeoutMillis = 1L)
        delay(10)
        activator.onWindowChanged("com.android.settings")
        delay(50)

        assertEquals("stale arm should be silently dropped", 0, received.size)
        assertFalse(activator.isArmed())
        collector.cancel()
    }

    @Test
    fun `events with no prior arm produce no trigger`() = runBlocking {
        val manual = ManualActivator()
        val activator = WindowStateActivator(manual)
        val received = mutableListOf<Activation>()
        val collector = collectingActivations(this, received, manual)

        activator.onWindowChanged("com.android.systemui")
        activator.onWindowChanged("com.android.settings")
        activator.onWindowChanged("com.android.launcher3")
        delay(50)

        assertEquals(0, received.size)
        collector.cancel()
    }

    @Test
    fun `single arm fires at most once across multiple qualifying events`() = runBlocking {
        val manual = ManualActivator()
        val activator = WindowStateActivator(manual)
        val received = mutableListOf<Activation>()
        val collector = collectingActivations(this, received, manual)

        activator.arm()
        activator.onWindowChanged("com.android.settings")
        activator.onWindowChanged("com.android.settings")
        activator.onWindowChanged("com.android.launcher3")
        delay(50)

        // Exactly one trigger from the single arm — re-firing on every event
        // would create a thundering-herd as the user navigates between apps.
        assertEquals(1, received.size)
        collector.cancel()
    }

    @Test
    fun `re-arming extends the deadline`() = runBlocking {
        val manual = ManualActivator()
        val activator = WindowStateActivator(manual)
        val received = mutableListOf<Activation>()
        val collector = collectingActivations(this, received, manual)

        activator.arm(timeoutMillis = 50)
        delay(30)
        activator.arm(timeoutMillis = 1_000)
        delay(40)
        // Total elapsed (~70ms) is past the first arm's 50ms deadline but well
        // within the re-armed 1000ms — extension should win.
        activator.onWindowChanged("com.android.settings")
        delay(50)

        assertEquals("re-arm should have extended the deadline", 1, received.size)
        collector.cancel()
    }
}
