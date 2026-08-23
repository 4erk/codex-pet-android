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
    private val scanSequence = AtomicLong(0L)
    private val latestEventByKey = ConcurrentHashMap<String, Long>()
    private var connected = false
    private var rebindAttempt = 0
    private var forcingReconnect = false

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

    private val rebindRunnable = object : Runnable {
        override fun run() {
            if (connected) return
            rebindAttempt += 1
            AppGraph.diagnostics.listenerRebindAttempt(rebindAttempt)
            runCatching {
                requestRebind(ComponentName(this@ChatGptNotificationListener, ChatGptNotificationListener::class.java))
            }.onFailure {
                AppGraph.diagnostics.error("listener requestRebind: ${it.javaClass.simpleName}")
            }
            if (!connected) {
                mainHandler.postDelayed(this, ListenerRecoveryPolicy.delayMillis(rebindAttempt))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Some OEM builds create the service but delay/lose the first connected callback. A
        // one-shot grace timer turns that silent half-bound state into the normal rebind loop.
        mainHandler.postDelayed(rebindRunnable, INITIAL_BIND_GRACE_MS)
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
        forcingReconnect = false
        rebindAttempt = 0
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
        scheduleRebind(resetAttempts = !forcingReconnect)
        forcingReconnect = false
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

    private fun scheduleRebind(resetAttempts: Boolean) {
        if (resetAttempts) rebindAttempt = 0
        mainHandler.removeCallbacks(rebindRunnable)
        mainHandler.postDelayed(rebindRunnable, ListenerRecoveryPolicy.delayMillis(rebindAttempt))
    }

    private fun forceReconnect(reason: String) {
        if (forcingReconnect) return
        forcingReconnect = true
        connected = false
        mainHandler.removeCallbacks(reconcileRunnable)
        mainHandler.removeCallbacks(watchdogRunnable)
        AppGraph.diagnostics.error("listener self-heal: $reason")
        runCatching { requestUnbind() }
        scheduleRebind(resetAttempts = true)
    }

    private fun scanActive(
        event: String,
        inspectPet: Boolean,
        acceptPet: Boolean,
        recordDiagnostics: Boolean,
        announceConnection: Boolean,
    ) {
        val captured = runCatching {
            requireNotNull(activeNotifications) { "activeNotifications returned null" }.toList()
        }.onFailure { error ->
            val failures = AppGraph.diagnostics.listenerScanFailed(
                "getActiveNotifications: ${error.javaClass.simpleName}",
            )
            if (ListenerRecoveryPolicy.shouldForceRebind(failures)) {
                forceReconnect("$failures consecutive activeNotifications failures")
            }
        }.getOrNull() ?: return

        val sourcePackage = AppGraph.settings.settings.value.sourcePackage
        val matching = captured.filter { it.packageName == sourcePackage }
        AppGraph.diagnostics.listenerHeartbeat(matching.size)
        val snapshotEventSequence = eventSequence.get()
        val thisScan = scanSequence.incrementAndGet()

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
            if (thisScan != scanSequence.get()) return@launch
            if (snapshotEventSequence != eventSequence.get()) {
                mainHandler.post { scheduleReconcile(POSTED_RECONCILE_DELAY_MS) }
                return@launch
            }
            val reduced = NotificationSetReducer.reduce(parsed)
            AppGraph.tasks.replaceAll(reduced)
            val activeKeys = matching.mapTo(mutableSetOf()) { it.key }
            latestEventByKey.keys.removeIf { it !in activeKeys }
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
            requireNotNull(activeNotifications).count { it.packageName == sourcePackage }
        }.getOrElse { error ->
            val failures = AppGraph.diagnostics.listenerScanFailed(
                "active count: ${error.javaClass.simpleName}",
            )
            if (ListenerRecoveryPolicy.shouldForceRebind(failures)) {
                mainHandler.post { forceReconnect("active count failed $failures times") }
            }
            AppGraph.diagnostics.listener.value.activeNotificationCount
        }
        AppGraph.diagnostics.activeCount(count)
    }

    companion object {
        private const val POSTED_RECONCILE_DELAY_MS = 220L
        private const val REMOVED_RECONCILE_DELAY_MS = 300L
        private const val RANKING_RECONCILE_DELAY_MS = 400L
        private const val WATCHDOG_INTERVAL_MS = 12_000L
        private const val INITIAL_BIND_GRACE_MS = 5_000L

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

        fun ensureHealthy(context: Context) {
            val listener = instance
            if (listener != null && listener.connected) {
                listener.scanActive(
                    event = "HEALTH_CHECK",
                    inspectPet = false,
                    acceptPet = false,
                    recordDiagnostics = false,
                    announceConnection = false,
                )
            } else {
                requestRebind(ComponentName(context, ChatGptNotificationListener::class.java))
            }
        }

        fun restart(context: Context) {
            val component = ComponentName(context, ChatGptNotificationListener::class.java)
            val listener = instance
            if (listener != null) {
                listener.forceReconnect("manual restart")
            } else {
                requestRebind(component)
            }
        }
    }
}
