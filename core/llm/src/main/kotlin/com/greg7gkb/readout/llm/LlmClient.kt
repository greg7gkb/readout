package com.greg7gkb.readout.llm

import com.greg7gkb.readout.common.model.Answer
import com.greg7gkb.readout.common.model.ScreenSnapshot

/**
 * Answers a user question grounded in the current screen.
 *
 * Implementations:
 *  - [EchoClient] — dev/test stub, no network or model call
 *  - CloudGeminiFlashClient — cloud flavor (later step)
 *  - AICoreClient — onDevice flavor on AICore-capable devices (later step)
 *
 * The choice of which implementation is bound to this interface is made in
 * `:app`'s DI graph and may vary by build flavor.
 */
interface LlmClient {
    suspend fun answer(
        question: String,
        screen: ScreenSnapshot,
        appName: String,
    ): Answer
}
