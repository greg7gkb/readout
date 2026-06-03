package com.greg7gkb.readout.di

import com.greg7gkb.readout.screen.AccessibilityScreenReader
import com.greg7gkb.readout.screen.ScreenReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the real AccessibilityScreenReader: each inspect() pulls a fresh
 * view-hierarchy walk from the live ReadoutAccessibilityService. The
 * Phase 1 FakeScreenReader is no longer wired; it stays in the source
 * tree as a reference for future test/dev overrides.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenModule {

    @Binds
    @Singleton
    abstract fun bindScreenReader(impl: AccessibilityScreenReader): ScreenReader
}
