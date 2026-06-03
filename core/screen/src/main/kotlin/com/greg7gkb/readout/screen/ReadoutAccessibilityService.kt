package com.greg7gkb.readout.screen

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Phase 2 Step 1: empty AccessibilityService that registers in the system.
 *
 * Wired up in later steps:
 *   - Step 3 adds the pure node-tree walker
 *   - Step 4 backs AccessibilityScreenReader with the latest root node from this service
 *   - Step 5 swaps FakeScreenReader for AccessibilityScreenReader in DI
 */
class ReadoutAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        Log.i(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op for Step 1. Real handling lands in Step 4.
    }

    override fun onInterrupt() {
        Log.i(TAG, "service interrupted")
    }

    companion object {
        private const val TAG = "Readout/Screen"
    }
}
