package com.greg7gkb.readout.di

import com.greg7gkb.readout.llm.EchoClient
import com.greg7gkb.readout.llm.LlmClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `dev` flavor: [EchoClient] — no network, no model. Used for fast iteration on
 * the pipeline (orchestrator, STT, screen reader, TTS) without waiting on a
 * real LLM. The echoed-reverse response is the unmistakable "we hit Echo, not
 * a real model" signal in logcat.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLlmClient(impl: EchoClient): LlmClient
}
