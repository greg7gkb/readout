package com.greg7gkb.readout.screen

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.util.Log
import com.greg7gkb.readout.common.di.IoDispatcher
import com.greg7gkb.readout.common.model.ScreenInspection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [ScreenReader] backed by the live [ReadoutAccessibilityService].
 * Each call to [inspect] grabs the current active-window root from the
 * system, walks it via [NodeWalker], and returns a [ScreenReadResult].
 *
 * Returns [ScreenReadResult.Unavailable] rather than an empty inspection
 * when the service isn't bound or the active root is unreadable, so the
 * orchestrator can fail closed (spoken error, no LLM call) instead of
 * relying on the model to recognize an empty node list.
 */
@Singleton
class AccessibilityScreenReader @Inject constructor(
    private val holder: ReadoutAccessibilityServiceHolder,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ScreenReader {

    // SupervisorJob keeps this collector alive for the process lifetime —
    // there's no Activity scope to tie it to, and the holder's state is
    // process-wide. Eagerly started so first UI read reflects current state.
    private val availabilityScope = CoroutineScope(SupervisorJob() + io)
    override val availability: StateFlow<Boolean> = holder.service
        .map { it != null }
        .stateIn(availabilityScope, SharingStarted.Eagerly, holder.service.value != null)

    override suspend fun inspect(): ScreenReadResult = withContext(io) {
        val service = holder.service.value
        if (service == null) {
            Log.w(TAG, "inspect: service not bound")
            return@withContext ScreenReadResult.Unavailable(
                ScreenReadResult.Unavailable.Reason.SERVICE_NOT_BOUND,
            )
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
            Log.w(TAG, "inspect: rootInActiveWindow is null")
            return@withContext ScreenReadResult.Unavailable(
                ScreenReadResult.Unavailable.Reason.ROOT_NOT_AVAILABLE,
            )
        }
        val nodes = NodeWalker.walk(root)
        val pkg = root.packageName?.toString().orEmpty()
        val label = resolveAppLabel(service, pkg)
        Log.i(TAG, "inspection pkg=$pkg label=$label nodes=${nodes.size}")
        ScreenReadResult.Available(
            ScreenInspection(
                foregroundPackage = pkg,
                timestampMillis = System.currentTimeMillis(),
                nodes = nodes,
                foregroundAppLabel = label,
            ),
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

    companion object {
        private const val TAG = "Readout/Screen"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val SHADE_DISMISS_DELAY_MS = 300L
    }
}
