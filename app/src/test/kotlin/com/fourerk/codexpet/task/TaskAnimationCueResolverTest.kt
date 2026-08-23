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
    fun `failures cannot be erased by a generic work verb`() {
        assertCue(TaskAnimationCue.FAILED, "Build failed while building the release", TaskStatus.RUNNING)
        assertCue(TaskAnimationCue.FAILED, "Не удалось запустить тесты, собирает диагностические данные", TaskStatus.RUNNING)
    }

    @Test
    fun `explicit resume can recover a prior failure`() {
        assertCue(TaskAnimationCue.ACTIVE, "Build failed. Retrying and continuing", TaskStatus.RUNNING)
        assertCue(TaskAnimationCue.ACTIVE, "Сбой. Возобновляет работу", TaskStatus.RUNNING)
    }

    @Test
    fun `active work text maps to active`() {
        assertCue(TaskAnimationCue.ACTIVE, "Implementing the requested change")
        assertCue(TaskAnimationCue.ACTIVE, "Исправляет код и собирает проект")
    }

    @Test
    fun `later specific workflow stage can refine running`() {
        assertCue(TaskAnimationCue.REVIEWING, "Implemented the fix; now running tests", TaskStatus.RUNNING)
        assertCue(TaskAnimationCue.COMPLETED, "Проверяет сборку. Готово, тесты прошли", TaskStatus.RUNNING)
    }

    @Test
    fun `structured error and completion remain authoritative against weak text`() {
        assertCue(TaskAnimationCue.FAILED, "Reviewing logs", TaskStatus.ERROR)
        assertCue(TaskAnimationCue.COMPLETED, "Preparing the next optional step", TaskStatus.COMPLETED)
    }

    @Test
    fun `strong connectivity and attention signals refine structured states`() {
        assertCue(TaskAnimationCue.WAITING_FOR_INPUT, "User action required", TaskStatus.RUNNING)
        assertCue(TaskAnimationCue.DISCONNECTED, "Remote computer is offline", TaskStatus.ERROR)
        assertCue(TaskAnimationCue.RECONNECTING, "Connection lost. Trying to reconnect", TaskStatus.RUNNING)
    }

    @Test
    fun `broader situational phrases are bilingual`() {
        assertCue(TaskAnimationCue.WAITING_FOR_INPUT, "Необходимо ваше действие")
        assertCue(TaskAnimationCue.FAILED, "Permission denied")
        assertCue(TaskAnimationCue.FAILED, "Нет доступа")
        assertCue(TaskAnimationCue.REVIEWING, "Reading logs and comparing results")
        assertCue(TaskAnimationCue.ACTIVE, "Обновляет конфигурацию и продолжает работу")
    }

    @Test
    fun `remote connectivity states are bilingual`() {
        assertCue(TaskAnimationCue.DISCONNECTED, "Disconnected from the remote computer")
        assertCue(TaskAnimationCue.DISCONNECTED, "Нет связи с удалённым компьютером")
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
