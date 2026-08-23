package com.fourerk.codexpet.task

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskStatusResolverTest {
    @Test
    fun `indeterminate progress is running`() {
        assertEquals(
            TaskStatus.RUNNING,
            resolve(progressIndeterminate = true, fallbackText = "done"),
        )
    }

    @Test
    fun `partial structured progress wins over completion text`() {
        assertEquals(
            TaskStatus.RUNNING,
            resolve(progress = 4, progressMax = 10, fallbackText = "completed"),
        )
    }

    @Test
    fun `full structured progress is completed`() {
        assertEquals(TaskStatus.COMPLETED, resolve(progress = 10, progressMax = 10))
    }

    @Test
    fun `ongoing flag wins over error text`() {
        assertEquals(TaskStatus.RUNNING, resolve(ongoing = true, fallbackText = "failed"))
    }

    @Test
    fun `explicit error fallback is error`() {
        assertEquals(TaskStatus.ERROR, resolve(fallbackText = "Task failed"))
        assertEquals(TaskStatus.ERROR, resolve(fallbackText = "Не удалось завершить задачу"))
    }

    @Test
    fun `explicit completion fallback is completed`() {
        assertEquals(TaskStatus.COMPLETED, resolve(fallbackText = "Task is ready"))
        assertEquals(TaskStatus.COMPLETED, resolve(fallbackText = "Задача завершена"))
    }

    @Test
    fun `unstructured text stays unknown`() {
        assertEquals(TaskStatus.UNKNOWN, resolve(fallbackText = "Updated repository state"))
    }

    private fun resolve(
        progress: Int? = null,
        progressMax: Int? = null,
        progressIndeterminate: Boolean = false,
        ongoing: Boolean = false,
        fallbackText: String? = null,
    ): TaskStatus = TaskStatusResolver.resolve(
        TaskSignals(
            progress = progress,
            progressMax = progressMax,
            progressIndeterminate = progressIndeterminate,
            ongoing = ongoing,
            fallbackText = fallbackText,
        ),
    )
}
