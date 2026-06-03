package com.greg7gkb.readout.screen

import com.greg7gkb.readout.common.model.ScreenSnapshot

/**
 * Captures structured text from the currently foregrounded app.
 *
 * Implementations:
 *  - [FakeScreenReader] — dev stub returning a hardcoded generic-app snapshot
 *  - AccessibilityScreenReader — walks AccessibilityNodeInfo tree (Phase 2)
 *  - MediaProjectionScreenReader — screenshot + OCR fallback (Phase 5)
 */
interface ScreenReader {
    suspend fun snapshot(): ScreenSnapshot
}
