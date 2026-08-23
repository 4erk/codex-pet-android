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
    fun upsert(task: CodexTask) {
        val previous = byNotification.value[task.sourceNotificationKey]
        byNotification.value = byNotification.value + (task.sourceNotificationKey to task)
        if (previous != null && previous.status != task.status) {
            mutableTransitions.tryEmit(TaskTransition(task.id, previous.status, task.status))
        }
    }

    @Synchronized
    fun remove(notificationKey: String) {
        byNotification.value = byNotification.value - notificationKey
    }

    @Synchronized
    fun replaceAll(newTasks: List<CodexTask>) {
        val old = byNotification.value
        val replacement = newTasks.associateBy(CodexTask::sourceNotificationKey)
        byNotification.value = replacement
        replacement.forEach { (key, task) ->
            val prior = old[key]
            if (prior != null && prior.status != task.status) {
                mutableTransitions.tryEmit(TaskTransition(task.id, prior.status, task.status))
            }
        }
    }
}
