package com.greg7gkb.readout.di

import com.greg7gkb.readout.screen.FakeScreenReader
import com.greg7gkb.readout.screen.ScreenReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 1 default: FakeScreenReader. The real AccessibilityScreenReader
 * lands in Phase 2 and will replace this binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenModule {

    @Binds
    @Singleton
    abstract fun bindScreenReader(impl: FakeScreenReader): ScreenReader
}
