package com.fourerk.codexpet.task

import android.app.PendingIntent
import java.time.Instant

enum class TaskStatus {
    RUNNING,
    COMPLETED,
    ERROR,
    UNKNOWN,
}

data class TaskProgress(
    val value: Int,
    val max: Int,
    val indeterminate: Boolean,
)

data class CodexTask(
    val id: String,
    val title: String,
    val summary: String?,
    val status: TaskStatus,
    val updatedAt: Instant,
    val progress: TaskProgress?,
    val contentIntent: PendingIntent?,
    val bubbleIntent: PendingIntent?,
    val sourceNotificationKey: String,
    val groupKey: String?,
)

data class TaskTransition(
    val taskId: String,
    val from: TaskStatus?,
    val to: TaskStatus,
)
