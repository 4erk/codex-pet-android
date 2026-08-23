package com.fourerk.codexpet.task

import com.fourerk.codexpet.pet.PetAnimationState

/** Chooses the steady pet animation; transient reactions are handled separately by the overlay. */
object PetAnimationStateResolver {
    fun resolve(tasks: List<CodexTask>): PetAnimationState {
        val current = tasks.filter { it.isDisplayTask() && it.isCodexTask() }
        return when {
            current.any { it.animationCue == TaskAnimationCue.WAITING_FOR_INPUT } -> PetAnimationState.WAITING
            current.any {
                it.status == TaskStatus.ERROR ||
                    it.animationCue == TaskAnimationCue.FAILED ||
                    it.animationCue == TaskAnimationCue.DISCONNECTED
            } -> PetAnimationState.FAILED
            current.any { it.animationCue == TaskAnimationCue.RECONNECTING } -> PetAnimationState.WAITING
            current.any { it.animationCue == TaskAnimationCue.REVIEWING } -> PetAnimationState.REVIEW
            current.any { it.status == TaskStatus.RUNNING || it.animationCue == TaskAnimationCue.ACTIVE } ->
                PetAnimationState.RUNNING
            else -> PetAnimationState.IDLE
        }
    }
}
