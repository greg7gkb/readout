package com.greg7gkb.readout.llm

import com.greg7gkb.readout.common.model.ScreenInspection

/**
 * Renders a [ScreenInspection] as a compact, line-based block that an LLM can read.
 *
 * Format chosen by eyeballing the Settings sample dump in `docs/phase3_target.md`:
 * the [com.greg7gkb.readout.screen.NodeWalker] emits nodes in document order, and
 * on accessibility-clean apps that order matches reading order with label /
 * subtitle pairs adjacent. A flat bulleted list preserves that ordering without
 * spending tokens on JSON structure or pixel bounds.
 *
 * If a future target app uses a two-column layout where document order diverges
 * from reading order, the right move is to enrich this format with bounds, not
 * to switch to a heavier schema on every screen.
 *
 * Per-node text resolution: prefer [com.greg7gkb.readout.common.model.ScreenNode.text]
 * (the authoritative on-screen string) over [contentDescription][com.greg7gkb.readout.common.model.ScreenNode.contentDescription]
 * (often a TalkBack hint that duplicates or wraps the text). Fall back to the
 * description when text is absent — buttons in Maps were exactly this case.
 */
fun ScreenInspection.toPromptText(): String {
    val header = buildString {
        append("Foreground app: ")
        val pkg = foregroundPackage.ifBlank { "unknown" }
        val label = foregroundAppLabel?.takeIf { it.isNotBlank() && it != foregroundPackage }
        if (label != null) {
            append(label)
            append(" (package: ")
            append(pkg)
            append(")")
        } else {
            append(pkg)
        }
    }

    val lines = nodes.asSequence()
        .mapNotNull { node ->
            node.text?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.takeIf { it.isNotBlank() }
        }
        .toList()

    val body = if (lines.isEmpty()) {
        "No visible text was detected on screen."
    } else {
        buildString {
            append("Visible content (in document order):\n")
            lines.joinTo(this, separator = "\n") { "- $it" }
        }
    }

    return "$header\n$body"
}
