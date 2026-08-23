package com.fourerk.codexpet.overlay

/** Pure tail-center math used by the drawable and unit tests. */
internal object SpeechBubbleTailGeometry {
    fun safeCenter(
        requested: Float,
        bodyStart: Float,
        bodyEnd: Float,
        cornerRadius: Float,
        halfBase: Float,
    ): Float {
        val start = bodyStart + cornerRadius + halfBase
        val end = bodyEnd - cornerRadius - halfBase
        return if (start <= end) {
            requested.coerceIn(start, end)
        } else {
            (bodyStart + bodyEnd) / 2f
        }
    }
}
