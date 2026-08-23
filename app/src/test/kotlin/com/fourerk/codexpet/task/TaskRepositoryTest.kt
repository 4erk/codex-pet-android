package com.fourerk.codexpet.task

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRepositoryTest {
    @Test
    fun `deduplicates a stable task id and keeps newest notification`() = runTest {
        val repository = TaskRepository(backgroundScope)
        runCurrent()
        repository.upsert(task("task-1", "notification-a", 1, TaskStatus.RUNNING))
        repository.upsert(task("task-1", "notification-b", 2, TaskStatus.COMPLETED))

        runCurrent()

        assertEquals(1, repository.tasks.value.size)
        assertEquals("notification-b", repository.tasks.value.single().sourceNotificationKey)
        assertEquals(TaskStatus.COMPLETED, repository.tasks.value.single().status)
    }

    @Test
    fun `initial observation does not fake a completion transition`() = runTest {
        val repository = TaskRepository(backgroundScope)
        val transitions = mutableListOf<TaskTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.transitions.collect(transitions::add)
        }

        repository.upsert(task("task-1", "notification-a", 1, TaskStatus.COMPLETED))
        advanceUntilIdle()

        assertTrue(transitions.isEmpty())
    }

    @Test
    fun `running to completed emits one real transition`() = runTest {
        val repository = TaskRepository(backgroundScope)
        val transitions = mutableListOf<TaskTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.transitions.collect(transitions::add)
        }

        repository.upsert(task("task-1", "notification-a", 1, TaskStatus.RUNNING))
        repository.upsert(task("task-1", "notification-a", 2, TaskStatus.COMPLETED))
        advanceUntilIdle()

        assertEquals(
            listOf(TaskTransition("task-1", TaskStatus.RUNNING, TaskStatus.COMPLETED)),
            transitions,
        )
    }

    private fun task(
        id: String,
        notificationKey: String,
        epochSecond: Long,
        status: TaskStatus,
    ) = CodexTask(
        id = id,
        title = id,
        summary = null,
        status = status,
        updatedAt = Instant.ofEpochSecond(epochSecond),
        progress = null,
        contentIntent = null,
        bubbleIntent = null,
        sourceNotificationKey = notificationKey,
        groupKey = null,
    )
}
