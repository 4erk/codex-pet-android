package com.fourerk.codexpet.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechBubblePlacementTest {
    private val safe = OverlayBounds(0, 0, 1080, 1920)

    @Test
    fun `single bubble stays right of pet and is vertically centered`() {
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
        assertEquals(690, placement.y)
        assertEquals(50f, placement.tailOffset)
    }

    @Test
    fun `short single-line bubble centers its side tail on pet`() {
        val placement = SpeechBubblePlacement.calculate(
            safe = safe,
            petX = 100,
            petY = 700,
            petSize = 80,
            bubbleWidth = 210,
            bubbleHeights = listOf(38),
            margin = 8,
            gap = 6,
        ).single()

        assertEquals(BubbleAnchor.LEFT, placement.anchor)
        assertEquals(721, placement.y)
        assertEquals(19f, placement.tailOffset)
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
        assertEquals(690, placement.y)
        assertEquals(50f, placement.tailOffset)
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

    @Test
    fun `five bubbles stay ordered non overlapping and inside screen`() {
        val heights = listOf(72, 84, 76, 90, 80)
        val placements = SpeechBubblePlacement.calculate(
            safe = safe,
            petX = 460,
            petY = 780,
            petSize = 96,
            bubbleWidth = 260,
            bubbleHeights = heights,
            margin = 8,
            gap = 7,
        )

        assertEquals(5, placements.size)
        placements.forEachIndexed { index, placement ->
            assertTrue(placement.x >= safe.left)
            assertTrue(placement.x + 260 <= safe.right)
            assertTrue(placement.y >= safe.top)
            assertTrue(placement.y + heights[index] <= safe.bottom)
            if (index > 0) {
                assertTrue(placement.y >= placements[index - 1].y + heights[index - 1] + 7)
            }
        }
    }
}
