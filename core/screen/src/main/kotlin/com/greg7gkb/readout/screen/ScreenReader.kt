package com.greg7gkb.readout.screen

import kotlinx.coroutines.flow.StateFlow

/**
 * Captures structured text from the currently foregrounded app.
 *
 * Implementations:
 *  - [FakeScreenReader] — dev stub returning a hardcoded generic-app snapshot
 *  - [AccessibilityScreenReader] — walks AccessibilityNodeInfo tree (Phase 2)
 *  - MediaProjectionScreenReader — screenshot + OCR fallback (Phase 5)
 *
 * Two surfaces:
 *  - [inspect] returns a per-call [ScreenReadResult] — Available or Unavailable
 *    with a reason. Callers fail closed on Unavailable rather than passing an
 *    empty inspection to the LLM.
 *  - [availability] is a continuous signal of whether [inspect] would *probably*
 *    succeed right now. UI observes it to show a "reader is off" banner. It is
 *    advisory — `inspect()` is still the source of truth for any individual call
 *    because state can flip between an observation and the next attempt.
 */
interface ScreenReader {
    suspend fun inspect(): ScreenReadResult

    val availability: StateFlow<Boolean>
}
