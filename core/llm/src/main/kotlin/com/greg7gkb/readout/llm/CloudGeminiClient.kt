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
 * Google Generative Language API client. The specific model is set in
 * [MODEL_ID] — keep it on the companion so swapping (Flash → Pro → next-gen)
 * is a one-line change. Today it points at Gemini 2.5 Flash.
 *
 * Wire shape:
 * `POST {baseUrl}/v1beta/models/{model}:generateContent?key={key}`
 * with a JSON body containing `system_instruction`, `contents`, and
 * `generationConfig.maxOutputTokens`. Response text is
 * `candidates[0].content.parts[0].text`.
 *
 * The API key is a query parameter rather than a header — the only
 * non-header-key path Google's REST API documents for direct calls. The header
 * alternative requires a service account, which is overkill for a personal
 * prototype.
 */
@Singleton
class CloudGeminiClient @Inject constructor(
    @GeminiApiKey private val apiKey: String,
    @GeminiBaseUrl private val baseUrl: String,
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
        val payload = GeminiRequest(
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = prompt.systemPrompt))),
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = prompt.userPrompt)),
                ),
            ),
            generationConfig = GeminiGenerationConfig(maxOutputTokens = MAX_OUTPUT_TOKENS),
        )
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1beta/models/$MODEL_ID:generateContent?key=$apiKey")
            .post(json.encodeToString(GeminiRequest.serializer(), payload).toRequestBody(JSON_MEDIA))
            .header("content-type", "application/json")
            .build()

        val text = withContext(Dispatchers.IO) {
            httpClient.executeWithRetry(request).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Gemini ${response.code}: ${body.take(200)}")
                }
                json.decodeFromString(GeminiResponse.serializer(), body)
                    .candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    .orEmpty()
            }
        }

        return Answer(text = text, latencyMillis = System.currentTimeMillis() - start)
    }

    companion object {
        const val MODEL_ID = "gemini-2.5-flash"
        const val MAX_OUTPUT_TOKENS = 512
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}

@Serializable
internal data class GeminiRequest(
    @SerialName("system_instruction") val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
)

@Serializable
internal data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
internal data class GeminiPart(
    val text: String,
)

@Serializable
internal data class GeminiGenerationConfig(
    val maxOutputTokens: Int,
)

@Serializable
internal data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
)
