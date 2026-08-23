package com.fourerk.codexpet.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TaskRepository(scope: CoroutineScope) {
    private val byNotification = MutableStateFlow<Map<String, CodexTask>>(emptyMap())
    private val mutableTransitions = MutableSharedFlow<TaskTransition>(extraBufferCapacity = 32)

    val transitions = mutableTransitions.asSharedFlow()
    val tasks = byNotification
        .map { values ->
            values.values
                .groupBy(CodexTask::id)
                .mapNotNull { (_, duplicates) -> duplicates.maxByOrNull { it.updatedAt } }
                .sortedWith(compareByDescending<CodexTask> { it.status == TaskStatus.RUNNING }
                    .thenByDescending { it.updatedAt })
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    @Synchronized
    fun upsert(task: CodexTask, liveNotification: Boolean = false) {
        val previous = byNotification.value[task.sourceNotificationKey]
        if (previous != null && previous.hasSameVisibleContent(task)) return
        byNotification.value = byNotification.value + (task.sourceNotificationKey to task)
        val liveChatEvent = liveNotification && task.kind == TaskKind.CHAT_MESSAGE
        if ((previous != null &&
                (previous.status != task.status || previous.animationCue != task.animationCue)) ||
            liveChatEvent
        ) {
            mutableTransitions.tryEmit(
                TaskTransition(
                    taskId = task.id,
                    from = previous?.status,
                    to = task.status,
                    fromCue = previous?.animationCue ?: TaskAnimationCue.UNKNOWN,
                    toCue = task.animationCue,
                    kind = task.kind,
                    liveNotification = liveChatEvent,
                ),
            )
        }
    }

    @Synchronized
    fun remove(notificationKey: String) {
        byNotification.value = byNotification.value - notificationKey
    }

    @Synchronized
    fun replaceAll(newTasks: List<CodexTask>) {
        val old = byNotification.value
        val replacement = newTasks
            .associateBy(CodexTask::sourceNotificationKey)
            .mapValues { (key, task) ->
                old[key]?.takeIf { it.hasSameVisibleContent(task) } ?: task
            }
        if (old == replacement) return
        byNotification.value = replacement
        replacement.forEach { (key, task) ->
            val prior = old[key]
            if (prior != null &&
                (prior.status != task.status || prior.animationCue != task.animationCue)
            ) {
                mutableTransitions.tryEmit(
                    TaskTransition(
                        taskId = task.id,
                        from = prior.status,
                        to = task.status,
                        fromCue = prior.animationCue,
                        toCue = task.animationCue,
                        kind = task.kind,
                    ),
                )
            }
        }
    }
}
