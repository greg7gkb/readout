package com.greg7gkb.readout.wake

import com.greg7gkb.readout.common.model.Activation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tap-driven activator. UI (button), the foreground-service notification
 * action, and accessibility-shortcut handlers all call [trigger] when the
 * user wants to start a session.
 *
 * Backed by a SharedFlow with a one-slot drop-oldest buffer so a tap that
 * arrives just before a subscriber attaches isn't strictly lost, but rapid
 * back-to-back taps with no consumer don't pile up.
 */
@Singleton
class ManualActivator @Inject constructor() : Activator {

    private val _activations = MutableSharedFlow<Activation>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun activations(): Flow<Activation> = _activations.asSharedFlow()

    fun trigger(source: Source = Source.Tap) {
        val activation = when (source) {
            Source.Tap -> Activation.Tap(timestampMillis = System.currentTimeMillis())
            Source.NotificationAction ->
                Activation.NotificationAction(timestampMillis = System.currentTimeMillis())
        }
        _activations.tryEmit(activation)
    }

    enum class Source { Tap, NotificationAction }
}
