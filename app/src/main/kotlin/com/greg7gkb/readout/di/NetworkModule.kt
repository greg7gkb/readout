package com.greg7gkb.readout.di

import com.greg7gkb.readout.BuildConfig
import com.greg7gkb.readout.llm.AnthropicApiKey
import com.greg7gkb.readout.llm.AnthropicBaseUrl
import com.greg7gkb.readout.llm.GeminiApiKey
import com.greg7gkb.readout.llm.GeminiBaseUrl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Shared networking + JSON plumbing for cloud-LLM clients.
 *
 * Lives in `:app` (not `:core:llm`) so credential reads from [BuildConfig] —
 * which is regenerated per build with the local.properties values baked in —
 * stay in the application module. The `:core:llm` clients receive the strings
 * via Hilt qualifiers without ever importing BuildConfig.
 *
 * Read timeout is generous (30s) — Claude Haiku is typically ~1s but the long
 * tail matters for the 3-second per-call budget's worst case. Connect timeout
 * stays tight (5s) because a slow connect is almost always a real failure.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // LLM providers add response fields over time (token counts, cache
        // hits, model-version stamps). Don't blow up on those.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @AnthropicApiKey
    fun provideAnthropicApiKey(): String = BuildConfig.ANTHROPIC_API_KEY

    @Provides
    @AnthropicBaseUrl
    fun provideAnthropicBaseUrl(): String = "https://api.anthropic.com"

    @Provides
    @GeminiApiKey
    fun provideGeminiApiKey(): String = BuildConfig.GEMINI_API_KEY

    @Provides
    @GeminiBaseUrl
    fun provideGeminiBaseUrl(): String = "https://generativelanguage.googleapis.com"
}
