package com.greg7gkb.readout.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permissions Readout needs before it can do useful work.
 * Accessibility-service binding and screen-capture consent are handled
 * by different system flows and live elsewhere.
 */
object OnboardingPermissions {

    val required: List<String>
        get() = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    fun allGranted(context: Context): Boolean = missing(context).isEmpty()

    fun missing(context: Context): List<String> = required.filter { perm ->
        ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
    }
}
