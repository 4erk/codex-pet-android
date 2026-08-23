package com.fourerk.codexpet.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fourerk.codexpet.R
import com.fourerk.codexpet.app.AppGraph
import com.fourerk.codexpet.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OverlayController
    private var collectors: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        controller = OverlayController(this, serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            serviceScope.launch { AppGraph.settings.setOverlayEnabled(false) }
            stopOverlay()
            return START_NOT_STICKY
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            AppGraph.diagnostics.error("Overlay service started without SYSTEM_ALERT_WINDOW grant")
            stopOverlay()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        startCollectors()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller.onConfigurationChanged()
    }

    override fun onDestroy() {
        collectors?.cancel()
        controller.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCollectors() {
        if (collectors != null) return
        collectors = serviceScope.launch {
            launch {
                AppGraph.settings.updates.collectLatest { settings ->
                    if (!settings.overlayEnabled) {
                        stopOverlay()
                    } else {
                        controller.applySettings(settings)
                        controller.show()
                    }
                }
            }
            launch { AppGraph.pets.visual.collectLatest(controller::setPet) }
            launch { AppGraph.tasks.tasks.collectLatest(controller::setTasks) }
            launch { AppGraph.tasks.transitions.collectLatest(controller::onTaskTransition) }
        }
    }

    private fun startForegroundCompat() {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hideIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OverlayService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_notification, getString(R.string.hide_pet), hideIntent)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.overlay_channel_description)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    private fun stopOverlay() {
        controller.destroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.fourerk.codexpet.action.START_OVERLAY"
        const val ACTION_HIDE = "com.fourerk.codexpet.action.HIDE_OVERLAY"
        private const val CHANNEL_ID = "codex_pet_overlay"
        private const val NOTIFICATION_ID = 4101
    }
}
