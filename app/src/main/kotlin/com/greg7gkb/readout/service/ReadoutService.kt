package com.greg7gkb.readout.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.greg7gkb.readout.MainActivity
import com.greg7gkb.readout.R
import com.greg7gkb.readout.session.SessionOrchestrator
import com.greg7gkb.readout.session.SessionState
import com.greg7gkb.readout.session.summary
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that owns the active session. Holds the orchestrator's
 * coroutine scope for as long as the user wants Readout listening, and
 * publishes a persistent notification reflecting the current SessionState
 * so the user always knows when the microphone could be active.
 *
 * The "Stop" notification action and [stop] both result in service
 * termination, which cancels the orchestrator's collector via scope.
 */
@AndroidEntryPoint
class ReadoutService : LifecycleService() {

    @Inject
    lateinit var orchestrator: SessionOrchestrator

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        Log.i(TAG, "service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "ACTION_STOP received")
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground(SessionState.Idle)
        orchestrator.start(lifecycleScope)
        lifecycleScope.launch {
            orchestrator.state.collect { state ->
                Log.i(TAG, "state → ${state.summary()}")
                updateNotification(state)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "service destroyed")
        orchestrator.stop()
        super.onDestroy()
    }

    private fun startInForeground(state: SessionState) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForegroundQ(notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startForegroundQ(notification: Notification) {
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
    }

    private fun updateNotification(state: SessionState) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: SessionState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            REQ_OPEN,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQ_STOP,
            Intent(this, ReadoutService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Readout")
            .setContentText(state.summary())
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Readout session",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shown while Readout is actively listening for a wake word or query."
                },
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "readout-session"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.greg7gkb.readout.action.STOP"

        private const val REQ_OPEN = 100
        private const val REQ_STOP = 101
        private const val TAG = "Readout/Service"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReadoutService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReadoutService::class.java))
        }
    }
}
