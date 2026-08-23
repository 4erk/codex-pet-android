package com.fourerk.codexpet.pet

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

internal data class PetFingerprint(
    val aspectRatio: Float,
    val alphaCoverage: Float,
    val colorHistogram: FloatArray,
)

/**
 * ChatGPT republishes several bitmap frames through the conversation Person icon. Exact hashes
 * therefore identify frames, not necessarily a different selected pet. This fingerprint ignores
 * transparent-space movement and compares the alpha-weighted colour palette instead.
 */
internal object PetVisualMatcher {
    fun fingerprint(bitmap: Bitmap): PetFingerprint {
        val histogram = FloatArray(HISTOGRAM_SIZE)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var weightSum = 0f
        var covered = 0
        pixels.forEach { pixel ->
            val alpha = Color.alpha(pixel) / 255f
            if (alpha <= MIN_VISIBLE_ALPHA) return@forEach
            covered++
            val red = Color.red(pixel) ushr 6
            val green = Color.green(pixel) ushr 6
            val blue = Color.blue(pixel) ushr 6
            val index = (red shl 4) or (green shl 2) or blue
            histogram[index] += alpha
            weightSum += alpha
        }
        if (weightSum > 0f) {
            histogram.indices.forEach { histogram[it] /= weightSum }
        }
        return PetFingerprint(
            aspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1),
            alphaCoverage = covered.toFloat() / pixels.size.coerceAtLeast(1),
            colorHistogram = histogram,
        )
    }

    fun looksLikeSamePet(first: Bitmap, second: Bitmap): Boolean =
        looksLikeSamePet(fingerprint(first), fingerprint(second))

    fun looksLikeSamePet(first: PetFingerprint, second: PetFingerprint): Boolean {
        val aspectDelta = abs(first.aspectRatio - second.aspectRatio) /
            first.aspectRatio.coerceAtLeast(0.01f)
        if (aspectDelta > MAX_ASPECT_DELTA) return false
        if (abs(first.alphaCoverage - second.alphaCoverage) > MAX_ALPHA_COVERAGE_DELTA) return false
        val histogramDistance = first.colorHistogram.indices.sumOf { index ->
            abs(first.colorHistogram[index] - second.colorHistogram[index]).toDouble()
        }.toFloat() / 2f
        return histogramDistance <= MAX_HISTOGRAM_DISTANCE
    }

    private const val HISTOGRAM_SIZE = 64
    private const val MIN_VISIBLE_ALPHA = 0.08f
    private const val MAX_ASPECT_DELTA = 0.12f
    private const val MAX_ALPHA_COVERAGE_DELTA = 0.14f
    private const val MAX_HISTOGRAM_DISTANCE = 0.32f
}
