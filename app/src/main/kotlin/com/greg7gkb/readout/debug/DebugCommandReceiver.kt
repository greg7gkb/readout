package com.greg7gkb.readout.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Thin Android-layer adapter: receives the debug-command broadcast and
 * hands the cmd name + intent to [DebugCommandDispatcher]. The dispatcher
 * is where commands are actually registered and run.
 *
 * Invocation from adb:
 *
 *     adb shell am broadcast \
 *         -a com.greg7gkb.readout.action.DEBUG_COMMAND \
 *         --es cmd inspect \
 *         -p com.greg7gkb.readout.dev
 *
 * Exported because adb shell runs as a different UID; the action is
 * namespaced to our package so collision risk is negligible. Receiver
 * does nothing on missing/unknown cmd extras except log.
 */
@AndroidEntryPoint
class DebugCommandReceiver : BroadcastReceiver() {

    @Inject
    lateinit var dispatcher: DebugCommandDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        dispatcher.dispatch(intent.getStringExtra(DebugCommandDispatcher.EXTRA_CMD), intent)
    }

    companion object {
        const val ACTION = "com.greg7gkb.readout.action.DEBUG_COMMAND"
    }
}
