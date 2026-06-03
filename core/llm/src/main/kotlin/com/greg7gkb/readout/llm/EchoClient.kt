package com.greg7gkb.readout.llm

import com.greg7gkb.readout.common.model.Answer
import com.greg7gkb.readout.common.model.ScreenInspection
import javax.inject.Inject

/**
 * Returns the user's question reversed. Proves the pipeline is wired
 * end-to-end without any model or network dependency.
 */
class EchoClient @Inject constructor() : LlmClient {
    override suspend fun answer(
        question: String,
        screen: ScreenInspection,
        appName: String,
    ): Answer {
        val start = System.currentTimeMillis()
        return Answer(
            text = question.reversed(),
            latencyMillis = System.currentTimeMillis() - start,
        )
    }
}
