package com.greg7gkb.readout.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.greg7gkb.readout.screen.AccessibilityServiceStatus

/**
 * Things Readout needs from the user before it can do useful work.
 *
 * Modeled as a sealed class because two kinds of grants are in play and
 * they take different flows: runtime permissions go through the system
 * permission dialog; the accessibility service has to be enabled via
 * Settings deep-link. MediaProjection consent (Phase 5) and wake-word
 * mic-keepalive consent (Phase 4) will slot in here too.
 */
sealed class OnboardingRequirement {
    data class RuntimePermission(val name: String) : OnboardingRequirement()
    data object AccessibilityService : OnboardingRequirement()
}

object OnboardingPermissions {

    private val runtime: List<OnboardingRequirement.RuntimePermission>
        get() = buildList {
            add(OnboardingRequirement.RuntimePermission(Manifest.permission.RECORD_AUDIO))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(OnboardingRequirement.RuntimePermission(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

    fun allGranted(context: Context): Boolean = missing(context).isEmpty()

    fun missing(context: Context): List<OnboardingRequirement> = buildList {
        addAll(runtime.filter { !it.isGranted(context) })
        if (!AccessibilityServiceStatus.isEnabled(context)) {
            add(OnboardingRequirement.AccessibilityService)
        }
    }

    private fun OnboardingRequirement.RuntimePermission.isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
}
