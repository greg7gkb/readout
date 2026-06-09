package com.greg7gkb.readout.wake

import com.greg7gkb.readout.common.model.Activation
import com.greg7gkb.readout.common.model.WakeEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the merge contract:
 *  - WakeEvents from the engine surface as [Activation.WakeWord] with the
 *    same timestamp and confidence
 *  - ManualActivator triggers pass through unchanged ([Activation.Tap],
 *    [Activation.NotificationAction])
 *  - Both sources are observed concurrently — no event is shadowed by the
 *    other being more recent
 *
 * UnconfinedTestDispatcher so that the async collector subscribes before the
 * emits run. MutableSharedFlow with no replay drops emits that arrive before
 * a subscriber, so ordering matters.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompositeActivatorTest {

    @Test
    fun `wake events become WakeWord activations with timestamp and confidence`() =
        runTest(UnconfinedTestDispatcher()) {
            val wake = FakeWakeWordEngine()
            val manual = ManualActivator()
            val composite = CompositeActivator(wake, manual)

            val collected = async { composite.activations().take(1).toList() }
            wake.emit(WakeEvent(timestampMillis = 1_700_000_000_000L, confidence = 0.87f))

            val result = collected.await()
            assertEquals(1, result.size)
            assertEquals(
                Activation.WakeWord(
                    timestampMillis = 1_700_000_000_000L,
                    confidence = 0.87f,
                ),
                result[0],
            )
        }

    @Test
    fun `manual triggers and wake events both reach the merged stream`() =
        runTest(UnconfinedTestDispatcher()) {
            val wake = FakeWakeWordEngine()
            val manual = ManualActivator()
            val composite = CompositeActivator(wake, manual)

            val collected = async { composite.activations().take(3).toList() }
            manual.trigger(ManualActivator.Source.Tap)
            wake.emit(WakeEvent(timestampMillis = 1_000L, confidence = 0.6f))
            manual.trigger(ManualActivator.Source.NotificationAction)

            val result = collected.await()
            assertEquals(3, result.size)
            assertTrue("first should be Tap, got ${result[0]}", result[0] is Activation.Tap)
            assertTrue("second should be WakeWord, got ${result[1]}", result[1] is Activation.WakeWord)
            assertTrue(
                "third should be NotificationAction, got ${result[2]}",
                result[2] is Activation.NotificationAction,
            )
        }

    private class FakeWakeWordEngine : WakeWordEngine {
        private val flow = MutableSharedFlow<WakeEvent>(extraBufferCapacity = 16)
        override fun events(): Flow<WakeEvent> = flow
        override suspend fun pause() = Unit
        override suspend fun resume() = Unit
        suspend fun emit(event: WakeEvent) {
            flow.emit(event)
        }
    }
}
