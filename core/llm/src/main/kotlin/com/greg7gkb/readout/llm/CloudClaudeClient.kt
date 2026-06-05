package com.greg7gkb.readout.llm

import com.greg7gkb.readout.common.model.Answer
import com.greg7gkb.readout.common.model.ScreenInspection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anthropic Messages API client. The specific model is set in [MODEL_ID] — keep
 * it on the companion so swapping (Haiku 4.5 → Sonnet → next-gen Haiku) is a
 * one-line change. Today it points at Claude Haiku 4.5.
 *
 * Wire shape:
 * `POST {baseUrl}/v1/messages` with `x-api-key` + `anthropic-version` headers,
 * a JSON body of `{model, max_tokens, system, messages}`, and a response body
 * whose `content[0].text` is the spoken answer.
 *
 * `max_tokens=512` is sized for the voice-Q&A use case: spoken answers are
 * short and going higher just lets a misbehaving model ramble. Revisit if the
 * Step 6 validation pass shows answers being truncated.
 */
@Singleton
class CloudClaudeClient @Inject constructor(
    @AnthropicApiKey private val apiKey: String,
    @AnthropicBaseUrl private val baseUrl: String,
    private val httpClient: OkHttpClient,
    private val json: Json,
) : LlmClient {

    override suspend fun answer(
        question: String,
        screen: ScreenInspection,
        appName: String,
    ): Answer {
        val start = System.currentTimeMillis()
        val prompt = buildPrompt(question, screen)
        val payload = AnthropicRequest(
            model = MODEL_ID,
            maxTokens = MAX_TOKENS,
            system = prompt.systemPrompt,
            messages = listOf(AnthropicMessage(role = "user", content = prompt.userPrompt)),
        )
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/messages")
            .post(json.encodeToString(AnthropicRequest.serializer(), payload).toRequestBody(JSON_MEDIA))
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .build()

        val text = withContext(Dispatchers.IO) {
            httpClient.executeWithRetry(request).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Anthropic ${response.code}: ${body.take(200)}")
                }
                json.decodeFromString(AnthropicResponse.serializer(), body)
                    .content
                    .firstOrNull { it.type == "text" }
                    ?.text
                    .orEmpty()
            }
        }

        return Answer(text = text, latencyMillis = System.currentTimeMillis() - start)
    }

    companion object {
        const val MODEL_ID = "claude-haiku-4-5-20251001"
        const val MAX_TOKENS = 512
        const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}

@Serializable
internal data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<AnthropicMessage>,
)

@Serializable
internal data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class AnthropicResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
)

@Serializable
internal data class AnthropicContentBlock(
    val type: String,
    val text: String = "",
)
