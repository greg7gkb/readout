package com.greg7gkb.readout.screen

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pin the [FakeScreenReader] contract — Available + availability=true — so a
 * future refactor of [ScreenReader] (e.g. adding fields to ScreenReadResult)
 * doesn't accidentally make the dev-flavor reader start reporting unavailable.
 * The Echo-client dev loop depends on Available always firing here.
 */
class FakeScreenReaderTest {

    @Test
    fun `availability is true`() {
        assertTrue(FakeScreenReader().availability.value)
    }

    @Test
    fun `inspect returns Available with a non-empty fake inspection`() = runBlocking {
        val result = FakeScreenReader().inspect()
        assertTrue("expected Available, got $result", result is ScreenReadResult.Available)
        val inspection = (result as ScreenReadResult.Available).inspection
        assertEquals("com.example.weather", inspection.foregroundPackage)
        assertEquals("Weather", inspection.foregroundAppLabel)
        assertTrue("fake should produce at least a few nodes", inspection.nodes.size >= 5)
    }
}
