package com.greg7gkb.readout.llm

import com.greg7gkb.readout.common.model.ScreenInspection
import com.greg7gkb.readout.common.model.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptTest {

    @Test
    fun `golden Settings-shaped prompt`() {
        // Pinned to catch unintended drift in SYSTEM_PROMPT, the user-message
        // layout, or toPromptText's header / bullet format. When the Phase 3
        // validation pass iterates the system prompt, this fixture updates
        // intentionally and shows the diff in the commit.
        val inspection = ScreenInspection(
            foregroundPackage = "com.android.settings",
            foregroundAppLabel = "Settings",
            timestampMillis = 0L,
            nodes = listOf(
                ScreenNode(text = "Storage"),
                ScreenNode(text = "48% used - 4.16 GB free"),
            ),
        )

        val prompt = buildPrompt(
            question = "How much free space do I have?",
            screen = inspection,
        )

        assertEquals(
            "You answer questions about content currently on the user's Android screen. " +
                "Given the structured screen text, answer the user's question concisely and " +
                "naturally for spoken output. Use units and phrasing a person would say aloud, " +
                "not abbreviations.",
            prompt.systemPrompt,
        )

        assertEquals(
            """
                Foreground app: Settings (package: com.android.settings)
                Visible content (in document order):
                - Storage
                - 48% used - 4.16 GB free

                Question: How much free space do I have?
            """.trimIndent(),
            prompt.userPrompt,
        )
    }

    @Test
    fun `trims whitespace around the question`() {
        // STT often returns a final transcript with trailing whitespace or
        // newlines from the partial-result merge; the prompt should not show
        // that to the LLM as part of the ask.
        val prompt = buildPrompt(
            question = "  what version of Android am I running?  \n",
            screen = ScreenInspection(
                foregroundPackage = "com.android.settings",
                foregroundAppLabel = "Settings",
                timestampMillis = 0L,
                nodes = listOf(ScreenNode(text = "Android 15")),
            ),
        )

        assertEquals(
            """
                Foreground app: Settings (package: com.android.settings)
                Visible content (in document order):
                - Android 15

                Question: what version of Android am I running?
            """.trimIndent(),
            prompt.userPrompt,
        )
    }

    @Test
    fun `propagates empty-screen state into the user prompt`() {
        // When AccessibilityScreenReader returns an empty inspection (service
        // not bound, root unavailable), the LLM still sees a well-formed
        // question — the system prompt tells it how to answer when it can't
        // see anything.
        val prompt = buildPrompt(
            question = "what's on screen?",
            screen = ScreenInspection(
                foregroundPackage = "",
                foregroundAppLabel = null,
                timestampMillis = 0L,
                nodes = emptyList(),
            ),
        )

        assertEquals(
            """
                Foreground app: unknown
                No visible text was detected on screen.

                Question: what's on screen?
            """.trimIndent(),
            prompt.userPrompt,
        )
    }
}
