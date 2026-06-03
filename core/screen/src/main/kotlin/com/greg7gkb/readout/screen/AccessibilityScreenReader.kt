package com.greg7gkb.readout.screen

import android.util.Log
import com.greg7gkb.readout.common.di.IoDispatcher
import com.greg7gkb.readout.common.model.ScreenSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [ScreenReader] backed by the live [ReadoutAccessibilityService].
 * Each call to [snapshot] grabs the current active-window root from the
 * system, walks it via [NodeWalker], and returns the result.
 *
 * If the service hasn't been bound yet (user hasn't enabled it in
 * Accessibility Settings, or the system just unbound it), returns an
 * empty snapshot rather than throwing — the LLM downstream can recognize
 * the empty case and answer "I can't see your screen right now."
 */
@Singleton
class AccessibilityScreenReader @Inject constructor(
    private val holder: ReadoutAccessibilityServiceHolder,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ScreenReader {

    override suspend fun snapshot(): ScreenSnapshot = withContext(io) {
        val service = holder.service
        if (service == null) {
            Log.w(TAG, "snapshot requested but accessibility service is not bound")
            return@withContext empty()
        }
        val root = service.rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "snapshot requested but rootInActiveWindow is null")
            return@withContext empty()
        }
        val nodes = NodeWalker.walk(root)
        val pkg = root.packageName?.toString().orEmpty()
        Log.i(TAG, "snapshot pkg=$pkg nodes=${nodes.size}")
        ScreenSnapshot(
            foregroundPackage = pkg,
            timestampMillis = System.currentTimeMillis(),
            nodes = nodes,
        )
    }

    private fun empty(): ScreenSnapshot = ScreenSnapshot(
        foregroundPackage = "",
        timestampMillis = System.currentTimeMillis(),
        nodes = emptyList(),
    )

    companion object {
        private const val TAG = "Readout/Screen"
    }
}
