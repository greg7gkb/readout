package com.greg7gkb.readout.di

import com.greg7gkb.readout.wake.Activator
import com.greg7gkb.readout.wake.ManualActivator
import com.greg7gkb.readout.wake.NoopWakeWordEngine
import com.greg7gkb.readout.wake.WakeWordEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 1 defaults:
 *  - WakeWordEngine → NoopWakeWordEngine (real Porcupine binding lands in Phase 4)
 *  - Activator → ManualActivator (Step 11 wires button + notification action to this)
 *
 * Note: a CompositeActivator that merges wake-word events + manual taps will
 * replace the direct ManualActivator binding once Phase 4 lands.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WakeModule {

    @Binds
    @Singleton
    abstract fun bindWakeWordEngine(impl: NoopWakeWordEngine): WakeWordEngine

    @Binds
    @Singleton
    abstract fun bindActivator(impl: ManualActivator): Activator
}
