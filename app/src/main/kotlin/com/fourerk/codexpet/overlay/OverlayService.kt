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
import com.fourerk.codexpet.app.HomeActivity
import com.fourerk.codexpet.notification.ChatGptNotificationListener
import com.fourerk.codexpet.pet.PetAnimationState
import com.fourerk.codexpet.settings.AppSettings
import com.fourerk.codexpet.system.SystemAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: OverlayControllerV2
    private var collectors: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        controller = OverlayControllerV2(this, serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            AppGraph.diagnostics.error("Overlay service started without SYSTEM_ALERT_WINDOW grant")
            stopOverlay()
            return START_NOT_STICKY
        }

        startForegroundCompat(AppGraph.settings.settings.value)
        startCollectors()
        when (intent?.action) {
            ACTION_HIDE -> serviceScope.launch { AppGraph.settings.setPetVisible(false) }
            ACTION_SHOW -> serviceScope.launch { AppGraph.settings.setPetVisible(true) }
            ACTION_TOGGLE_AUTO_BUBBLES -> serviceScope.launch {
                val enabled = AppGraph.settings.settings.value.autoTaskBubblesEnabled
                AppGraph.settings.setAutoTaskBubblesEnabled(!enabled)
            }
            ACTION_MORE_SPEECH -> controller.showMoreSpeech()
            ACTION_PREVIEW_ANIMATION -> {
                val state = intent.getStringExtra(EXTRA_ANIMATION_STATE)
                    ?.let { runCatching { PetAnimationState.valueOf(it) }.getOrNull() }
                controller.previewAnimation(state)
            }
        }
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
                        startForegroundCompat(settings)
                        controller.applySettings(settings)
                        controller.show()
                    }
                }
            }
            launch { AppGraph.pets.visual.collectLatest(controller::setPet) }
            launch { AppGraph.tasks.tasks.collectLatest(controller::setTasks) }
            launch { AppGraph.tasks.transitions.collectLatest(controller::onTaskTransition) }
            launch {
                while (true) {
                    if (SystemAccess.hasNotificationAccess(this@OverlayService)) {
                        ChatGptNotificationListener.ensureHealthy(this@OverlayService)
                    }
                    delay(LISTENER_HEALTH_PULSE_MS)
                }
            }
            launch {
                while (true) {
                    AppGraph.updates.checkIfDue()
                    delay(UPDATE_PULSE_MS)
                }
            }
        }
    }

    private fun startForegroundCompat(settings: AppSettings) {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, HomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val visibilityIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OverlayService::class.java).setAction(
                if (settings.petVisible) ACTION_HIDE else ACTION_SHOW,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val bubblesIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, OverlayService::class.java).setAction(ACTION_TOGGLE_AUTO_BUBBLES),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val moreSpeechIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, OverlayService::class.java).setAction(ACTION_MORE_SPEECH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stateText = getString(
            R.string.overlay_notification_state,
            getString(if (settings.petVisible) R.string.pet_visible else R.string.pet_hidden),
            getString(
                if (settings.autoTaskBubblesEnabled) R.string.auto_bubbles_enabled
                else R.string.auto_bubbles_disabled,
            ),
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(stateText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_notification,
                getString(if (settings.petVisible) R.string.hide_pet else R.string.show_pet),
                visibilityIntent,
            )
            .addAction(
                R.drawable.ic_notification,
                getString(
                    if (settings.autoTaskBubblesEnabled) R.string.disable_auto_bubbles
                    else R.string.enable_auto_bubbles,
                ),
                bubblesIntent,
            )
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.more_speech),
                moreSpeechIntent,
            )
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
        const val ACTION_SHOW = "com.fourerk.codexpet.action.SHOW_OVERLAY"
        const val ACTION_TOGGLE_AUTO_BUBBLES = "com.fourerk.codexpet.action.TOGGLE_AUTO_BUBBLES"
        const val ACTION_MORE_SPEECH = "com.fourerk.codexpet.action.MORE_SPEECH"
        const val ACTION_PREVIEW_ANIMATION = "com.fourerk.codexpet.action.PREVIEW_ANIMATION"
        const val EXTRA_ANIMATION_STATE = "animation_state"
        private const val CHANNEL_ID = "codex_pet_overlay"
        private const val NOTIFICATION_ID = 4101
        private const val LISTENER_HEALTH_PULSE_MS = 30_000L
        private const val UPDATE_PULSE_MS = 30L * 60L * 1_000L
    }
}
