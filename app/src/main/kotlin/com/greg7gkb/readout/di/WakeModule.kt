package com.greg7gkb.readout.di

import com.greg7gkb.readout.wake.Activator
import com.greg7gkb.readout.wake.ManualActivator
import com.greg7gkb.readout.wake.OpenWakeWordEngine
import com.greg7gkb.readout.wake.WakeWordEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings:
 *  - WakeWordEngine → OpenWakeWordEngine (OWW + onnxruntime-android, "Hey Jarvis").
 *    Loaded from `core/wake/src/main/assets/wake/`; see Phase 4.1 commits for
 *    the model fetch story. Real detection runs once the lifecycle owner —
 *    ReadoutService, wired in Phase 4.5 — starts collecting events().
 *  - Activator → ManualActivator (notification action + future tap-to-talk).
 *    Phase 4.3 will replace this with a CompositeActivator merging wake-word
 *    events + manual taps into one stream.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WakeModule {

    @Binds
    @Singleton
    abstract fun bindWakeWordEngine(impl: OpenWakeWordEngine): WakeWordEngine

    @Binds
    @Singleton
    abstract fun bindActivator(impl: ManualActivator): Activator
}
