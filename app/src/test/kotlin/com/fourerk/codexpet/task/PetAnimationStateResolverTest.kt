package com.fourerk.codexpet.task

import com.fourerk.codexpet.pet.PetAnimationState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PetAnimationStateResolverTest {
    @Test
    fun `completed notification does not pin review forever`() {
        assertEquals(PetAnimationState.IDLE, PetAnimationStateResolver.resolve(listOf(task("done", TaskStatus.COMPLETED, TaskAnimationCue.COMPLETED))))
    }

    @Test
    fun `running task beats stale completed notification`() {
        assertEquals(
            PetAnimationState.RUNNING,
            PetAnimationStateResolver.resolve(
                listOf(
                    task("done", TaskStatus.COMPLETED, TaskAnimationCue.COMPLETED),
                    task("run", TaskStatus.RUNNING, TaskAnimationCue.ACTIVE),
                ),
            ),
        )
    }

    @Test
    fun `actionable input remains highest priority`() {
        assertEquals(
            PetAnimationState.WAITING,
            PetAnimationStateResolver.resolve(
                listOf(
                    task("failed", TaskStatus.ERROR, TaskAnimationCue.FAILED),
                    task("input", TaskStatus.RUNNING, TaskAnimationCue.WAITING_FOR_INPUT),
                ),
            ),
        )
    }

    @Test
    fun `review beats ordinary work but not errors`() {
        assertEquals(
            PetAnimationState.REVIEW,
            PetAnimationStateResolver.resolve(
                listOf(
                    task("run", TaskStatus.RUNNING, TaskAnimationCue.ACTIVE),
                    task("review", TaskStatus.RUNNING, TaskAnimationCue.REVIEWING),
                ),
            ),
        )
        assertEquals(
            PetAnimationState.FAILED,
            PetAnimationStateResolver.resolve(
                listOf(
                    task("review", TaskStatus.RUNNING, TaskAnimationCue.REVIEWING),
                    task("failed", TaskStatus.ERROR, TaskAnimationCue.FAILED),
                ),
            ),
        )
    }

    private fun task(id: String, status: TaskStatus, cue: TaskAnimationCue) = CodexTask(
        id = id,
        title = id,
        summary = id,
        status = status,
        updatedAt = Instant.EPOCH,
        progress = null,
        contentIntent = null,
        bubbleIntent = null,
        sourceNotificationKey = id,
        groupKey = null,
        animationCue = cue,
    )
}
