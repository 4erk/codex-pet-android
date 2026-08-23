package com.fourerk.codexpet.overlay

internal enum class BubbleAnchor {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

internal data class OverlayBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal data class BubblePlacement(
    val x: Int,
    val y: Int,
    val anchor: BubbleAnchor,
    val tailOffset: Float,
)

/** Pure placement math so drag can move existing WindowManager views without recreating them. */
internal object SpeechBubblePlacement {
    fun calculate(
        safe: OverlayBounds,
        petX: Int,
        petY: Int,
        petSize: Int,
        bubbleWidth: Int,
        bubbleHeights: List<Int>,
        margin: Int,
        gap: Int,
    ): List<BubblePlacement> {
        if (bubbleHeights.isEmpty()) return emptyList()

        val petCenterX = petX + petSize / 2
        val totalHeight = bubbleHeights.sum() + gap * (bubbleHeights.size - 1).coerceAtLeast(0)
        val rightX = petX + petSize + margin
        val leftX = petX - bubbleWidth - margin
        val fitsRight = rightX + bubbleWidth <= safe.right
        val fitsLeft = leftX >= safe.left

        if (fitsRight || fitsLeft) {
            val useRight = fitsRight && (!fitsLeft || petCenterX < (safe.left + safe.right) / 2)
            val x = if (useRight) rightX else leftX
            var y = if (bubbleHeights.size == 1) {
                petY.coerceIn(safe.top, (safe.bottom - bubbleHeights.first()).coerceAtLeast(safe.top))
            } else {
                (petY + petSize / 2 - totalHeight / 2)
                    .coerceIn(safe.top, (safe.bottom - totalHeight).coerceAtLeast(safe.top))
            }
            return bubbleHeights.mapIndexed { index, height ->
                val targetY = if (bubbleHeights.size == 1) {
                    petY + petSize / 2
                } else {
                    petY + (((index + 1f) / (bubbleHeights.size + 1f)) * petSize).toInt()
                }
                BubblePlacement(
                    x = x,
                    y = y,
                    anchor = if (useRight) BubbleAnchor.LEFT else BubbleAnchor.RIGHT,
                    tailOffset = (targetY - y).toFloat(),
                ).also { y += height + gap }
            }
        }

        val belowY = petY + petSize + margin
        val aboveY = petY - totalHeight - margin
        val spaceBelow = safe.bottom - belowY
        val spaceAbove = petY - margin - safe.top
        val useBelow = spaceBelow >= totalHeight || spaceBelow >= spaceAbove
        val x = (petCenterX - bubbleWidth / 2)
            .coerceIn(safe.left, (safe.right - bubbleWidth).coerceAtLeast(safe.left))
        var y = (if (useBelow) belowY else aboveY)
            .coerceIn(safe.top, (safe.bottom - totalHeight).coerceAtLeast(safe.top))

        return bubbleHeights.mapIndexed { index, height ->
            BubblePlacement(
                x = x,
                y = y,
                anchor = if (useBelow) BubbleAnchor.TOP else BubbleAnchor.BOTTOM,
                tailOffset = (petCenterX - x).toFloat(),
            ).also { y += height + gap }
        }
    }
}
