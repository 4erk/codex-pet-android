package com.fourerk.codexpet.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechBubbleTailGeometryTest {
    @Test
    fun `short side bubble centers tail when rounded-corner safe range collapses`() {
        val center = SpeechBubbleTailGeometry.safeCenter(
            requested = 52f,
            bodyStart = 0f,
            bodyEnd = 38f,
            cornerRadius = 18f,
            halfBase = 8f,
        )

        assertEquals(19f, center, 0.001f)
    }

    @Test
    fun `normal bubble keeps requested tail inside safe range`() {
        val center = SpeechBubbleTailGeometry.safeCenter(
            requested = 34f,
            bodyStart = 0f,
            bodyEnd = 100f,
            cornerRadius = 14f,
            halfBase = 7f,
        )

        assertEquals(34f, center, 0.001f)
    }
}
