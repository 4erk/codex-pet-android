package com.fourerk.codexpet.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.roundToInt

internal object PetUi {
    const val BACKGROUND = 0xFF111315.toInt()
    const val SURFACE = 0xFF1C1F23.toInt()
    const val SURFACE_ALT = 0xFF25292E.toInt()
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

    fun card(context: Context, title: String? = null): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
        background = rounded(context, SURFACE, 22)
        if (!title.isNullOrBlank()) {
            addView(text(context, title, 18f, TEXT, bold = true))
        }
    }

    fun action(context: Context, title: String, onClick: () -> Unit): Button = Button(context).apply {
        text = title
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    fun toggle(context: Context, title: String, subtitle: String? = null): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 6), 0, dp(context, 6))
        }
        val toggle = SwitchMaterial(context).apply {
            text = title
            setTextColor(TEXT)
            tag = "switch"
        }
        row.addView(toggle)
        if (!subtitle.isNullOrBlank()) {
            row.addView(text(context, subtitle, 12f, MUTED))
        }
        return row
    }

    fun switchFrom(row: View): SwitchMaterial = (row as LinearLayout).findViewWithTag("switch")

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
        background = rounded(context, SURFACE, 24)
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

    fun sectionTitle(context: Context, title: String): TextView = text(context, title, 13f, MUTED, bold = true).apply {
        setPadding(dp(context, 4), dp(context, 18), 0, dp(context, 6))
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
}
