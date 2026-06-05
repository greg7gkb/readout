package com.greg7gkb.readout.llm

import javax.inject.Qualifier

/**
 * Hilt qualifiers for cloud-LLM credentials and endpoints. The values are
 * provided in `:app`'s DI graph from `BuildConfig` strings derived from
 * `local.properties`. Tests construct clients directly and bypass Hilt.
 */

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnthropicApiKey

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnthropicBaseUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiApiKey

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiBaseUrl
