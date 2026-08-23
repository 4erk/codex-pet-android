package com.fourerk.codexpet.pet

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import com.fourerk.codexpet.diagnostics.BitmapDiagnostics
import com.fourerk.codexpet.diagnostics.PetCandidateDiagnostics
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

class PetAssetProvider(private val context: Context) {
    fun inspect(notification: Notification, sourcePackage: String): PetInspection {
        val notes = mutableListOf<String>()
        val icons = mutableListOf<Pair<PetSource, Icon>>()

        notification.bubbleMetadata?.icon?.let { icons += PetSource.BUBBLE_ICON to it }

        val messagingStyle = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        }.getOrNull()
        messagingStyle?.user?.icon
            ?.let { runCatching { it.toIcon(context) }.getOrNull() }
            ?.let { icons += PetSource.CONVERSATION_PERSON_ICON to it }
        messagingStyle?.messages
            ?.asReversed()
            ?.firstNotNullOfOrNull { it.person?.icon }
            ?.let { runCatching { it.toIcon(context) }.getOrNull() }
            ?.let { icons += PetSource.CONVERSATION_PERSON_ICON to it }

        notification.getLargeIcon()?.let { icons += PetSource.LARGE_ICON to it }

        if (notification.shortcutId != null && icons.none { it.first == PetSource.CONVERSATION_PERSON_ICON }) {
            notes += "shortcutId is present, but publisher-owned shortcut icons are not generally readable by a non-launcher app"
        }
        if (icons.isEmpty()) notes += "No BubbleMetadata, conversation Person, or largeIcon candidate was exposed"

        val candidates = icons.mapNotNull { (source, icon) ->
            inspectIcon(icon, source, sourcePackage, enforceTransparency = true)
        }
        return PetInspection(
            candidates = candidates,
            selected = candidates
                .filter { it.diagnostics.acceptedForOverlay }
                .minWithOrNull(
                    compareBy<PetCandidate> { it.source.ordinal }
                        .thenByDescending { candidate ->
                            candidate.bitmap.width.toLong() * candidate.bitmap.height
                        },
                ),
            notes = notes,
        )
    }

    fun inspectDrawable(
        drawable: Drawable,
        source: PetSource,
        enforceTransparency: Boolean,
    ): PetCandidate? {
        val rendered = renderDrawable(drawable) ?: return null
        val bitmapDiagnostics = analyzeBitmap(rendered.bitmap)
        val cleanAlpha = hasCleanAlpha(bitmapDiagnostics)
        val accepted = !enforceTransparency || cleanAlpha
        val diagnostics = PetCandidateDiagnostics(
            source = source.name,
            iconType = -1,
            iconTypeName = "DRAWABLE",
            drawableClass = rendered.drawable.javaClass.name,
            intrinsicWidth = drawable.intrinsicWidth,
            intrinsicHeight = drawable.intrinsicHeight,
            animatable = rendered.drawable is Animatable,
            adaptiveForegroundExtracted = rendered.adaptiveForegroundExtracted,
            bitmap = bitmapDiagnostics,
            acceptedForOverlay = accepted,
            rejectionReason = if (accepted) null else alphaRejectionReason(bitmapDiagnostics),
        )
        return PetCandidate(source, rendered.drawable, rendered.bitmap, diagnostics)
    }

    private fun inspectIcon(
        icon: Icon,
        source: PetSource,
        sourcePackage: String,
        enforceTransparency: Boolean,
    ): PetCandidate? {
        val drawable = loadDrawable(icon, sourcePackage) ?: return null
        val rendered = renderDrawable(drawable) ?: return null
        val bitmapDiagnostics = analyzeBitmap(rendered.bitmap)
        val cleanAlpha = hasCleanAlpha(bitmapDiagnostics)
        val accepted = !enforceTransparency || cleanAlpha
        val diagnostics = PetCandidateDiagnostics(
            source = source.name,
            iconType = icon.type,
            iconTypeName = iconTypeName(icon.type),
            drawableClass = drawable.javaClass.name,
            intrinsicWidth = drawable.intrinsicWidth,
            intrinsicHeight = drawable.intrinsicHeight,
            animatable = rendered.drawable is Animatable,
            adaptiveForegroundExtracted = rendered.adaptiveForegroundExtracted,
            bitmap = bitmapDiagnostics,
            acceptedForOverlay = accepted,
            rejectionReason = if (accepted) null else alphaRejectionReason(bitmapDiagnostics),
        )
        return PetCandidate(source, rendered.drawable, rendered.bitmap, diagnostics)
    }

    private fun loadDrawable(icon: Icon, sourcePackage: String): Drawable? {
        runCatching { icon.loadDrawable(context) }.getOrNull()?.let { return it }
        val packageContext = runCatching {
            context.createPackageContext(sourcePackage, Context.CONTEXT_RESTRICTED)
        }.getOrNull() ?: return null
        return runCatching { icon.loadDrawable(packageContext) }.getOrNull()
    }

    private data class RenderedDrawable(
        val drawable: Drawable,
        val bitmap: Bitmap,
        val adaptiveForegroundExtracted: Boolean,
    )

    private fun renderDrawable(original: Drawable): RenderedDrawable? {
        val adaptive = original as? AdaptiveIconDrawable
        val drawable = (adaptive?.foreground ?: original).mutate()
        if (adaptive == null && drawable is BitmapDrawable && drawable.bitmap.config != Bitmap.Config.HARDWARE) {
            // Keep the exact 192x208 cell exposed by ChatGPT. Drawing it into another bitmap first
            // adds an unnecessary resampling pass and makes the scaled overlay visibly softer.
            drawable.setTargetDensity(context.resources.displayMetrics)
            drawable.isFilterBitmap = true
            drawable.paint.isDither = true
            return RenderedDrawable(drawable, drawable.bitmap, adaptiveForegroundExtracted = false)
        }
        val sourceWidth = when (drawable) {
            is BitmapDrawable -> drawable.bitmap.width
            else -> drawable.intrinsicWidth
        }.takeIf { it > 0 } ?: DEFAULT_RENDER_SIZE
        val sourceHeight = when (drawable) {
            is BitmapDrawable -> drawable.bitmap.height
            else -> drawable.intrinsicHeight
        }.takeIf { it > 0 } ?: DEFAULT_RENDER_SIZE
        val scale = (MAX_RENDER_SIZE.toFloat() / max(sourceWidth, sourceHeight)).coerceAtMost(1f)
        val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        return runCatching {
            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.TRANSPARENT)
            val oldBounds = drawable.bounds
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
            drawable.bounds = oldBounds
            (drawable as? BitmapDrawable)?.apply {
                setTargetDensity(context.resources.displayMetrics)
                isFilterBitmap = true
                paint.isDither = true
            }
            RenderedDrawable(drawable, bitmap, adaptive != null)
        }.getOrNull()
    }

    private fun analyzeBitmap(bitmap: Bitmap): BitmapDiagnostics {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var transparent = 0
        var partial = 0
        pixels.forEach { pixel ->
            when (Color.alpha(pixel)) {
                0 -> transparent++
                in 1..254 -> partial++
            }
        }
        val total = pixels.size.coerceAtLeast(1)
        val transparentCorners = listOf(
            pixels.first(),
            pixels[bitmap.width - 1],
            pixels[(bitmap.height - 1) * bitmap.width],
            pixels.last(),
        ).count { Color.alpha(it) <= 8 }
        val hashInput = ByteBuffer.allocate(8 + pixels.size * Int.SIZE_BYTES)
            .putInt(bitmap.width)
            .putInt(bitmap.height)
        pixels.forEach { pixel -> hashInput.putInt(pixel) }
        return BitmapDiagnostics(
            width = bitmap.width,
            height = bitmap.height,
            hasAlpha = bitmap.hasAlpha(),
            alphaCoveragePercent = ((total - transparent) * 100.0 / total),
            transparentPixelPercent = (transparent * 100.0 / total),
            partialAlphaPixelPercent = (partial * 100.0 / total),
            transparentCorners = transparentCorners,
            sha256 = MessageDigest.getInstance("SHA-256")
                .digest(hashInput.array())
                .joinToString("") { "%02x".format(it) },
        )
    }

    private fun hasCleanAlpha(bitmap: BitmapDiagnostics): Boolean =
        bitmap.hasAlpha && bitmap.transparentPixelPercent >= 2.0 && bitmap.transparentCorners >= 2

    private fun alphaRejectionReason(bitmap: BitmapDiagnostics): String = when {
        !bitmap.hasAlpha -> "Rendered bitmap reports no alpha channel"
        bitmap.transparentPixelPercent < 2.0 -> "Less than 2% of pixels are transparent; background may already be baked in"
        bitmap.transparentCorners < 2 -> "Image corners are mostly opaque; adaptive/system background may be baked in"
        else -> "Candidate did not meet the transparency safety gate"
    }

    companion object {
        private const val DEFAULT_RENDER_SIZE = 256
        private const val MAX_RENDER_SIZE = 768

        fun iconTypeName(type: Int): String = when (type) {
            Icon.TYPE_BITMAP -> "BITMAP"
            Icon.TYPE_RESOURCE -> "RESOURCE"
            Icon.TYPE_DATA -> "DATA"
            Icon.TYPE_URI -> "URI"
            Icon.TYPE_ADAPTIVE_BITMAP -> "ADAPTIVE_BITMAP"
            Icon.TYPE_URI_ADAPTIVE_BITMAP -> "URI_ADAPTIVE_BITMAP"
            else -> "UNKNOWN($type)"
        }
    }
}
