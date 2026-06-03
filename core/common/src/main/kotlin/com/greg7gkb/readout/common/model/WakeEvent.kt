package com.greg7gkb.readout.common.model

/** A single detection from a [WakeWordEngine]. */
data class WakeEvent(
    val timestampMillis: Long,
    val confidence: Float? = null,
)
