package com.greg7gkb.readout.common.model

/** A foreground app the user is asking about. */
data class App(
    val packageName: String,
    val displayName: String? = null,
)
