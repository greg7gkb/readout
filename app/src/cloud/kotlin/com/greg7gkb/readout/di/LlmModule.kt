package com.greg7gkb.readout.di

import com.greg7gkb.readout.llm.CloudLlmClient
import com.greg7gkb.readout.llm.LlmClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `cloud` flavor: [CloudLlmClient] — a per-call delegating client that routes
 * each [LlmClient.answer] to Claude Haiku or Gemini Flash depending on the
 * current [com.greg7gkb.readout.llm.CloudLlmConfig] selection. Default is
 * Claude. Switch at runtime by flipping the config — no rebuild needed for
 * Step 6's A/B query-variants pass.
 *
 * Requires `anthropic.api.key` (and optionally `gemini.api.key`) in
 * `local.properties`. An unset key surfaces as HTTP 401 from the provider on
 * the first call — obvious in logcat under `Readout/Session`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLlmClient(impl: CloudLlmClient): LlmClient
}
