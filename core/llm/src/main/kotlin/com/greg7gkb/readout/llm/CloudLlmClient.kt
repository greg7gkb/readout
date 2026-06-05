package com.greg7gkb.readout.llm

import com.greg7gkb.readout.common.model.Answer
import com.greg7gkb.readout.common.model.ScreenInspection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single [LlmClient] binding for the `cloud` flavor. Routes each call to the
 * provider currently selected in [CloudLlmConfig].
 *
 * Both backends consume the same [Prompt] and return the same [Answer], so the
 * caller (orchestrator) is unaware of which provider answered. The dispatch is
 * per-call rather than per-instance so a runtime provider flip takes effect
 * immediately without rewiring DI.
 */
@Singleton
class CloudLlmClient @Inject constructor(
    private val claude: CloudClaudeClient,
    private val gemini: CloudGeminiClient,
    private val config: CloudLlmConfig,
) : LlmClient {

    override suspend fun answer(
        question: String,
        screen: ScreenInspection,
        appName: String,
    ): Answer = when (config.provider.value) {
        CloudProvider.CLAUDE -> claude.answer(question, screen, appName)
        CloudProvider.GEMINI -> gemini.answer(question, screen, appName)
    }
}
