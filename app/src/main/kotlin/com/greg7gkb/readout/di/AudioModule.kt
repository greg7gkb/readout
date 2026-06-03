package com.greg7gkb.readout.di

import com.greg7gkb.readout.audio.AndroidSpeechRecognizer
import com.greg7gkb.readout.audio.AndroidTtsEngine
import com.greg7gkb.readout.audio.SpeechRecognizer
import com.greg7gkb.readout.audio.TtsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 1 defaults wire the real Android-SDK-backed implementations.
 * Tests override these via TestInstaller / @TestInstallIn.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindSpeechRecognizer(impl: AndroidSpeechRecognizer): SpeechRecognizer

    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: AndroidTtsEngine): TtsEngine
}
