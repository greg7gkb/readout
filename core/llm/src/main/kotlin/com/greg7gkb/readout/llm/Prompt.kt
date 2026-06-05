package com.greg7gkb.readout.llm

import com.greg7gkb.readout.common.model.ScreenInspection

/**
 * Backend-agnostic prompt envelope. Both cloud (Gemini Flash, Claude Haiku) and
 * on-device (AICore / Gemini Nano) clients consume the same [Prompt] — each
 * client is responsible for translating it into the wire shape its API expects
 * (system field + user message array, or a single contents array with a
 * system_instruction, etc.).
 *
 * Kept deliberately flat: two strings, no role-array, no structured-output
 * opinions. The clients downstream are where API-specific structure belongs.
 */
data class Prompt(
    val systemPrompt: String,
    val userPrompt: String,
)

/**
 * Phase 3 system prompt — pinned from `docs/plan.md` so any change to product
 * behavior is a visible diff in this file rather than an undocumented drift
 * inside a per-client implementation.
 *
 * Word-for-word from the plan; future iteration during Step 6 validation will
 * land here.
 */
const val SYSTEM_PROMPT: String =
    "You answer questions about content currently on the user's Android screen. " +
        "Given the structured screen text, answer the user's question concisely and " +
        "naturally for spoken output. Use units and phrasing a person would say aloud, " +
        "not abbreviations."

/**
 * Assembles a [Prompt] for a single voice query.
 *
 * User-message layout: screen text first (via [toPromptText]), then the question
 * on its own labeled line. Putting the instruction at the tail is the
 * LLM-friendly pattern — the model reads the data, then sees the ask.
 *
 * The [question] is trimmed but not otherwise sanitized — the speech recognizer
 * already strips control characters and the LLM is robust to messy phrasing.
 */
fun buildPrompt(question: String, screen: ScreenInspection): Prompt = Prompt(
    systemPrompt = SYSTEM_PROMPT,
    userPrompt = buildString {
        append(screen.toPromptText())
        append("\n\nQuestion: ")
        append(question.trim())
    },
)
