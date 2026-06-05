package com.greg7gkb.readout.llm

import com.greg7gkb.readout.common.model.ScreenInspection
import com.greg7gkb.readout.common.model.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenInspectionPromptTest {

    @Test
    fun `formats Settings-style label-subtitle list mirroring the phase3 sample dump`() {
        // First 6 entries from the Settings root dump in docs/phase3_target.md —
        // proves the format reads as the doc claims it will.
        val inspection = ScreenInspection(
            foregroundPackage = "com.android.settings",
            foregroundAppLabel = "Settings",
            timestampMillis = 0L,
            nodes = listOf(
                ScreenNode(text = "Search Settings"),
                ScreenNode(text = "Google"),
                ScreenNode(text = "Services & preferences"),
                ScreenNode(text = "Network & internet"),
                ScreenNode(text = "Mobile, Wi-Fi, hotspot"),
                ScreenNode(text = "Storage"),
                ScreenNode(text = "48% used - 4.16 GB free"),
            ),
        )

        val expected = """
            Foreground app: Settings (package: com.android.settings)
            Visible content (in document order):
            - Search Settings
            - Google
            - Services & preferences
            - Network & internet
            - Mobile, Wi-Fi, hotspot
            - Storage
            - 48% used - 4.16 GB free
        """.trimIndent()

        assertEquals(expected, inspection.toPromptText())
    }

    @Test
    fun `falls back to contentDescription when text is absent`() {
        val inspection = ScreenInspection(
            foregroundPackage = "com.google.android.apps.maps",
            foregroundAppLabel = "Maps",
            timestampMillis = 0L,
            nodes = listOf(
                ScreenNode(contentDescription = "Voice search"),
                ScreenNode(contentDescription = "Directions"),
            ),
        )

        val expected = """
            Foreground app: Maps (package: com.google.android.apps.maps)
            Visible content (in document order):
            - Voice search
            - Directions
        """.trimIndent()

        assertEquals(expected, inspection.toPromptText())
    }

    @Test
    fun `prefers text over contentDescription when both are present`() {
        // contentDescription is often a TalkBack hint that wraps or duplicates
        // the displayed text — text is the authoritative on-screen string.
        val inspection = ScreenInspection(
            foregroundPackage = "com.example",
            foregroundAppLabel = "Example",
            timestampMillis = 0L,
            nodes = listOf(
                ScreenNode(text = "Submit", contentDescription = "Submit button"),
            ),
        )

        val result = inspection.toPromptText()
        assertEquals(
            """
                Foreground app: Example (package: com.example)
                Visible content (in document order):
                - Submit
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `falls back to package only when app label is null`() {
        val inspection = ScreenInspection(
            foregroundPackage = "com.example.unknown",
            foregroundAppLabel = null,
            timestampMillis = 0L,
            nodes = listOf(ScreenNode(text = "Hello")),
        )

        val expected = """
            Foreground app: com.example.unknown
            Visible content (in document order):
            - Hello
        """.trimIndent()

        assertEquals(expected, inspection.toPromptText())
    }

    @Test
    fun `reports empty state when no visible nodes`() {
        // Matches AccessibilityScreenReader's empty() helper — service not
        // bound or root unavailable. The LLM downstream should be able to
        // answer "I can't see your screen right now."
        val inspection = ScreenInspection(
            foregroundPackage = "",
            foregroundAppLabel = null,
            timestampMillis = 0L,
            nodes = emptyList(),
        )

        val expected = """
            Foreground app: unknown
            No visible text was detected on screen.
        """.trimIndent()

        assertEquals(expected, inspection.toPromptText())
    }

    @Test
    fun `skips nodes with neither text nor contentDescription rather than emitting blank bullets`() {
        // The walker should already filter these, but the formatter defends
        // anyway so a future walker change can't produce "- " noise.
        val inspection = ScreenInspection(
            foregroundPackage = "com.example",
            foregroundAppLabel = "Example",
            timestampMillis = 0L,
            nodes = listOf(
                ScreenNode(text = "First"),
                ScreenNode(text = null, contentDescription = null),
                ScreenNode(text = "  ", contentDescription = ""),
                ScreenNode(text = "Last"),
            ),
        )

        val expected = """
            Foreground app: Example (package: com.example)
            Visible content (in document order):
            - First
            - Last
        """.trimIndent()

        assertEquals(expected, inspection.toPromptText())
    }
}
