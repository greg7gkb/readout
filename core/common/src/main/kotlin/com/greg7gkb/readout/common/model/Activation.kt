package com.greg7gkb.readout.common.model

/** How the current session was initiated. */
sealed interface Activation {
    val timestampMillis: Long

    data class WakeWord(
        override val timestampMillis: Long,
        val confidence: Float? = null,
    ) : Activation

    data class Tap(
        override val timestampMillis: Long,
    ) : Activation

    data class NotificationAction(
        override val timestampMillis: Long,
    ) : Activation
}
