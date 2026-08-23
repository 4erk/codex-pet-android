package com.fourerk.codexpet.overlay

/** Chooses how many prepared bubbles can fit vertically without overlapping screen bounds. */
internal object SpeechBubblePageFit {
    fun fittingCount(heights: List<Int>, gap: Int, availableHeight: Int): Int {
        if (heights.isEmpty()) return 0
        val safeHeight = availableHeight.coerceAtLeast(1)
        var total = 0
        heights.forEachIndexed { index, height ->
            val candidate = total + (if (index > 0) gap.coerceAtLeast(0) else 0) + height.coerceAtLeast(1)
            if (index > 0 && candidate > safeHeight) return index
            total = candidate
        }
        return heights.size
    }
}
