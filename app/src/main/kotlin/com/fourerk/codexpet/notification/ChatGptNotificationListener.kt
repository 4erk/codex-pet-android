package com.fourerk.codexpet.notification

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.fourerk.codexpet.BuildConfig
import com.fourerk.codexpet.app.AppGraph
import com.fourerk.codexpet.task.TaskKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatGptNotificationListener : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        scanActive("LISTENER_CONNECTED")
    }

    override fun onListenerDisconnected() {
        AppGraph.diagnostics.listenerDisconnected()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isConfiguredSource(sbn)) return
        process(sbn, "POSTED", updateTask = true, acceptPet = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isConfiguredSource(sbn)) return
        AppGraph.tasks.remove(sbn.key)
        process(sbn, "REMOVED", updateTask = false, acceptPet = false)
        updateActiveCount()
    }

    private fun scanActive(event: String) {
        val captured = runCatching { activeNotifications.orEmpty().toList() }
            .onFailure { AppGraph.diagnostics.error("getActiveNotifications: ${it.javaClass.simpleName}") }
            .getOrDefault(emptyList())
        val sourcePackage = AppGraph.settings.settings.value.sourcePackage
        val matching = captured.filter { it.packageName == sourcePackage }
        AppGraph.applicationScope.launch {
            val parsed = matching.mapNotNull { sbn ->
                parse(sbn, event, acceptPet = true)?.task
            }
            AppGraph.tasks.replaceAll(parsed)
            AppGraph.diagnostics.listenerConnected(sourcePackage, matching.size)
        }
    }

    private fun process(sbn: StatusBarNotification, event: String, updateTask: Boolean, acceptPet: Boolean) {
        AppGraph.applicationScope.launch {
            val parsed = parse(sbn, event, acceptPet) ?: return@launch
            if (updateTask) AppGraph.tasks.upsert(parsed.task, liveNotification = event == "POSTED")
            updateActiveCount()
        }
    }

    private suspend fun parse(
        sbn: StatusBarNotification,
        event: String,
        acceptPet: Boolean,
    ): ParsedNotification? = withContext(Dispatchers.Default) {
        runCatching {
            val inspection = AppGraph.petAssets.inspect(sbn.notification, sbn.packageName)
            val parsed = AppGraph.notificationParser.parse(
                sbn = sbn,
                event = event,
                petInspection = inspection,
                includeDebugText = BuildConfig.DEBUG,
            )
            AppGraph.diagnostics.record(parsed.snapshot)
            if (acceptPet && parsed.task.kind != TaskKind.CHAT_MESSAGE) inspection.selected?.let {
                AppGraph.pets.acceptAutoCandidate(it, sbn.packageName)
            }
            parsed
        }.onFailure {
            AppGraph.diagnostics.error("notification parse: ${it.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun isConfiguredSource(sbn: StatusBarNotification): Boolean =
        sbn.packageName == AppGraph.settings.settings.value.sourcePackage

    private fun updateActiveCount() {
        val sourcePackage = AppGraph.settings.settings.value.sourcePackage
        val count = runCatching {
            activeNotifications.orEmpty().count { it.packageName == sourcePackage }
        }.getOrDefault(AppGraph.diagnostics.listener.value.activeNotificationCount)
        AppGraph.diagnostics.activeCount(count)
    }

    companion object {
        @Volatile
        private var instance: ChatGptNotificationListener? = null

        fun refresh(context: Context) {
            instance?.scanActive("MANUAL_REFRESH") ?: requestRebind(
                ComponentName(context, ChatGptNotificationListener::class.java),
            )
        }

        fun restart(context: Context) {
            val component = ComponentName(context, ChatGptNotificationListener::class.java)
            instance?.let { runCatching { it.requestUnbind() } }
            Handler(Looper.getMainLooper()).postDelayed({ requestRebind(component) }, 500L)
        }
    }
}
