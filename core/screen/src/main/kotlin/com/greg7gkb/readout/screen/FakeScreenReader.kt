package com.greg7gkb.readout.screen

import com.greg7gkb.readout.common.model.Bounds
import com.greg7gkb.readout.common.model.ScreenNode
import com.greg7gkb.readout.common.model.ScreenInspection
import javax.inject.Inject

/**
 * Returns a hardcoded inspection resembling a generic weather-app screen.
 * Lets the rest of the pipeline (LLM, TTS, session orchestrator) be exercised
 * before the real AccessibilityService implementation lands in Phase 2.
 *
 * The choice of "weather" is deliberate: it's universally relatable, covers
 * situational-accessibility scenarios (planning a walk, driving, dressing a
 * child), and produces labeled values an LLM can reason about — without
 * committing the product to any particular third-party app.
 */
class FakeScreenReader @Inject constructor() : ScreenReader {
    override suspend fun inspect(): ScreenInspection = ScreenInspection(
        foregroundPackage = "com.example.weather",
        timestampMillis = System.currentTimeMillis(),
        nodes = listOf(
            ScreenNode(text = "San Francisco", className = "android.widget.TextView", bounds = Bounds(40, 200, 720, 280)),
            ScreenNode(text = "Today", className = "android.widget.TextView", bounds = Bounds(40, 300, 200, 340)),
            ScreenNode(text = "62°F", className = "android.widget.TextView", bounds = Bounds(40, 360, 360, 480)),
            ScreenNode(text = "Partly cloudy", className = "android.widget.TextView", bounds = Bounds(40, 500, 500, 560)),
            ScreenNode(text = "Wind", className = "android.widget.TextView", bounds = Bounds(40, 620, 200, 660)),
            ScreenNode(text = "8 mph NW", className = "android.widget.TextView", bounds = Bounds(40, 670, 360, 730)),
            ScreenNode(text = "Humidity", className = "android.widget.TextView", bounds = Bounds(40, 780, 280, 820)),
            ScreenNode(text = "71%", className = "android.widget.TextView", bounds = Bounds(40, 830, 200, 890)),
            ScreenNode(text = "Sunset", className = "android.widget.TextView", bounds = Bounds(40, 940, 200, 980)),
            ScreenNode(text = "7:42 PM", className = "android.widget.TextView", bounds = Bounds(40, 990, 360, 1050)),
        ),
    )
}
