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
        buildTail()
        canvas.drawRoundRect(body, cornerRadiusPx, cornerRadiusPx, fill)
        canvas.drawPath(tail, fill)
        if (stroke.strokeWidth > 0f) {
            canvas.drawRoundRect(body, cornerRadiusPx, cornerRadiusPx, stroke)
            // The fill triangle intentionally overlaps the body by a few physical pixels. Repaint
            // it after the body stroke so anti-aliasing cannot leave a hairline at the join.
            canvas.drawPath(tail, fill)
            // tailOutline contains only the two exposed sides; there is deliberately no base line.
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
        val overlap = max(2f, stroke.strokeWidth * 1.75f)
        when (tailEdge) {
            TailEdge.LEFT, TailEdge.RIGHT -> {
                val center = SpeechBubbleTailGeometry.safeCenter(
                    requested = tailOffsetPx,
                    bodyStart = body.top,
                    bodyEnd = body.bottom,
                    cornerRadius = cornerRadiusPx,
                    halfBase = halfBase,
                )
                val outlineEdgeX = if (tailEdge == TailEdge.LEFT) body.left else body.right
                val fillEdgeX = if (tailEdge == TailEdge.LEFT) body.left + overlap else body.right - overlap
                val pointX = if (tailEdge == TailEdge.LEFT) bounds.left.toFloat() else bounds.right.toFloat()
                tail.moveTo(fillEdgeX, center - halfBase)
                tail.lineTo(pointX, center)
                tail.lineTo(fillEdgeX, center + halfBase)
                tail.close()
                tailOutline.moveTo(outlineEdgeX, center - halfBase)
                tailOutline.lineTo(pointX, center)
                tailOutline.lineTo(outlineEdgeX, center + halfBase)
            }
            TailEdge.TOP, TailEdge.BOTTOM -> {
                val center = SpeechBubbleTailGeometry.safeCenter(
                    requested = tailOffsetPx,
                    bodyStart = body.left,
                    bodyEnd = body.right,
                    cornerRadius = cornerRadiusPx,
                    halfBase = halfBase,
                )
                val outlineEdgeY = if (tailEdge == TailEdge.TOP) body.top else body.bottom
                val fillEdgeY = if (tailEdge == TailEdge.TOP) body.top + overlap else body.bottom - overlap
                val pointY = if (tailEdge == TailEdge.TOP) bounds.top.toFloat() else bounds.bottom.toFloat()
                tail.moveTo(center - halfBase, fillEdgeY)
                tail.lineTo(center, pointY)
                tail.lineTo(center + halfBase, fillEdgeY)
                tail.close()
                tailOutline.moveTo(center - halfBase, outlineEdgeY)
                tailOutline.lineTo(center, pointY)
                tailOutline.lineTo(center + halfBase, outlineEdgeY)
            }
        }
    }
}
