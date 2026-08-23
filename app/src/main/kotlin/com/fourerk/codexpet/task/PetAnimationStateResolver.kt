package com.fourerk.codexpet.task

import com.fourerk.codexpet.pet.PetAnimationState

/** Chooses the steady pet animation; transient reactions are handled separately by the overlay. */
object PetAnimationStateResolver {
    fun resolve(tasks: List<CodexTask>): PetAnimationState {
        val current = tasks.filter { it.isDisplayTask() && it.isCodexTask() }
        return when {
            current.any { it.animationCue == TaskAnimationCue.WAITING_FOR_INPUT } -> PetAnimationState.WAITING
            // A reconnect cue is more specific and newer than a coarse ERROR status that may stay
            // set on the notification while ChatGPT is already attempting recovery.
            current.any { it.animationCue == TaskAnimationCue.RECONNECTING } -> PetAnimationState.WAITING
            current.any {
                it.animationCue == TaskAnimationCue.FAILED ||
                    it.animationCue == TaskAnimationCue.DISCONNECTED ||
                    (it.status == TaskStatus.ERROR && it.animationCue != TaskAnimationCue.RECONNECTING)
            } -> PetAnimationState.FAILED
            current.any { it.animationCue == TaskAnimationCue.REVIEWING } -> PetAnimationState.REVIEW
            current.any { it.status == TaskStatus.RUNNING || it.animationCue == TaskAnimationCue.ACTIVE } ->
                PetAnimationState.RUNNING
            else -> PetAnimationState.IDLE
        }
    }
}
