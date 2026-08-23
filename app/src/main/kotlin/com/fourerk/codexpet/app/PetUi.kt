package com.fourerk.codexpet.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.roundToInt

/**
 * Native settings design system inspired by iOS grouped settings plus HyperOS/MagicOS soft surfaces.
 * Keep controls contextual: navigation is row-based, toggles are trailing controls, and primary
 * actions are visually distinct from ordinary settings rows.
 */
internal object PetUi {
    const val BACKGROUND = 0xFF0B0D10.toInt()
    const val SURFACE = 0xFF171A1F.toInt()
    const val SURFACE_ALT = 0xFF20242A.toInt()
    const val SURFACE_SELECTED = 0xFF28333D.toInt()
    const val SURFACE_HIGHLIGHT = 0xFF222B34.toInt()
    const val STROKE = 0xFF2A3037.toInt()
    const val DIVIDER = 0xFF292E34.toInt()
    const val TEXT = 0xFFF7F8FA.toInt()
    const val MUTED = 0xFFA4ABB4.toInt()
    const val SECONDARY = 0xFFCCD1D7.toInt()
    const val ACCENT = 0xFF7CC7FF.toInt()
    const val ACCENT_SOFT = 0xFF173047.toInt()
    const val GOOD = 0xFF6FD6AC.toInt()
    const val WARN = 0xFFFFC75E.toInt()
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
        setLineSpacing(0f, 1.13f)
        includeFontPadding = false
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun page(context: Context, title: String, subtitle: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20), dp(context, 22), dp(context, 20), dp(context, 44))
        setBackgroundColor(BACKGROUND)
        addView(text(context, title, 32f, TEXT, bold = true).apply {
            letterSpacing = -0.018f
        })
        addView(text(context, subtitle, 13.5f, MUTED).apply {
            setPadding(0, dp(context, 7), 0, 0)
            maxLines = 3
        })
    }

    fun card(context: Context, title: String? = null): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 16), dp(context, 15), dp(context, 16), dp(context, 15))
        background = rounded(context, SURFACE, 20, STROKE)
        elevation = dp(context, 1).toFloat()
        if (!title.isNullOrBlank()) {
            addView(text(context, title, 16.5f, TEXT, bold = true).apply {
                setPadding(dp(context, 1), 0, dp(context, 1), dp(context, 8))
            })
        }
    }

    fun heroCard(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18))
        background = rounded(context, SURFACE_HIGHLIGHT, 26, 0xFF34414C.toInt())
        elevation = dp(context, 2).toFloat()
    }

    fun previewSurface(context: Context): FrameLayout = FrameLayout(context).apply {
        background = rounded(context, SURFACE_ALT, 22, STROKE)
        clipChildren = false
        clipToPadding = false
    }

    fun action(context: Context, title: String, onClick: () -> Unit): Button = MaterialButton(context).apply {
        text = title
        isAllCaps = false
        setTextColor(TEXT)
        textSize = 14f
        minHeight = dp(context, 44)
        minimumHeight = dp(context, 44)
        insetTop = 0
        insetBottom = 0
        cornerRadius = dp(context, 13)
        backgroundTintList = ColorStateList.valueOf(SURFACE_ALT)
        strokeWidth = dp(context, 1)
        strokeColor = ColorStateList.valueOf(STROKE)
        setPadding(dp(context, 14), 0, dp(context, 14), 0)
        setOnClickListener { onClick() }
    }

    fun primaryAction(context: Context, title: String, onClick: () -> Unit): Button = MaterialButton(context).apply {
        text = title
        isAllCaps = false
        setTextColor(0xFF07131D.toInt())
        textSize = 14.5f
        setTypeface(typeface, Typeface.BOLD)
        minHeight = dp(context, 46)
        minimumHeight = dp(context, 46)
        insetTop = 0
        insetBottom = 0
        cornerRadius = dp(context, 14)
        backgroundTintList = ColorStateList.valueOf(ACCENT)
        strokeWidth = 0
        setPadding(dp(context, 16), 0, dp(context, 16), 0)
        setOnClickListener { onClick() }
    }

    /** Grouped-settings row with a trailing system-style switch. */
    fun toggle(context: Context, title: String, subtitle: String? = null): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 2), dp(context, 10), 0, dp(context, 10))

            val labels = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(context, title, 15f, TEXT, bold = true))
                if (!subtitle.isNullOrBlank()) {
                    addView(text(context, subtitle, 11.5f, MUTED).apply {
                        setPadding(0, dp(context, 3), dp(context, 12), 0)
                        maxLines = 3
                    })
                }
            }
            addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(SwitchMaterial(context).apply {
                tag = "switch"
                text = ""
                minWidth = 0
                minimumWidth = 0
                buttonTintList = null
            })
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
        setPadding(dp(context, 2), dp(context, 10), 0, dp(context, 10))
        isClickable = true
        isFocusable = true
        foreground = selectableForeground(context)
        setOnClickListener { onClick() }

        val glyph = text(context, icon, 17f, ACCENT, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(context, ACCENT_SOFT, 11, 0xFF275071.toInt())
        }
        addView(glyph, LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)))

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), 0, dp(context, 8), 0)
            addView(text(context, title, 15f, TEXT, bold = true))
            if (!subtitle.isNullOrBlank()) {
                addView(text(context, subtitle, 11.5f, MUTED).apply {
                    setPadding(0, dp(context, 2), 0, 0)
                    maxLines = 2
                })
            }
        }
        addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (!value.isNullOrBlank()) {
            addView(text(context, value, 12f, SECONDARY).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                maxLines = 1
            })
        }
        addView(text(context, "›", 25f, MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(dp(context, 6), 0, 0, 0)
        })
    }

    fun statusPill(context: Context, value: String, color: Int): TextView =
        text(context, value, 11.5f, color, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
            background = rounded(context, withAlpha(color, 28), 13, withAlpha(color, 72))
        }

    fun valuePill(context: Context, value: String): TextView = text(context, value, 12f, SECONDARY, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5))
        background = rounded(context, SURFACE_ALT, 12, STROKE)
    }

    fun divider(context: Context): View = View(context).apply { setBackgroundColor(DIVIDER) }

    fun addDivider(group: LinearLayout, context: Context) {
        group.addView(divider(context), LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1))
    }

    /** Two-or-three option contextual choice; avoids a detached Android spinner for simple settings. */
    fun segmented(
        context: Context,
        labels: List<String>,
        selected: Int,
        onSelected: (Int) -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3))
        background = rounded(context, 0xFF101318.toInt(), 14, STROKE)
        labels.forEachIndexed { index, label ->
            addView(
                text(context, label, 13f, if (index == selected) TEXT else MUTED, bold = index == selected).apply {
                    tag = "segment:$index"
                    gravity = Gravity.CENTER
                    setPadding(dp(context, 10), dp(context, 9), dp(context, 10), dp(context, 9))
                    background = if (index == selected) rounded(context, SURFACE_SELECTED, 11) else null
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        setSegmentedSelection(this@apply, index)
                        onSelected(index)
                    }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }

    fun setSegmentedSelection(group: LinearLayout, selected: Int) {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) as? TextView ?: continue
            val active = index == selected
            child.setTextColor(if (active) TEXT else MUTED)
            child.setTypeface(Typeface.create("sans-serif", if (active) Typeface.BOLD else Typeface.NORMAL))
            child.background = if (active) rounded(group.context, SURFACE_SELECTED, 11) else null
        }
    }

    fun sectionTitle(context: Context, title: String): TextView = text(context, title, 12f, MUTED, bold = true).apply {
        setPadding(dp(context, 4), dp(context, 22), 0, dp(context, 8))
        letterSpacing = 0.035f
    }

    fun helper(context: Context, value: CharSequence): TextView = text(context, value, 11.5f, MUTED).apply {
        setPadding(dp(context, 4), dp(context, 8), dp(context, 4), 0)
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

    private fun selectableForeground(context: Context) = TypedValue().let { out ->
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        context.getDrawable(out.resourceId)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
}
