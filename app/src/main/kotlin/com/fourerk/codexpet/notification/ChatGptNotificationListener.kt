package com.fourerk.codexpet.notification

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.fourerk.codexpet.BuildConfig
import com.fourerk.codexpet.app.AppGraph
import com.fourerk.codexpet.pet.PetInspection
import com.fourerk.codexpet.task.NotificationSetReducer
import com.fourerk.codexpet.task.TaskKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ChatGptNotificationListener : NotificationListenerService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val eventSequence = AtomicLong(0L)
    private val latestEventByKey = ConcurrentHashMap<String, Long>()
    private var connected = false

    private val reconcileRunnable = Runnable {
        if (connected) scanActive(
            event = "RECONCILE",
            inspectPet = false,
            acceptPet = false,
            recordDiagnostics = false,
            announceConnection = false,
        )
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!connected) return
            scanActive(
                event = "WATCHDOG",
                inspectPet = false,
                acceptPet = false,
                recordDiagnostics = false,
                announceConnection = false,
            )
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private val rebindRunnable = Runnable {
        if (!connected) {
            requestRebind(ComponentName(this, ChatGptNotificationListener::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        connected = false
        mainHandler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        mainHandler.removeCallbacks(rebindRunnable)
        mainHandler.removeCallbacks(watchdogRunnable)
        scanActive(
            event = "LISTENER_CONNECTED",
            inspectPet = true,
            acceptPet = true,
            recordDiagnostics = true,
            announceConnection = true,
        )
        mainHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
    }

    override fun onListenerDisconnected() {
        connected = false
        mainHandler.removeCallbacks(reconcileRunnable)
        mainHandler.removeCallbacks(watchdogRunnable)
        AppGraph.diagnostics.listenerDisconnected()
        mainHandler.removeCallbacks(rebindRunnable)
        mainHandler.postDelayed(rebindRunnable, REBIND_DELAY_MS)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (!isConfiguredSource(sbn)) return
        val sequence = markEvent(sbn.key)
        processPosted(sbn, sequence)
        scheduleReconcile(POSTED_RECONCILE_DELAY_MS)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        if (!isConfiguredSource(sbn)) return
        markEvent(sbn.key)
        // Removed StatusBarNotification is intentionally not parsed: Android documents it as a
        // lightweight object. Reconcile against activeNotifications after the group settles.
        scheduleReconcile(REMOVED_RECONCILE_DELAY_MS)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        if (connected) scheduleReconcile(RANKING_RECONCILE_DELAY_MS)
    }

    private fun markEvent(key: String): Long {
        val sequence = eventSequence.incrementAndGet()
        latestEventByKey[key] = sequence
        return sequence
    }

    private fun scheduleReconcile(delayMs: Long) {
        mainHandler.removeCallbacks(reconcileRunnable)
        mainHandler.postDelayed(reconcileRunnable, delayMs)
    }

    private fun scanActive(
        event: String,
        inspectPet: Boolean,
        acceptPet: Boolean,
        recordDiagnostics: Boolean,
        announceConnection: Boolean,
    ) {
        val captured = runCatching { activeNotifications?.toList() ?: emptyList() }
            .onFailure { AppGraph.diagnostics.error("getActiveNotifications: ${it.javaClass.simpleName}") }
            .getOrNull()
            ?: return
        val sourcePackage = AppGraph.settings.settings.value.sourcePackage
        val matching = captured.filter { it.packageName == sourcePackage }
        val snapshotSequence = eventSequence.get()

        AppGraph.applicationScope.launch {
            val parsed = matching.mapNotNull { sbn ->
                parse(
                    sbn = sbn,
                    event = event,
                    inspectPet = inspectPet,
                    acceptPet = acceptPet,
                    recordDiagnostics = recordDiagnostics,
                )?.task
            }
            // A new callback arrived while the snapshot was being parsed. Never let an older full
            // snapshot overwrite newer live state; just schedule another settle pass.
            if (snapshotSequence != eventSequence.get()) {
                mainHandler.post { scheduleReconcile(POSTED_RECONCILE_DELAY_MS) }
                return@launch
            }
            AppGraph.tasks.replaceAll(NotificationSetReducer.reduce(parsed))
            AppGraph.diagnostics.activeCount(matching.size)
            if (announceConnection) {
                AppGraph.diagnostics.listenerConnected(sourcePackage, matching.size)
            }
        }
    }

    private fun processPosted(sbn: StatusBarNotification, sequence: Long) {
        AppGraph.applicationScope.launch {
            val parsed = parse(
                sbn = sbn,
                event = "POSTED",
                inspectPet = true,
                acceptPet = true,
                recordDiagnostics = true,
            ) ?: return@launch
            if (latestEventByKey[sbn.key] != sequence) return@launch

            // Real child notifications update the UI immediately. Group summaries wait for the
            // full snapshot so they cannot flash as a duplicate next to their children.
            if (!parsed.task.isGroupSummary) {
                AppGraph.tasks.upsert(
                    parsed.task.copy(updatedAt = Instant.now()),
                    liveNotification = true,
                )
            }
            updateActiveCountOnly()
        }
    }

    private suspend fun parse(
        sbn: StatusBarNotification,
        event: String,
        inspectPet: Boolean,
        acceptPet: Boolean,
        recordDiagnostics: Boolean,
    ): ParsedNotification? = withContext(Dispatchers.Default) {
        runCatching {
            val inspection = if (inspectPet) {
                AppGraph.petAssets.inspect(sbn.notification, sbn.packageName)
            } else {
                PetInspection(emptyList(), null, emptyList())
            }
            val parsed = AppGraph.notificationParser.parse(
                sbn = sbn,
                event = event,
                petInspection = inspection,
                includeDebugText = BuildConfig.DEBUG && recordDiagnostics,
            )
            if (recordDiagnostics) AppGraph.diagnostics.record(parsed.snapshot)
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

    private fun updateActiveCountOnly() {
        val sourcePackage = AppGraph.settings.settings.value.sourcePackage
        val count = runCatching {
            activeNotifications.orEmpty().count { it.packageName == sourcePackage }
        }.getOrDefault(AppGraph.diagnostics.listener.value.activeNotificationCount)
        AppGraph.diagnostics.activeCount(count)
    }

    companion object {
        private const val POSTED_RECONCILE_DELAY_MS = 220L
        private const val REMOVED_RECONCILE_DELAY_MS = 260L
        private const val RANKING_RECONCILE_DELAY_MS = 350L
        private const val WATCHDOG_INTERVAL_MS = 10_000L
        private const val REBIND_DELAY_MS = 1_500L

        @Volatile
        private var instance: ChatGptNotificationListener? = null

        fun refresh(context: Context) {
            val listener = instance
            if (listener != null && listener.connected) {
                listener.scanActive(
                    event = "MANUAL_REFRESH",
                    inspectPet = true,
                    acceptPet = true,
                    recordDiagnostics = true,
                    announceConnection = true,
                )
            } else {
                requestRebind(ComponentName(context, ChatGptNotificationListener::class.java))
            }
        }

        fun restart(context: Context) {
            val component = ComponentName(context, ChatGptNotificationListener::class.java)
            instance?.let { listener ->
                listener.connected = false
                runCatching { listener.requestUnbind() }
            }
            Handler(Looper.getMainLooper()).postDelayed({ requestRebind(component) }, REBIND_DELAY_MS)
        }
    }
}
