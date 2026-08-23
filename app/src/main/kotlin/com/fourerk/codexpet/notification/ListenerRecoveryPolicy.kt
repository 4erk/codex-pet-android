package com.fourerk.codexpet.notification

/** Pure retry policy used by NotificationListenerService so OEM disconnects cannot stall forever. */
object ListenerRecoveryPolicy {
    private val delaysMs = longArrayOf(1_500L, 3_000L, 7_500L, 15_000L, 30_000L, 60_000L)

    fun delayMillis(attempt: Int): Long = delaysMs[attempt.coerceIn(0, delaysMs.lastIndex)]

    fun shouldForceRebind(consecutiveScanFailures: Int): Boolean = consecutiveScanFailures >= 3
}
