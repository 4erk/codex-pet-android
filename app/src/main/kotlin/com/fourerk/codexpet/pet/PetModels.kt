package com.fourerk.codexpet.pet

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.fourerk.codexpet.diagnostics.PetCandidateDiagnostics

enum class PetSource {
    BUILT_IN,
    BUBBLE_ICON,
    CONVERSATION_PERSON_ICON,
    LARGE_ICON,
    SPRITE_SHEET_IMPORT,
    MANUAL_IMPORT,
    CACHE,
}

enum class PetAnimationState {
    IDLE,
    RUNNING_RIGHT,
    RUNNING_LEFT,
    WAVING,
    JUMPING,
    FAILED,
    WAITING,
    RUNNING,
    REVIEW,
}

data class PetFrameSequence(
    val frames: List<Bitmap>,
    val frameDurationsMs: List<Int>,
)

data class PetCandidate(
    val source: PetSource,
    val drawable: Drawable,
    val bitmap: Bitmap,
    val diagnostics: PetCandidateDiagnostics,
)

data class PetInspection(
    val candidates: List<PetCandidate>,
    val selected: PetCandidate?,
    val notes: List<String>,
)

data class PetVisual(
    val drawable: Drawable,
    val bitmap: Bitmap,
    val hash: String,
    val source: PetSource,
    val updatedAt: Long,
    val hasMeaningfulTransparency: Boolean,
    val frameSequences: Map<PetAnimationState, PetFrameSequence> = emptyMap(),
    val lookDirections: List<Bitmap> = emptyList(),
)
