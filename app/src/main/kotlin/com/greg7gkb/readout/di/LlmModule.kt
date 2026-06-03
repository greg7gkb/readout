package com.greg7gkb.readout.di

import com.greg7gkb.readout.llm.EchoClient
import com.greg7gkb.readout.llm.LlmClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Default LlmClient binding for all flavors during Phase 1: EchoClient.
 *
 * When real cloud and on-device implementations land, this binding moves
 * into per-flavor source sets (src/dev/, src/cloud/, src/onDevice/) so
 * each flavor wires the right impl.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLlmClient(impl: EchoClient): LlmClient
}
