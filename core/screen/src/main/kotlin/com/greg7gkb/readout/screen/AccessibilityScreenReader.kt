package com.greg7gkb.readout.screen

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.util.Log
import com.greg7gkb.readout.common.di.IoDispatcher
import com.greg7gkb.readout.common.model.ScreenInspection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [ScreenReader] backed by the live [ReadoutAccessibilityService].
 * Each call to [inspect] grabs the current active-window root from the
 * system, walks it via [NodeWalker], and returns the result.
 *
 * If the service hasn't been bound yet (user hasn't enabled it in
 * Accessibility Settings, or the system just unbound it), returns an
 * empty inspection rather than throwing — the LLM downstream can recognize
 * the empty case and answer "I can't see your screen right now."
 */
@Singleton
class AccessibilityScreenReader @Inject constructor(
    private val holder: ReadoutAccessibilityServiceHolder,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ScreenReader {

    override suspend fun inspect(): ScreenInspection = withContext(io) {
        val service = holder.service
        if (service == null) {
            Log.w(TAG, "inspect requested but accessibility service is not bound")
            return@withContext empty()
        }

        var root = service.rootInActiveWindow
        if (root?.packageName == SYSTEM_UI_PACKAGE) {
            // Notification shade or quick settings is the focused window
            // (e.g. user triggered inspect via the notification action).
            // Dismiss and re-read so we capture the underlying foreground
            // app instead of the shade's own view tree.
            Log.i(TAG, "active window is $SYSTEM_UI_PACKAGE; dismissing shade and retrying")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            delay(SHADE_DISMISS_DELAY_MS)
            root = service.rootInActiveWindow
        }

        if (root == null) {
            Log.w(TAG, "inspect requested but rootInActiveWindow is null")
            return@withContext empty()
        }
        val nodes = NodeWalker.walk(root)
        val pkg = root.packageName?.toString().orEmpty()
        val label = resolveAppLabel(service, pkg)
        Log.i(TAG, "inspection pkg=$pkg label=$label nodes=${nodes.size}")
        ScreenInspection(
            foregroundPackage = pkg,
            timestampMillis = System.currentTimeMillis(),
            nodes = nodes,
            foregroundAppLabel = label,
        )
    }

    private fun resolveAppLabel(service: AccessibilityService, pkg: String): String? {
        if (pkg.isBlank()) return null
        return try {
            val info = service.packageManager.getApplicationInfo(pkg, 0)
            service.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun empty(): ScreenInspection = ScreenInspection(
        foregroundPackage = "",
        timestampMillis = System.currentTimeMillis(),
        nodes = emptyList(),
    )

    companion object {
        private const val TAG = "Readout/Screen"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val SHADE_DISMISS_DELAY_MS = 300L
    }
}
