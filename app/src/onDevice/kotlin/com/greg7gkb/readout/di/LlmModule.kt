package com.greg7gkb.readout.di

import com.greg7gkb.readout.llm.EchoClient
import com.greg7gkb.readout.llm.LlmClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `onDevice` flavor: placeholder [EchoClient] until Step 7 replaces it with
 * `AICoreClient` (Gemini Nano via AICore on Tensor-G3+ devices).
 *
 * Keeping the flavor wired and installable today means the borrowed Pixel 10
 * Pro can be set up end-to-end before the AICore impl lands — only this one
 * binding changes when it does.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLlmClient(impl: EchoClient): LlmClient
}
