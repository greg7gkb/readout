package com.greg7gkb.readout.llm

/**
 * Which cloud LLM provider the `cloud` flavor's [CloudLlmClient] should route to.
 *
 * Values name the *provider*, not the specific model. Per-provider model IDs
 * live in the client's companion (see [CloudClaudeClient.MODEL_ID] /
 * [CloudGeminiClient.MODEL_ID]) so swapping Haiku 4.5 → Sonnet, or Flash → Pro,
 * doesn't touch this enum.
 *
 * Both backends implement the same prompt contract (see [buildPrompt]). The
 * choice is a runtime decision rather than a build-time one so the Step 6
 * validation pass can A/B them on identical query suites without rebuilding,
 * and so a future settings UI can expose the toggle to the user.
 */
enum class CloudProvider {
    CLAUDE,
    GEMINI,
}
