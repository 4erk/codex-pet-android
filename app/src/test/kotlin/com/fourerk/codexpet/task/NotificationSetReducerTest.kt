package com.fourerk.codexpet.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NotificationSetReducerTest {
    @Test
    fun `group summary is suppressed when real child exists`() {
        val summary = task("summary", "group:a", summary = true, seconds = 1)
        val child = task("child", "group:a", summary = false, seconds = 2)

        val result = NotificationSetReducer.reduce(listOf(summary, child))

        assertEquals(listOf("child"), result.map { it.id })
        assertFalse(result.single().isGroupSummary)
    }

    @Test
    fun `group summary survives when it is the only visible member`() {
        val summary = task("summary", "group:a", summary = true, seconds = 1)

        val result = NotificationSetReducer.reduce(listOf(summary))

        assertEquals(1, result.size)
        assertTrue(result.single().isGroupSummary)
    }

    @Test
    fun `newest real logical duplicate wins`() {
        val old = task("same", null, summary = false, seconds = 1, key = "old")
        val fresh = task("same", null, summary = false, seconds = 3, key = "fresh")

        val result = NotificationSetReducer.reduce(listOf(old, fresh))

        assertEquals("fresh", result.single().sourceNotificationKey)
    }

    private fun task(
        id: String,
        group: String?,
        summary: Boolean,
        seconds: Long,
        key: String = id,
    ) = CodexTask(
        id = id,
        title = id,
        summary = "text",
        status = TaskStatus.RUNNING,
        updatedAt = Instant.EPOCH.plusSeconds(seconds),
        progress = null,
        contentIntent = null,
        bubbleIntent = null,
        sourceNotificationKey = key,
        groupKey = group,
        kind = TaskKind.TASK,
        animationCue = TaskAnimationCue.ACTIVE,
        isGroupSummary = summary,
    )
}
