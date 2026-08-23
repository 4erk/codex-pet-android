package com.fourerk.codexpet.pet

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.fourerk.codexpet.diagnostics.PetCandidateDiagnostics

enum class PetSource {
    BUBBLE_ICON,
    CONVERSATION_PERSON_ICON,
    LARGE_ICON,
    MANUAL_IMPORT,
    CACHE,
}

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
)
