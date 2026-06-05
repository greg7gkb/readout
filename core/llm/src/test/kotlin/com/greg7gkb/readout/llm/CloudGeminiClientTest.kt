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

class CloudGeminiClientTest {

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

    private fun client() = CloudGeminiClient(
        apiKey = "key-xyz",
        baseUrl = server.url("/").toString(),
        httpClient = http,
        json = json,
    )

    @Test
    fun `posts Gemini request shape and parses candidate text`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"candidates":[{"content":{"role":"model","parts":[{"text":"Android 15."}]}}]}""",
            ),
        )

        val answer = runBlocking {
            client().answer(
                question = "what version of Android am I running?",
                screen = inspection,
                appName = "Settings",
            )
        }

        assertEquals("Android 15.", answer.text)
        assertNotNull(answer.latencyMillis)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        // Key is on the query string, not a header.
        val expectedPath = "/v1beta/models/${CloudGeminiClient.MODEL_ID}:generateContent?key=key-xyz"
        assertEquals(expectedPath, recorded.path)
        assertTrue(recorded.getHeader("content-type")!!.startsWith("application/json"))

        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        val systemParts = body["system_instruction"]!!.jsonObject["parts"]!!.jsonArray
        assertTrue(systemParts[0].jsonObject["text"]!!.jsonPrimitive.content.contains("user's Android screen"))

        val contents = body["contents"]!!.jsonArray
        assertEquals(1, contents.size)
        val userContent = contents[0].jsonObject
        assertEquals("user", userContent["role"]!!.jsonPrimitive.content)
        val userText = userContent["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(userText.contains("Android 15"))
        assertTrue(userText.contains("what version of Android am I running?"))

        val cfg = body["generationConfig"]!!.jsonObject
        assertEquals(CloudGeminiClient.MAX_OUTPUT_TOKENS, cfg["maxOutputTokens"]!!.jsonPrimitive.int)
    }

    @Test
    fun `retries once on HTTP 502 then succeeds`() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("temp"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""",
            ),
        )

        val answer = runBlocking { client().answer("q", inspection, "Settings") }
        assertEquals("ok", answer.text)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `fails on HTTP 403 without retry`() {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"error":"permission"}"""),
        )

        val ex = try {
            runBlocking { client().answer("q", inspection, "Settings") }
            fail("expected IOException"); return
        } catch (e: IOException) {
            e
        }
        assertTrue(ex.message!!.startsWith("Gemini 403"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `returns empty answer when candidates list is empty`() {
        // Safety filter or refusal path: Gemini sometimes returns an empty
        // candidates array. The client surfaces "" rather than crashing;
        // the orchestrator decides whether to speak nothing or a fallback.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"candidates":[]}"""),
        )

        val answer = runBlocking { client().answer("q", inspection, "Settings") }
        assertEquals("", answer.text)
    }
}
