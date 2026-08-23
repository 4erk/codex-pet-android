package com.fourerk.codexpet.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechBubblePageFitTest {
    @Test
    fun `keeps all bubbles when stack fits`() {
        assertEquals(5, SpeechBubblePageFit.fittingCount(listOf(60, 60, 60, 60, 60), 8, 400))
    }

    @Test
    fun `moves overflow bubbles to next page`() {
        assertEquals(3, SpeechBubblePageFit.fittingCount(listOf(100, 100, 100, 100, 100), 10, 330))
    }

    @Test
    fun `always keeps at least first bubble even when it is taller than screen`() {
        assertEquals(1, SpeechBubblePageFit.fittingCount(listOf(500, 100), 10, 300))
    }
}
