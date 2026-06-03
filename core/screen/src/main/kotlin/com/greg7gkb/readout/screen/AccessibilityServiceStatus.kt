package com.greg7gkb.readout.screen

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Whether the user has enabled [ReadoutAccessibilityService] in system
 * Accessibility Settings, plus an Intent to send them there to do so.
 *
 * Lives in :core:screen so the FQN of the service stays colocated with
 * the service class itself; :feature:onboarding calls these without
 * having to know what the concrete service is named.
 */
object AccessibilityServiceStatus {

    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val target = ReadoutAccessibilityService::class.java.name
        return enabled.any { it.resolveInfo.serviceInfo.name == target }
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
