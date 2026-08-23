package com.fourerk.codexpet.overlay

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

internal enum class TailEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

internal class SpeechBubbleDrawable(
    color: Int,
    strokeColor: Int,
    private val cornerRadiusPx: Float,
    private val tailSizePx: Float,
    strokeWidthPx: Float,
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = strokeColor
        strokeWidth = strokeWidthPx
    }
    private val body = RectF()
    private val tail = Path()
    private val tailOutline = Path()

    var tailEdge: TailEdge = TailEdge.RIGHT
        private set
    var tailOffsetPx: Float = 0f
        private set

    fun pointTo(edge: TailEdge, offsetPx: Float) {
        if (tailEdge == edge && tailOffsetPx == offsetPx) return
        tailEdge = edge
        tailOffsetPx = offsetPx
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        calculateBody()
        canvas.drawRoundRect(body, cornerRadiusPx, cornerRadiusPx, fill)
        buildTail()
        canvas.drawPath(tail, fill)
        if (stroke.strokeWidth > 0f) {
            canvas.drawRoundRect(body, cornerRadiusPx, cornerRadiusPx, stroke)
            // Paint the tail once more to erase the body's stroke under its base, then outline
            // only the two exposed sides. This avoids a dark seam inside the comic bubble.
            canvas.drawPath(tail, fill)
            canvas.drawPath(tailOutline, stroke)
        }
    }

    override fun getPadding(padding: Rect): Boolean {
        val tail = tailSizePx.toInt()
        padding.set(
            if (tailEdge == TailEdge.LEFT) tail else 0,
            if (tailEdge == TailEdge.TOP) tail else 0,
            if (tailEdge == TailEdge.RIGHT) tail else 0,
            if (tailEdge == TailEdge.BOTTOM) tail else 0,
        )
        return true
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        stroke.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        stroke.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in the Android framework")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun calculateBody() {
        body.set(bounds)
        when (tailEdge) {
            TailEdge.LEFT -> body.left += tailSizePx
            TailEdge.RIGHT -> body.right -= tailSizePx
            TailEdge.TOP -> body.top += tailSizePx
            TailEdge.BOTTOM -> body.bottom -= tailSizePx
        }
    }

    private fun buildTail() {
        tail.reset()
        tailOutline.reset()
        val halfBase = tailSizePx * 0.72f
        when (tailEdge) {
            TailEdge.LEFT, TailEdge.RIGHT -> {
                val center = tailOffsetPx.coerceIn(
                    body.top + cornerRadiusPx + halfBase,
                    max(body.top + cornerRadiusPx + halfBase, body.bottom - cornerRadiusPx - halfBase),
                )
                val edgeX = if (tailEdge == TailEdge.LEFT) body.left else body.right
                val pointX = if (tailEdge == TailEdge.LEFT) bounds.left.toFloat() else bounds.right.toFloat()
                tail.moveTo(edgeX, center - halfBase)
                tail.lineTo(pointX, center)
                tail.lineTo(edgeX, center + halfBase)
                tailOutline.moveTo(edgeX, center - halfBase)
                tailOutline.lineTo(pointX, center)
                tailOutline.lineTo(edgeX, center + halfBase)
            }
            TailEdge.TOP, TailEdge.BOTTOM -> {
                val minimum = body.left + cornerRadiusPx + halfBase
                val maximum = max(minimum, min(body.right - cornerRadiusPx - halfBase, body.right))
                val center = tailOffsetPx.coerceIn(minimum, maximum)
                val edgeY = if (tailEdge == TailEdge.TOP) body.top else body.bottom
                val pointY = if (tailEdge == TailEdge.TOP) bounds.top.toFloat() else bounds.bottom.toFloat()
                tail.moveTo(center - halfBase, edgeY)
                tail.lineTo(center, pointY)
                tail.lineTo(center + halfBase, edgeY)
                tailOutline.moveTo(center - halfBase, edgeY)
                tailOutline.lineTo(center, pointY)
                tailOutline.lineTo(center + halfBase, edgeY)
            }
        }
        tail.close()
    }
}
