package com.greg7gkb.readout.di

import com.greg7gkb.readout.wake.Activator
import com.greg7gkb.readout.wake.CompositeActivator
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
 *  - Activator → CompositeActivator (merges wake-word events + manual taps
 *    into a single Flow<Activation> for the orchestrator).
 *
 * ManualActivator stays available for direct injection — DebugCommandDispatcher
 * still uses it for the notification "Trigger" action.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WakeModule {

    @Binds
    @Singleton
    abstract fun bindWakeWordEngine(impl: OpenWakeWordEngine): WakeWordEngine

    @Binds
    @Singleton
    abstract fun bindActivator(impl: CompositeActivator): Activator
}
