package com.fourerk.codexpet.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.roundToInt

/** Small native design system: iOS-style hierarchy with HyperOS/MagicOS-like soft cards. */
internal object PetUi {
    const val BACKGROUND = 0xFF0F1113.toInt()
    const val SURFACE = 0xFF1A1D21.toInt()
    const val SURFACE_ALT = 0xFF24282D.toInt()
    const val SURFACE_SELECTED = 0xFF29323B.toInt()
    const val STROKE = 0xFF30353B.toInt()
    const val TEXT = 0xFFF5F6F7.toInt()
    const val MUTED = 0xFFAEB5BB.toInt()
    const val ACCENT = 0xFF6EB8FF.toInt()
    const val GOOD = 0xFF77D7B1.toInt()
    const val WARN = 0xFFFFC857.toInt()
    const val BAD = 0xFFFF7474.toInt()

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    fun text(
        context: Context,
        value: CharSequence,
        size: Float,
        color: Int = TEXT,
        bold: Boolean = false,
    ): TextView = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        setLineSpacing(0f, 1.12f)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun page(context: Context, title: String, subtitle: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 36))
        setBackgroundColor(BACKGROUND)
        addView(text(context, title, 30f, TEXT, bold = true))
        addView(text(context, subtitle, 14f, MUTED).apply {
            setPadding(0, dp(context, 3), 0, 0)
        })
    }

    fun card(context: Context, title: String? = null): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
        background = rounded(context, SURFACE, 22, STROKE)
        if (!title.isNullOrBlank()) {
            addView(text(context, title, 17f, TEXT, bold = true).apply {
                setPadding(0, 0, 0, dp(context, 5))
            })
        }
    }

    fun action(context: Context, title: String, onClick: () -> Unit): Button = Button(context).apply {
        text = title
        isAllCaps = false
        setTextColor(TEXT)
        textSize = 14f
        minHeight = dp(context, 46)
        backgroundTintList = ColorStateList.valueOf(SURFACE_ALT)
        setOnClickListener { onClick() }
    }

    fun primaryAction(context: Context, title: String, onClick: () -> Unit): Button = action(context, title, onClick).apply {
        setTextColor(0xFF08121B.toInt())
        backgroundTintList = ColorStateList.valueOf(ACCENT)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun toggle(context: Context, title: String, subtitle: String? = null): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 7), 0, dp(context, 7))
        }
        val toggle = SwitchMaterial(context).apply {
            text = title
            textSize = 15f
            setTextColor(TEXT)
            tag = "switch"
        }
        row.addView(toggle)
        if (!subtitle.isNullOrBlank()) {
            row.addView(text(context, subtitle, 12f, MUTED).apply {
                setPadding(dp(context, 4), 0, 0, 0)
            })
        }
        return row
    }

    fun switchFrom(row: View): SwitchMaterial = (row as LinearLayout).findViewWithTag("switch")

    fun navigationRow(
        context: Context,
        icon: String,
        title: String,
        subtitle: String? = null,
        value: String? = null,
        onClick: () -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 4), dp(context, 10), dp(context, 2), dp(context, 10))
        isClickable = true
        isFocusable = true
        foreground = android.util.TypedValue().let { out ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            context.getDrawable(out.resourceId)
        }
        setOnClickListener { onClick() }

        val glyph = text(context, icon, 20f, TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(context, SURFACE_ALT, 12)
        }
        addView(glyph, LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)))

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), 0, dp(context, 8), 0)
            addView(text(context, title, 15f, TEXT, bold = true))
            if (!subtitle.isNullOrBlank()) {
                addView(text(context, subtitle, 11.5f, MUTED).apply { maxLines = 2 })
            }
        }
        addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (!value.isNullOrBlank()) {
            addView(text(context, value, 12f, MUTED).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                maxLines = 1
            })
        }
        addView(text(context, "›", 28f, MUTED).apply { gravity = Gravity.CENTER })
    }

    fun statusPill(context: Context, text: String, color: Int): TextView = text(context, text, 12f, color, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5))
        background = rounded(context, withAlpha(color, 34), 14, withAlpha(color, 80))
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(STROKE)
    }

    fun tile(
        context: Context,
        icon: String,
        title: String,
        subtitle: String,
        onClick: () -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(context, 10), dp(context, 14), dp(context, 10), dp(context, 12))
        background = rounded(context, SURFACE, 24, STROKE)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }

        val iconView = text(context, icon, 28f, TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(context, SURFACE_ALT, 18)
        }
        addView(iconView, LinearLayout.LayoutParams(dp(context, 58), dp(context, 58)))
        addView(text(context, title, 15f, TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 8), 0, 0)
        })
        addView(text(context, subtitle, 11f, MUTED).apply {
            gravity = Gravity.CENTER
            maxLines = 2
        })
    }

    fun sectionTitle(context: Context, title: String): TextView = text(context, title.uppercase(), 12f, MUTED, bold = true).apply {
        setPadding(dp(context, 4), dp(context, 20), 0, dp(context, 6))
        letterSpacing = 0.07f
    }

    fun spacer(context: Context, height: Int): Space = Space(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(context, height))
    }

    fun rounded(context: Context, color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
            strokeColor?.let { setStroke(dp(context, 1), it) }
        }

    fun marginParams(context: Context, top: Int = 12): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(context, top) }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
}
