package com.fourerk.codexpet.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fourerk.codexpet.overlay.BubbleAnchor
import com.fourerk.codexpet.overlay.OverlayBounds
import com.fourerk.codexpet.overlay.SpeechBubbleDrawable
import com.fourerk.codexpet.overlay.SpeechBubblePlacement
import com.fourerk.codexpet.overlay.TailEdge
import kotlin.math.roundToInt

/** Interactive production preview for 1–5 real speech bubble drawables and placement rules. */
class BubbleLabActivity : AppCompatActivity() {
    private lateinit var stage: FrameLayout
    private lateinit var petView: ImageView
    private lateinit var status: TextView
    private val bubbles = mutableListOf<LabBubble>()
    private var count = 5
    private var downX = 0f
    private var downY = 0f
    private var downPetX = 0f
    private var downPetY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        stage.post {
            centerPet()
            renderBubbles()
        }
    }

    private fun buildContent(): View {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PetUi.dp(this@BubbleLabActivity, 18), PetUi.dp(this@BubbleLabActivity, 20), PetUi.dp(this@BubbleLabActivity, 18), PetUi.dp(this@BubbleLabActivity, 36))
            setBackgroundColor(PetUi.BACKGROUND)
        }
        body.addView(PetUi.text(this, "Стенд баблов", 28f, PetUi.TEXT, bold = true))
        body.addView(PetUi.text(this, "Проверка 1–5 реплик без ожидания уведомлений. Пета можно таскать по стенду.", 14f, PetUi.MUTED))

        val preview = PetUi.card(this, "Живой макет")
        status = PetUi.text(this, "5 реплик", 12f, PetUi.MUTED)
        preview.addView(status)
        stage = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            background = PetUi.rounded(this@BubbleLabActivity, PetUi.SURFACE_ALT, 20)
        }
        preview.addView(stage, LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this, 420))
        body.addView(preview, PetUi.marginParams(this, 16))

        val controls = PetUi.card(this, "Количество")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        (1..5).forEach { value ->
            row.addView(
                PetUi.action(this, value.toString()) {
                    count = value
                    renderBubbles()
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        controls.addView(row)
        controls.addView(PetUi.text(
            this,
            "Проверяются те же правила: сторона относительно пета, fallback сверху/снизу, safe bounds, хвост и компактный stack. Масштаб берётся из текущих настроек реплик.",
            12f,
            PetUi.MUTED,
        ))
        body.addView(controls, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Связанные настройки").apply {
            addView(PetUi.action(this@BubbleLabActivity, "Реплики и масштаб") {
                startActivity(Intent(this@BubbleLabActivity, SpeechSettingsActivity::class.java))
            })
            addView(PetUi.action(this@BubbleLabActivity, "Анимации пета") {
                startActivity(Intent(this@BubbleLabActivity, AnimationSettingsActivity::class.java))
            })
        }, PetUi.marginParams(this))

        petView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(AppGraph.pets.visual.value?.bitmap)
            contentDescription = "Тестовый питомец — перетащите"
            setOnTouchListener(::onPetTouch)
        }
        stage.addView(
            petView,
            FrameLayout.LayoutParams(PetUi.dp(this, AppGraph.settings.settings.value.petSizeDp), PetUi.dp(this, AppGraph.settings.settings.value.petSizeDp)),
        )

        return ScrollView(this).apply { addView(body) }
    }

    private fun centerPet() {
        petView.x = ((stage.width - petView.width) / 2f).coerceAtLeast(0f)
        petView.y = ((stage.height - petView.height) / 2f).coerceAtLeast(0f)
    }

    private fun onPetTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downPetX = view.x
                downPetY = view.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                view.x = (downPetX + event.rawX - downX).coerceIn(0f, (stage.width - view.width).coerceAtLeast(0).toFloat())
                view.y = (downPetY + event.rawY - downY).coerceIn(0f, (stage.height - view.height).coerceAtLeast(0).toFloat())
                layoutBubbles()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.performClick()
                return true
            }
        }
        return false
    }

    private fun renderBubbles() {
        bubbles.forEach { stage.removeView(it.root) }
        bubbles.clear()
        val settings = AppGraph.settings.settings.value
        val scale = settings.bubbleScale.coerceIn(0.75f, 1.5f)
        val width = PetUi.dp(this, (190f * scale).roundToInt().coerceIn(150, 285))
        val tail = PetUi.dp(this, (11f * scale).roundToInt().coerceIn(8, 17))
        val samples = listOf(
            "Нужно подтверждение: выбрать вариант",
            "Проверяю тесты и lint",
            "Собираю APK и обновляю код",
            "Связь восстановлена — продолжаю",
            "Готово: все проверки прошли",
        )
        repeat(count) { index ->
            val drawable = SpeechBubbleDrawable(
                color = 0xF226292E.toInt(),
                strokeColor = 0xFF535962.toInt(),
                cornerRadiusPx = PetUi.dp(this, 18).toFloat() * scale,
                tailSizePx = tail.toFloat(),
                strokeWidthPx = PetUi.dp(this, 1).toFloat(),
            )
            val label = TextView(this).apply {
                text = samples[index]
                textSize = (13f * scale).coerceIn(10f, 18f)
                setTextColor(Color.WHITE)
                setPadding(PetUi.dp(this@BubbleLabActivity, 12), PetUi.dp(this@BubbleLabActivity, 9), PetUi.dp(this@BubbleLabActivity, 12), PetUi.dp(this@BubbleLabActivity, 9))
                maxLines = 4
                gravity = Gravity.CENTER_VERTICAL
            }
            val root = FrameLayout(this).apply {
                background = drawable
                clipChildren = false
                addView(label, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            }
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(PetUi.dp(this, 150), View.MeasureSpec.AT_MOST),
            )
            stage.addView(root, FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT))
            bubbles += LabBubble(root, drawable, tail)
        }
        status.text = "$count ${when (count) { 1 -> "реплика"; in 2..4 -> "реплики"; else -> "реплик" }} · масштаб ${"%.2f".format(scale)}×"
        stage.post(::layoutBubbles)
    }

    private fun layoutBubbles() {
        if (stage.width <= 0 || bubbles.isEmpty()) return
        val settings = AppGraph.settings.settings.value
        val scale = settings.bubbleScale.coerceIn(0.75f, 1.5f)
        val placements = SpeechBubblePlacement.calculate(
            safe = OverlayBounds(0, 0, stage.width, stage.height),
            petX = petView.x.roundToInt(),
            petY = petView.y.roundToInt(),
            petSize = petView.width,
            bubbleWidth = bubbles.first().root.measuredWidth,
            bubbleHeights = bubbles.map { it.root.measuredHeight.coerceAtLeast(PetUi.dp(this, 48)) },
            margin = PetUi.dp(this, (5f * scale).roundToInt().coerceIn(3, 8)),
            gap = PetUi.dp(this, (5f * scale).roundToInt().coerceIn(3, 8)),
        )
        bubbles.zip(placements).forEach { (bubble, placement) ->
            val edge = when (placement.anchor) {
                BubbleAnchor.LEFT -> TailEdge.LEFT
                BubbleAnchor.RIGHT -> TailEdge.RIGHT
                BubbleAnchor.TOP -> TailEdge.TOP
                BubbleAnchor.BOTTOM -> TailEdge.BOTTOM
            }
            bubble.drawable.pointTo(edge, placement.tailOffset)
            bubble.root.setPadding(
                if (edge == TailEdge.LEFT) bubble.tail else 0,
                if (edge == TailEdge.TOP) bubble.tail else 0,
                if (edge == TailEdge.RIGHT) bubble.tail else 0,
                if (edge == TailEdge.BOTTOM) bubble.tail else 0,
            )
            bubble.root.x = placement.x.toFloat()
            bubble.root.y = placement.y.toFloat()
        }
    }

    private data class LabBubble(
        val root: FrameLayout,
        val drawable: SpeechBubbleDrawable,
        val tail: Int,
    )
}
