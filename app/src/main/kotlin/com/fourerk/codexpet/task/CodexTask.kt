package com.fourerk.codexpet.task

import android.app.PendingIntent
import java.time.Instant

enum class TaskStatus {
    RUNNING,
    COMPLETED,
    ERROR,
    UNKNOWN,
}

enum class TaskKind {
    TASK,
    BUBBLE_CONTROLLER,
    CHAT_MESSAGE,
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
    val kind: TaskKind = TaskKind.TASK,
    val detail: String? = null,
    val animationCue: TaskAnimationCue = TaskAnimationCue.UNKNOWN,
    val animationCueSource: CueSignalSource = CueSignalSource.NONE,
    val isGroupSummary: Boolean = false,
)

fun CodexTask.isDisplayTask(): Boolean = kind != TaskKind.BUBBLE_CONTROLLER

fun CodexTask.isCodexTask(): Boolean = kind == TaskKind.TASK

fun CodexTask.hasExactOpenTarget(): Boolean = contentIntent != null || bubbleIntent != null

internal fun CodexTask.hasSameVisibleContent(other: CodexTask): Boolean =
    id == other.id &&
        title == other.title &&
        summary == other.summary &&
        detail == other.detail &&
        status == other.status &&
        progress == other.progress &&
        kind == other.kind &&
        animationCue == other.animationCue &&
        animationCueSource == other.animationCueSource &&
        contentIntent == other.contentIntent &&
        bubbleIntent == other.bubbleIntent &&
        sourceNotificationKey == other.sourceNotificationKey &&
        groupKey == other.groupKey &&
        isGroupSummary == other.isGroupSummary

data class TaskTransition(
    val taskId: String,
    val from: TaskStatus?,
    val to: TaskStatus,
    val fromCue: TaskAnimationCue = TaskAnimationCue.UNKNOWN,
    val toCue: TaskAnimationCue = TaskAnimationCue.UNKNOWN,
    val kind: TaskKind = TaskKind.TASK,
    val liveNotification: Boolean = false,
)
