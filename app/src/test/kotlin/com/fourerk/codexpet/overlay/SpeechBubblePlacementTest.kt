package com.fourerk.codexpet.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechBubblePlacementTest {
    private val safe = OverlayBounds(0, 0, 1080, 1920)

    @Test
    fun `single bubble stays right of pet when there is room`() {
        val placement = SpeechBubblePlacement.calculate(
            safe = safe,
            petX = 100,
            petY = 700,
            petSize = 80,
            bubbleWidth = 240,
            bubbleHeights = listOf(100),
            margin = 8,
            gap = 6,
        ).single()

        assertEquals(BubbleAnchor.LEFT, placement.anchor)
        assertEquals(188, placement.x)
        assertEquals(700, placement.y)
        assertEquals(40f, placement.tailOffset)
    }

    @Test
    fun `single bubble flips left near right screen edge`() {
        val placement = SpeechBubblePlacement.calculate(
            safe = safe,
            petX = 900,
            petY = 700,
            petSize = 80,
            bubbleWidth = 240,
            bubbleHeights = listOf(100),
            margin = 8,
            gap = 6,
        ).single()

        assertEquals(BubbleAnchor.RIGHT, placement.anchor)
        assertEquals(652, placement.x)
        assertEquals(700, placement.y)
    }

    @Test
    fun `wide bubbles fall below pet and remain inside safe bounds`() {
        val compactSafe = OverlayBounds(0, 24, 360, 800)
        val placements = SpeechBubblePlacement.calculate(
            safe = compactSafe,
            petX = 140,
            petY = 300,
            petSize = 80,
            bubbleWidth = 300,
            bubbleHeights = listOf(100, 120),
            margin = 8,
            gap = 6,
        )

        assertEquals(2, placements.size)
        assertTrue(placements.all { it.anchor == BubbleAnchor.TOP })
        assertEquals(30, placements.first().x)
        assertEquals(388, placements.first().y)
        assertEquals(494, placements.last().y)
        assertTrue(placements.last().y + 120 <= compactSafe.bottom)
    }

    @Test
    fun `stack is clamped vertically near bottom edge`() {
        val placements = SpeechBubblePlacement.calculate(
            safe = safe,
            petX = 100,
            petY = 1850,
            petSize = 60,
            bubbleWidth = 240,
            bubbleHeights = listOf(90, 90, 90),
            margin = 8,
            gap = 6,
        )

        assertEquals(3, placements.size)
        assertTrue(placements.first().y >= safe.top)
        assertTrue(placements.last().y + 90 <= safe.bottom)
    }
}
