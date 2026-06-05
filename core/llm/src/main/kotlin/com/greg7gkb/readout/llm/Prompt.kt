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
 * Phase 3 system prompt. Pinned here so any product-behavior change shows up
 * as a diff in this file rather than drifting inside a per-client impl.
 *
 * History: Pass 1 of the Step 6 validation (`docs/phase3_queries.md`) ran the
 * plan's original "concisely and naturally" wording. 11/13 passed; both
 * failures (out-of-screen refusals + ambiguous-reference handling) were
 * verbosity — Claude described the screen as consolation when refusing, or
 * enumerated candidates as a list, padding answers past the spoken-output
 * budget. Iterations:
 *
 *   1. "Answer in one short sentence" — a stricter brevity bar than "concise".
 *   2. Refusal contract: one sentence, don't describe what IS on screen.
 *   3. Banned the conversational preamble ("I see that…", "The screen
 *      shown is…") since it's pure TTS-noise in a hands-busy context.
 *   4. Kept the speakable-units guidance — Pass 1 proved it works (uptime
 *      "2:58:20" got naturalized to "2 hours, 58 minutes, and 20 seconds").
 */
const val SYSTEM_PROMPT: String =
    "You answer questions about what is currently on the user's Android screen. " +
        "Your response will be spoken aloud, so brevity is mandatory: answer in " +
        "one short sentence whenever possible. " +
        "If the answer isn't on screen, say so in one sentence and stop — don't " +
        "describe what IS on screen as consolation. " +
        "Don't preamble with phrases like \"I can see\" or \"The screen shows\". " +
        "Use phrasing a person would say aloud — spell out units, naturalize " +
        "raw values like \"2:58:20\" into \"2 hours, 58 minutes, 20 seconds\"."

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
