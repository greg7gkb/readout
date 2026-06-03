package com.greg7gkb.readout.debug

import android.content.Intent
import android.util.Log
import com.greg7gkb.readout.screen.ScreenReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generalized dispatcher for ADB- and notification-driven debug commands.
 *
 * Adding a new command is a one-line entry in [commands] — a name and a
 * [DebugCommand] lambda. The lambda gets the originating [Intent] so it
 * can read extras (e.g. `--es app com.example.weather`) for future
 * commands that need parameters.
 *
 * Commands run on a singleton-owned IO scope: invocations are
 * fire-and-forget from the receiver's point of view (which is on the
 * main thread and must return quickly).
 *
 * Invocation paths:
 *   - [DebugCommandReceiver] receives `am broadcast` from adb
 *   - The session-notification "Inspect" action fires the same broadcast
 */
@Singleton
class DebugCommandDispatcher @Inject constructor(
    private val screenReader: ScreenReader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val commands: Map<String, DebugCommand> = mapOf(
        CMD_INSPECT to DebugCommand { _ ->
            val inspection = screenReader.inspect()
            Log.i(
                TAG,
                "inspect pkg=${inspection.foregroundPackage} nodes=${inspection.nodes.size}",
            )
            inspection.nodes.forEachIndexed { i, node ->
                Log.i(TAG, "  [$i] text=${node.text} desc=${node.contentDescription} cls=${node.className}")
            }
        },
    )

    fun dispatch(cmd: String?, intent: Intent) {
        if (cmd == null) {
            Log.w(TAG, "missing $EXTRA_CMD extra")
            return
        }
        val handler = commands[cmd]
        if (handler == null) {
            Log.w(TAG, "unknown cmd=$cmd. known=${commands.keys}")
            return
        }
        scope.launch { handler.execute(intent) }
    }

    companion object {
        const val EXTRA_CMD = "cmd"
        const val CMD_INSPECT = "inspect"
        private const val TAG = "Readout/Debug"
    }
}

fun interface DebugCommand {
    suspend fun execute(intent: Intent)
}
