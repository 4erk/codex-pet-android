package com.fourerk.codexpet.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiagnosticsRepository {
    private val mutableListener = MutableStateFlow(ListenerDiagnostics())
    private val mutableSnapshots = MutableStateFlow<List<NotificationSnapshot>>(emptyList())

    val listener = mutableListener.asStateFlow()
    val snapshots = mutableSnapshots.asStateFlow()

    fun listenerConnected(sourcePackage: String, activeCount: Int) {
        val now = System.currentTimeMillis()
        mutableListener.value = mutableListener.value.copy(
            connected = true,
            sourcePackage = sourcePackage,
            activeNotificationCount = activeCount,
            lastConnectedAt = now,
            lastEventAt = now,
            lastHeartbeatAt = now,
            consecutiveScanFailures = 0,
            rebindAttempts = 0,
            lastError = null,
        )
    }

    fun listenerDisconnected() {
        mutableListener.value = mutableListener.value.copy(
            connected = false,
            lastEventAt = System.currentTimeMillis(),
        )
    }

    fun listenerHeartbeat(activeCount: Int) {
        val now = System.currentTimeMillis()
        mutableListener.value = mutableListener.value.copy(
            connected = true,
            activeNotificationCount = activeCount,
            lastHeartbeatAt = now,
            lastEventAt = now,
            consecutiveScanFailures = 0,
        )
    }

    fun listenerScanFailed(message: String): Int {
        val current = mutableListener.value
        val failures = current.consecutiveScanFailures + 1
        mutableListener.value = current.copy(
            consecutiveScanFailures = failures,
            lastError = message.take(240),
            lastEventAt = System.currentTimeMillis(),
        )
        return failures
    }

    fun listenerRebindAttempt(attempt: Int) {
        mutableListener.value = mutableListener.value.copy(
            connected = false,
            rebindAttempts = attempt,
            lastEventAt = System.currentTimeMillis(),
        )
    }

    fun activeCount(count: Int) {
        mutableListener.value = mutableListener.value.copy(
            activeNotificationCount = count,
            lastEventAt = System.currentTimeMillis(),
        )
    }

    fun error(message: String) {
        mutableListener.value = mutableListener.value.copy(
            lastError = message.take(240),
            lastEventAt = System.currentTimeMillis(),
        )
    }

    @Synchronized
    fun record(snapshot: NotificationSnapshot) {
        mutableSnapshots.value = (listOf(snapshot) + mutableSnapshots.value).take(MAX_SNAPSHOTS)
        mutableListener.value = mutableListener.value.copy(lastEventAt = System.currentTimeMillis())
    }

    fun clear() {
        mutableSnapshots.value = emptyList()
    }

    private companion object {
        const val MAX_SNAPSHOTS = 100
    }
}
