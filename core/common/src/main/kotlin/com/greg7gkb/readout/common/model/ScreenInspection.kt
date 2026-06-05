package com.greg7gkb.readout.common.model

/**
 * The structured result of a screen-reader inspection — labeled text and
 * descriptions visible on the foreground app at a moment in time. Designed
 * to serialize cleanly for transport to an LLM — keep fields JSON-friendly.
 */
data class ScreenInspection(
    val foregroundPackage: String,
    val timestampMillis: Long,
    val nodes: List<ScreenNode>,
    /**
     * Human-readable app label resolved via [android.content.pm.PackageManager.getApplicationLabel],
     * e.g. "Settings" for `com.android.settings`. Null when the lookup failed (package not
     * installed, query restrictions). Prompt builders should fall back to [foregroundPackage].
     */
    val foregroundAppLabel: String? = null,
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
