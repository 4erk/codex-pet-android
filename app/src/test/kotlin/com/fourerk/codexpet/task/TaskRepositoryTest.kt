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

    @Test
    fun `explicit completion text cue emits a transition even while notification stays ongoing`() = runTest {
        val repository = TaskRepository(backgroundScope)
        val transitions = mutableListOf<TaskTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.transitions.collect(transitions::add)
        }

        repository.upsert(task("task-1", "notification-a", 1, TaskStatus.RUNNING))
        repository.upsert(
            task("task-1", "notification-a", 2, TaskStatus.RUNNING).copy(
                animationCue = TaskAnimationCue.COMPLETED,
                animationCueSource = CueSignalSource.TEXT_HEURISTIC,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, transitions.size)
        assertEquals(TaskAnimationCue.COMPLETED, transitions.single().toCue)
    }

    @Test
    fun `timestamp-only avatar refresh does not replace visible task`() = runTest {
        val repository = TaskRepository(backgroundScope)
        repository.upsert(task("task-1", "notification-a", 1, TaskStatus.RUNNING))
        runCurrent()

        repository.upsert(task("task-1", "notification-a", 99, TaskStatus.RUNNING))
        runCurrent()

        assertEquals(Instant.ofEpochSecond(1), repository.tasks.value.single().updatedAt)
    }

    @Test
    fun `live regular ChatGPT message emits a transient interaction event`() = runTest {
        val repository = TaskRepository(backgroundScope)
        val transitions = mutableListOf<TaskTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.transitions.collect(transitions::add)
        }

        repository.upsert(
            task("chat-1", "notification-chat", 1, TaskStatus.UNKNOWN).copy(
                kind = TaskKind.CHAT_MESSAGE,
                animationCue = TaskAnimationCue.MESSAGE_RECEIVED,
            ),
            liveNotification = true,
        )
        advanceUntilIdle()

        assertEquals(1, transitions.size)
        assertTrue(transitions.single().liveNotification)
        assertEquals(TaskKind.CHAT_MESSAGE, transitions.single().kind)
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
