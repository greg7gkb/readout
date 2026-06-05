package com.greg7gkb.readout.llm

import com.greg7gkb.readout.common.model.ScreenInspection
import com.greg7gkb.readout.common.model.ScreenNode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class CloudLlmClientTest {

    private lateinit var server: MockWebServer
    private val http = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val inspection = ScreenInspection(
        foregroundPackage = "com.android.settings",
        foregroundAppLabel = "Settings",
        timestampMillis = 0L,
        nodes = listOf(ScreenNode(text = "Android 15")),
    )

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun delegating(config: CloudLlmConfig = CloudLlmConfig()): CloudLlmClient {
        val base = server.url("/").toString()
        return CloudLlmClient(
            claude = CloudClaudeClient("k", base, http, json),
            gemini = CloudGeminiClient("k", base, http, json),
            config = config,
        )
    }

    @Test
    fun `defaults to CLAUDE and hits Anthropic endpoint`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":[{"type":"text","text":"claude"}]}""",
            ),
        )

        val answer = runBlocking { delegating().answer("q", inspection, "Settings") }

        assertEquals("claude", answer.text)
        assertEquals("/v1/messages", server.takeRequest().path)
    }

    @Test
    fun `routes to Gemini when config provider is flipped`() {
        val config = CloudLlmConfig().apply { setProvider(CloudProvider.GEMINI) }
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"candidates":[{"content":{"parts":[{"text":"gemini"}]}}]}""",
            ),
        )

        val answer = runBlocking { delegating(config).answer("q", inspection, "Settings") }

        assertEquals("gemini", answer.text)
        assertTrue(server.takeRequest().path!!.startsWith("/v1beta/models/"))
    }

    @Test
    fun `runtime flip takes effect on the next call without reconstructing`() {
        // Per-call dispatch — the point of the abstraction. First call goes
        // to Claude; we flip the provider; second call goes to Gemini using
        // the same CloudLlmClient instance.
        val config = CloudLlmConfig()
        val client = delegating(config)

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":[{"type":"text","text":"first"}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"candidates":[{"content":{"parts":[{"text":"second"}]}}]}""",
            ),
        )

        runBlocking {
            assertEquals("first", client.answer("q", inspection, "Settings").text)
            config.setProvider(CloudProvider.GEMINI)
            assertEquals("second", client.answer("q", inspection, "Settings").text)
        }

        assertEquals("/v1/messages", server.takeRequest().path)
        assertTrue(server.takeRequest().path!!.startsWith("/v1beta/models/"))
    }
}
