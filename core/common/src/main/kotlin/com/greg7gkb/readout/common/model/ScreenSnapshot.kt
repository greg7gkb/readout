package com.greg7gkb.readout.common.model

/**
 * Structured representation of what's currently on screen, produced by a [ScreenReader].
 * Designed to serialize cleanly for transport to an LLM — keep fields JSON-friendly.
 */
data class ScreenSnapshot(
    val foregroundPackage: String,
    val timestampMillis: Long,
    val nodes: List<ScreenNode>,
)

data class ScreenNode(
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val viewIdResourceName: String? = null,
    val bounds: Bounds? = null,
)

data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
