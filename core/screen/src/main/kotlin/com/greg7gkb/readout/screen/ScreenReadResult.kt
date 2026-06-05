package com.greg7gkb.readout.screen

import com.greg7gkb.readout.common.model.ScreenInspection

/**
 * The outcome of a [ScreenReader.inspect] call. Returning a sealed type rather
 * than a possibly-empty [ScreenInspection] keeps "I can't see anything" out of
 * the LLM's hands — the orchestrator deals with [Unavailable] deterministically
 * (spoken error, no token spend) rather than relying on the model to recognize
 * an empty node list and gracefully refuse.
 *
 * Phase 5's MediaProjection fallback will add its own [Unavailable.Reason]
 * values (capture permission denied, capture session expired) — the sealed
 * shape gives future impls room without breaking callers.
 */
sealed interface ScreenReadResult {

    /** The screen was read successfully. Empty [inspection.nodes] is legal here
     * (e.g. the foreground app is showing only canvas content) — distinct from
     * Unavailable, which means we couldn't read at all. */
    data class Available(val inspection: ScreenInspection) : ScreenReadResult

    /** The screen reader couldn't produce an inspection. UI / orchestrator should
     * fail closed and surface [reason] to the user rather than asking the LLM. */
    data class Unavailable(val reason: Reason) : ScreenReadResult {

        enum class Reason {
            /** AccessibilityService isn't currently bound — either the user hasn't
             * enabled it in Settings, or the process was force-stopped / killed
             * since it last bound. The user must toggle the service to recover. */
            SERVICE_NOT_BOUND,

            /** Service is bound but `getRootInActiveWindow()` returned null —
             * usually transient (a window in transition, an app launching). The
             * user can simply retry. */
            ROOT_NOT_AVAILABLE,
        }
    }
}
