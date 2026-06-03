package com.greg7gkb.readout.screen

import com.greg7gkb.readout.common.model.Bounds
import com.greg7gkb.readout.common.model.ScreenNode
import com.greg7gkb.readout.common.model.ScreenSnapshot
import javax.inject.Inject

/**
 * Returns a hardcoded snapshot resembling a RideWithGPS active-ride screen.
 * Lets the rest of the pipeline (LLM, TTS, session orchestrator) be exercised
 * before the real AccessibilityService implementation lands in Phase 2.
 */
class FakeScreenReader @Inject constructor() : ScreenReader {
    override suspend fun snapshot(): ScreenSnapshot = ScreenSnapshot(
        foregroundPackage = "com.ridewithgps.mobile",
        timestampMillis = System.currentTimeMillis(),
        nodes = listOf(
            ScreenNode(text = "Distance", className = "android.widget.TextView", bounds = Bounds(40, 200, 200, 240)),
            ScreenNode(text = "24.7 mi", className = "android.widget.TextView", bounds = Bounds(40, 250, 360, 340)),
            ScreenNode(text = "Elapsed", className = "android.widget.TextView", bounds = Bounds(40, 380, 200, 420)),
            ScreenNode(text = "1:32:18", className = "android.widget.TextView", bounds = Bounds(40, 430, 360, 520)),
            ScreenNode(text = "Speed", className = "android.widget.TextView", bounds = Bounds(40, 560, 200, 600)),
            ScreenNode(text = "15.3 mph", className = "android.widget.TextView", bounds = Bounds(40, 610, 360, 700)),
            ScreenNode(text = "Elevation gained", className = "android.widget.TextView", bounds = Bounds(40, 740, 280, 780)),
            ScreenNode(text = "1,247 ft", className = "android.widget.TextView", bounds = Bounds(40, 790, 360, 880)),
            ScreenNode(text = "Route", className = "android.widget.TextView", bounds = Bounds(40, 920, 200, 960)),
            ScreenNode(text = "Sunday Long Ride", className = "android.widget.TextView", bounds = Bounds(40, 970, 720, 1040)),
        ),
    )
}
