package com.fourerk.codexpet.task

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskAnimationCueResolverTest {
    @Test
    fun `english and russian approval requests map to waiting`() {
        assertCue(TaskAnimationCue.WAITING_FOR_INPUT, "Needs your approval to continue")
        assertCue(TaskAnimationCue.WAITING_FOR_INPUT, "Требуется ваше подтверждение")
    }

    @Test
    fun `english and russian inspection actions map to review`() {
        assertCue(TaskAnimationCue.REVIEWING, "Validating installer flow")
        assertCue(TaskAnimationCue.REVIEWING, "Проверяет тесты и анализирует результат")
    }

    @Test
    fun `english and russian failures map to failed even if notification is ongoing`() {
        assertCue(TaskAnimationCue.FAILED, "Build failed", TaskStatus.RUNNING)
        assertCue(TaskAnimationCue.FAILED, "Не удалось запустить тесты", TaskStatus.RUNNING)
    }

    @Test
    fun `active work text maps to active`() {
        assertCue(TaskAnimationCue.ACTIVE, "Implementing the requested change")
        assertCue(TaskAnimationCue.ACTIVE, "Исправляет код и собирает проект")
    }

    @Test
    fun `latest explicit action wins inside a multi-step status`() {
        assertCue(TaskAnimationCue.REVIEWING, "Implemented the fix; now running tests")
        assertCue(TaskAnimationCue.COMPLETED, "Проверяет сборку. Готово, тесты прошли")
    }

    @Test
    fun `specific action heuristic refines a coarse structured state`() {
        assertCue(TaskAnimationCue.REVIEWING, "Will review logs now", TaskStatus.ERROR)
        assertCue(TaskAnimationCue.ACTIVE, "Preparing the next optional step", TaskStatus.COMPLETED)
    }

    @Test
    fun `broader situational phrases are bilingual`() {
        assertCue(TaskAnimationCue.WAITING_FOR_INPUT, "User action required")
        assertCue(TaskAnimationCue.WAITING_FOR_INPUT, "Необходимо ваше действие")
        assertCue(TaskAnimationCue.FAILED, "Permission denied")
        assertCue(TaskAnimationCue.FAILED, "Нет доступа")
        assertCue(TaskAnimationCue.REVIEWING, "Reading logs and comparing results")
        assertCue(TaskAnimationCue.ACTIVE, "Обновляет конфигурацию и продолжает работу")
    }

    @Test
    fun `remote connectivity states are bilingual and use the latest state`() {
        assertCue(TaskAnimationCue.DISCONNECTED, "Disconnected from the remote computer")
        assertCue(TaskAnimationCue.DISCONNECTED, "Нет связи с удалённым компьютером")
        assertCue(TaskAnimationCue.RECONNECTING, "Connection lost. Trying to reconnect")
        assertCue(TaskAnimationCue.RECONNECTING, "Связь потеряна, переподключается")
    }

    private fun assertCue(
        expected: TaskAnimationCue,
        text: String,
        status: TaskStatus = TaskStatus.UNKNOWN,
    ) {
        assertEquals(expected, TaskAnimationCueResolver.resolve(status, text).cue)
    }
}
