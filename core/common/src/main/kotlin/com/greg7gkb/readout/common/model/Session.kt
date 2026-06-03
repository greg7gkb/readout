package com.greg7gkb.readout.common.model

import java.util.UUID

/** One activation-to-answer cycle, tagged for log correlation. */
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val startedAtMillis: Long = System.currentTimeMillis(),
)
