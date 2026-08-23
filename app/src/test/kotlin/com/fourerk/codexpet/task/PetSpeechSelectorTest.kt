package com.fourerk.codexpet.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PetSpeechSelectorTest {
    private val now = Instant.parse("2026-08-23T12:00:00Z")
    private val allEnabled = PetSpeechPolicy(
        automaticEnabled = true,
        ongoingEnabled = true,
        attentionEnabled = true,
        chatMessagesEnabled = true,
        completionsEnabled = true,
        completedVisibleSeconds = 10,
    )

    @Test
    fun `needs input wins over blocked ready and running`() {
        val items = PetSpeechSelector.select(
            listOf(
                task("running", TaskStatus.RUNNING, TaskAnimationCue.ACTIVE, "Пишет код"),
                task("ready", TaskStatus.COMPLETED, TaskAnimationCue.COMPLETED, "Готово"),
                task("blocked", TaskStatus.ERROR, TaskAnimationCue.FAILED, "Сборка упала"),
                task("input", TaskStatus.RUNNING, TaskAnimationCue.WAITING_FOR_INPUT, "Нужно подтверждение"),
            ),
            allEnabled,
            now,
        )

        assertEquals(listOf("input", "blocked", "ready", "running"), items.map { it.task.id })
    }

    @Test
    fun `ongoing switch hides running but keeps attention`() {
        val policy = allEnabled.copy(ongoingEnabled = false)
        val items = PetSpeechSelector.select(
            listOf(
                task("running", TaskStatus.RUNNING, TaskAnimationCue.ACTIVE, "Собирает APK"),
                task("input", TaskStatus.RUNNING, TaskAnimationCue.WAITING_FOR_INPUT, "Выберите вариант"),
            ),
            policy,
            now,
        )

        assertEquals(listOf("input"), items.map { it.task.id })
    }

    @Test
    fun `master switch suppresses every automatic speech category`() {
        val items = PetSpeechSelector.select(
            listOf(
                task("message", TaskStatus.UNKNOWN, TaskAnimationCue.MESSAGE_RECEIVED, "Ответ в чате", kind = TaskKind.CHAT_MESSAGE),
                task("input", TaskStatus.RUNNING, TaskAnimationCue.WAITING_FOR_INPUT, "Нужен ответ"),
                task("running", TaskStatus.RUNNING, TaskAnimationCue.ACTIVE, "Собирает"),
            ),
            allEnabled.copy(automaticEnabled = false),
            now,
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `expired completion and chat message are removed`() {
        val old = now.minusSeconds(30)
        val items = PetSpeechSelector.select(
            listOf(
                task("ready", TaskStatus.COMPLETED, TaskAnimationCue.COMPLETED, "Готово", old),
                task(
                    id = "message",
                    status = TaskStatus.UNKNOWN,
                    cue = TaskAnimationCue.MESSAGE_RECEIVED,
                    summary = "Новое сообщение",
                    updatedAt = old,
                    kind = TaskKind.CHAT_MESSAGE,
                ),
            ),
            allEnabled,
            now,
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `attention remains until notification changes while transient items expire`() {
        val items = PetSpeechSelector.select(
            listOf(
                task("input", TaskStatus.RUNNING, TaskAnimationCue.WAITING_FOR_INPUT, "Нужен ответ"),
                task("blocked", TaskStatus.ERROR, TaskAnimationCue.DISCONNECTED, "Remote computer offline"),
                task("ready", TaskStatus.COMPLETED, TaskAnimationCue.COMPLETED, "Готово"),
                task(
                    id = "message",
                    status = TaskStatus.UNKNOWN,
                    cue = TaskAnimationCue.MESSAGE_RECEIVED,
                    summary = "Новое сообщение",
                    kind = TaskKind.CHAT_MESSAGE,
                ),
            ),
            allEnabled,
            now,
        ).associateBy { it.task.id }

        assertEquals(null, items.getValue("input").expiresAt)
        assertEquals(null, items.getValue("blocked").expiresAt)
        assertTrue(items.getValue("ready").expiresAt != null)
        assertTrue(items.getValue("message").expiresAt != null)
    }

    @Test
    fun `summary is preferred and generic title is omitted`() {
        val item = PetSpeechSelector.select(
            listOf(task("one", TaskStatus.RUNNING, TaskAnimationCue.ACTIVE, "  Проверяет   тесты\nи lint  ")),
            allEnabled,
            now,
        ).single()

        assertEquals("Проверяет тесты и lint", item.text)
        assertEquals(null, item.title)
    }

    private fun task(
        id: String,
        status: TaskStatus,
        cue: TaskAnimationCue,
        summary: String,
        updatedAt: Instant = now.minusSeconds(1),
        kind: TaskKind = TaskKind.TASK,
    ) = CodexTask(
        id = id,
        title = "Codex task",
        summary = summary,
        status = status,
        updatedAt = updatedAt,
        progress = null,
        contentIntent = null,
        bubbleIntent = null,
        sourceNotificationKey = "key:$id",
        groupKey = null,
        kind = kind,
        animationCue = cue,
    )
}
