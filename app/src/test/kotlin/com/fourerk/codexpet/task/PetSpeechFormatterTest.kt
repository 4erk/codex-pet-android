package com.fourerk.codexpet.task

import com.fourerk.codexpet.settings.SpeechStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PetSpeechFormatterTest {
    @Test
    fun `exact mode preserves notification text`() {
        val item = item(TaskAnimationCue.FAILED, "Build failed: exit code 1")

        val result = PetSpeechFormatter.format(item, SpeechStyle.EXACT)

        assertEquals(item.text, result.text)
    }

    @Test
    fun `friendly mode keeps exact useful payload after human lead`() {
        val item = item(TaskAnimationCue.DISCONNECTED, "Remote computer offline")

        val result = PetSpeechFormatter.format(item, SpeechStyle.FRIENDLY)

        assertTrue(result.text.contains("Remote computer offline"))
        assertTrue(result.text.contains(":"))
    }

    @Test
    fun `chat messages are never paraphrased`() {
        val item = item(
            cue = TaskAnimationCue.MESSAGE_RECEIVED,
            text = "Вот точный ответ из чата",
            kind = TaskKind.CHAT_MESSAGE,
        )

        val result = PetSpeechFormatter.format(item, SpeechStyle.FRIENDLY)

        assertEquals("Вот точный ответ из чата", result.text)
    }

    @Test
    fun `generic completed status becomes compact friendly phrase`() {
        val item = item(TaskAnimationCue.COMPLETED, "Done")

        val result = PetSpeechFormatter.format(item, SpeechStyle.FRIENDLY)

        assertTrue(result.text in setOf("Готово", "Есть, сделал", "Закончил"))
    }

    private fun item(
        cue: TaskAnimationCue,
        text: String,
        kind: TaskKind = TaskKind.TASK,
    ): PetSpeechItem {
        val task = CodexTask(
            id = "task-1",
            title = "Build",
            summary = text,
            status = when (cue) {
                TaskAnimationCue.COMPLETED -> TaskStatus.COMPLETED
                TaskAnimationCue.FAILED, TaskAnimationCue.DISCONNECTED -> TaskStatus.ERROR
                else -> TaskStatus.RUNNING
            },
            updatedAt = Instant.EPOCH,
            progress = null,
            contentIntent = null,
            bubbleIntent = null,
            sourceNotificationKey = "key",
            groupKey = null,
            kind = kind,
            animationCue = cue,
        )
        return PetSpeechItem(
            task = task,
            title = task.title,
            text = text,
            priority = PetSpeechPriority.RUNNING,
            expiresAt = null,
        )
    }
}
