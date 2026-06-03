package com.greg7gkb.readout.common.model

/** What the LLM produced in response to a [question + screen snapshot]. */
data class Answer(
    val text: String,
    val latencyMillis: Long? = null,
)
