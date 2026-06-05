package com.greg7gkb.readout.llm

import com.greg7gkb.readout.common.model.ScreenInspection
import com.greg7gkb.readout.common.model.ScreenNode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudClaudeClientTest {

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

    private fun client() = CloudClaudeClient(
        apiKey = "key-xyz",
        baseUrl = server.url("/").toString(),
        httpClient = http,
        json = json,
    )

    @Test
    fun `posts Anthropic request shape and parses content text`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":[{"type":"text","text":"It's Android 15."}]}""",
            ),
        )

        val answer = runBlocking {
            client().answer(
                question = "what version of Android am I running?",
                screen = inspection,
                appName = "Settings",
            )
        }

        assertEquals("It's Android 15.", answer.text)
        assertNotNull(answer.latencyMillis)
        assertTrue(answer.latencyMillis!! >= 0)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/messages", recorded.path)
        assertEquals("key-xyz", recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
        assertTrue(recorded.getHeader("content-type")!!.startsWith("application/json"))

        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals(CloudClaudeClient.MODEL_ID, body["model"]!!.jsonPrimitive.content)
        assertEquals(CloudClaudeClient.MAX_TOKENS, body["max_tokens"]!!.jsonPrimitive.int)
        assertTrue(body["system"]!!.jsonPrimitive.content.contains("user's Android screen"))
        val messages = body["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        val firstMessage = messages[0].jsonObject
        assertEquals("user", firstMessage["role"]!!.jsonPrimitive.content)
        val userContent = firstMessage["content"]!!.jsonPrimitive.content
        assertTrue(userContent.contains("Android 15"))
        assertTrue(userContent.contains("what version of Android am I running?"))
    }

    @Test
    fun `retries once on HTTP 503 then succeeds`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("temp"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":[{"type":"text","text":"ok"}]}""",
            ),
        )

        val answer = runBlocking {
            client().answer("q", inspection, "Settings")
        }

        assertEquals("ok", answer.text)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `fails on HTTP 400 without retry`() {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"error":"bad"}"""),
        )

        val ex = try {
            runBlocking { client().answer("q", inspection, "Settings") }
            fail("expected IOException"); return
        } catch (e: IOException) {
            e
        }
        assertTrue(ex.message!!.startsWith("Anthropic 400"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `ignores non-text content blocks`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":[{"type":"tool_use","id":"x"},{"type":"text","text":"answer"}]}""",
            ),
        )

        val answer = runBlocking { client().answer("q", inspection, "Settings") }
        assertEquals("answer", answer.text)
    }

    @Test
    fun `tolerates trailing slash on baseUrl`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"content":[{"type":"text","text":"ok"}]}""",
            ),
        )

        val client = CloudClaudeClient(
            apiKey = "k",
            baseUrl = server.url("/").toString().trimEnd('/') + "/",
            httpClient = http,
            json = json,
        )
        runBlocking { client.answer("q", inspection, "Settings") }
        assertEquals("/v1/messages", server.takeRequest().path)
    }
}
