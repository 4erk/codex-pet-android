package com.fourerk.codexpet.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.overlay.SpeechBubbleDrawable
import com.fourerk.codexpet.overlay.TailEdge
import com.fourerk.codexpet.settings.SpeechStyle
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SpeechSettingsActivity : AppCompatActivity() {
    private lateinit var styleSegments: LinearLayout
    private lateinit var styleExample: TextView
    private lateinit var previewStage: FrameLayout
    private lateinit var previewPet: ImageView
    private lateinit var previewBubble: FrameLayout
    private lateinit var previewBubbleText: TextView
    private lateinit var bubbleScaleValue: TextView
    private lateinit var bubbleScaleSeek: SeekBar
    private lateinit var maxValue: TextView
    private lateinit var maxSeek: SeekBar
    private lateinit var completionValue: TextView
    private lateinit var completionSeek: SeekBar
    private lateinit var autoSwitch: SwitchMaterial
    private lateinit var attentionSwitch: SwitchMaterial
    private lateinit var chatSwitch: SwitchMaterial
    private lateinit var completionSwitch: SwitchMaterial
    private var binding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        bind()
        observe()
    }

    private fun buildContent(): android.view.View {
        val body = PetUi.page(
            this,
            "Реплики",
            "Текст, масштаб и правила показа — с живым предпросмотром рядом с питомцем.",
        )

        val previewCard = PetUi.heroCard(this)
        previewCard.addView(PetUi.text(this, "Предпросмотр", 17f, PetUi.TEXT, bold = true))
        previewCard.addView(PetUi.text(
            this,
            "Короткая строка показывает самый чувствительный случай для бокового хвоста.",
            11.5f,
            PetUi.MUTED,
        ).apply { setPadding(0, PetUi.dp(this@SpeechSettingsActivity, 4), 0, PetUi.dp(this@SpeechSettingsActivity, 12)) })

        previewStage = PetUi.previewSurface(this)
        previewPet = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(AppGraph.pets.visual.value?.bitmap)
            contentDescription = "Питомец в предпросмотре реплики"
        }
        previewStage.addView(
            previewPet,
            FrameLayout.LayoutParams(PetUi.dp(this, 72), PetUi.dp(this, 72)),
        )
        previewBubbleText = TextView(this).apply {
            text = "Короткая реплика"
            setTextColor(Color.WHITE)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }
        previewBubble = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            addView(previewBubbleText, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        previewStage.addView(previewBubble)
        previewCard.addView(previewStage, LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this, 205))

        val scaleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, PetUi.dp(this@SpeechSettingsActivity, 14), 0, 0)
            addView(
                PetUi.text(this@SpeechSettingsActivity, "Масштаб", 14.5f, PetUi.TEXT, bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            bubbleScaleValue = PetUi.valuePill(this@SpeechSettingsActivity, "1.00×")
            addView(bubbleScaleValue)
        }
        previewCard.addView(scaleHeader)
        bubbleScaleSeek = SeekBar(this).apply { max = 75 }
        previewCard.addView(bubbleScaleSeek)
        body.addView(previewCard, PetUi.marginParams(this, 16))

        body.addView(PetUi.sectionTitle(this, "Текст"))
        val voiceCard = PetUi.card(this)
        styleSegments = PetUi.segmented(
            this,
            labels = listOf("Живой", "Точный"),
            selected = 0,
        ) { position ->
            if (!binding) lifecycleScope.launch {
                AppGraph.settings.setSpeechStyle(if (position == 0) SpeechStyle.FRIENDLY else SpeechStyle.EXACT)
            }
        }
        voiceCard.addView(styleSegments)
        styleExample = PetUi.text(this, "", 12f, PetUi.MUTED).apply {
            setPadding(
                PetUi.dp(this@SpeechSettingsActivity, 2),
                PetUi.dp(this@SpeechSettingsActivity, 10),
                PetUi.dp(this@SpeechSettingsActivity, 2),
                0,
            )
        }
        voiceCard.addView(styleExample)
        body.addView(voiceCard, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Плотность"))
        val densityCard = PetUi.card(this)
        val maxHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                PetUi.text(this@SpeechSettingsActivity, "Одновременно", 15f, PetUi.TEXT, bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            maxValue = PetUi.valuePill(this@SpeechSettingsActivity, "5")
            addView(maxValue)
        }
        densityCard.addView(maxHeader)
        maxSeek = SeekBar(this).apply { max = 4 }
        densityCard.addView(maxSeek)
        densityCard.addView(PetUi.helper(
            this,
            "Если выбранные реплики не помещаются, показывается столько, сколько реально входит на экран, а остальные переходят на следующую страницу.",
        ))
        densityCard.addView(PetUi.navigationRow(this, "▱", "Стенд", "1–5 реплик, края и поворот экрана") {
            startActivity(Intent(this@SpeechSettingsActivity, BubbleLabActivity::class.java))
        })
        body.addView(densityCard, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Автоматический показ"))
        val autoCard = PetUi.card(this)
        val autoRow = PetUi.toggle(this, "Автоматические реплики", "Главный выключатель. Ручной тап по питомцу всё равно показывает актуальный контекст.")
        autoSwitch = PetUi.switchFrom(autoRow)
        val attentionRow = PetUi.toggle(this, "Требует внимания", "Ответ, разрешение, ошибка и потеря связи остаются до изменения ситуации.")
        attentionSwitch = PetUi.switchFrom(attentionRow)
        val chatRow = PetUi.toggle(this, "Сообщения ChatGPT", "Короткие временные реплики не смешиваются с состояниями задач Codex.")
        chatSwitch = PetUi.switchFrom(chatRow)
        val completionRow = PetUi.toggle(this, "Успешное завершение", "Результат показывается кратко; анимация успеха проигрывается один раз.")
        completionSwitch = PetUi.switchFrom(completionRow)
        autoCard.addView(autoRow)
        PetUi.addDivider(autoCard, this)
        autoCard.addView(attentionRow)
        PetUi.addDivider(autoCard, this)
        autoCard.addView(chatRow)
        PetUi.addDivider(autoCard, this)
        autoCard.addView(completionRow)
        PetUi.addDivider(autoCard, this)

        val durationHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(PetUi.dp(this@SpeechSettingsActivity, 2), PetUi.dp(this@SpeechSettingsActivity, 12), 0, 0)
            addView(
                PetUi.text(this@SpeechSettingsActivity, "Время результата", 14.5f, PetUi.TEXT, bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            completionValue = PetUi.valuePill(this@SpeechSettingsActivity, "5 с")
            addView(completionValue)
        }
        autoCard.addView(durationHeader)
        completionSeek = SeekBar(this).apply { max = 30 }
        autoCard.addView(completionSeek)
        body.addView(autoCard, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Связано"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@SpeechSettingsActivity, "✦", "Анимации", "Какая реакция соответствует каждому типу реплики") {
                startActivity(Intent(this@SpeechSettingsActivity, AnimationSettingsActivity::class.java))
            })
            PetUi.addDivider(this, this@SpeechSettingsActivity)
            addView(PetUi.navigationRow(this@SpeechSettingsActivity, "↗", "Подключение", "Если реплики перестали обновляться") {
                startActivity(Intent(this@SpeechSettingsActivity, IntegrationActivity::class.java))
            })
        }, PetUi.marginParams(this, 4))

        return ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(body)
        }
    }

    private fun bind() {
        bubbleScaleSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val scale = scaleFromProgress(progress)
                bubbleScaleValue.text = "${"%.2f".format(scale)}×"
                renderBubblePreview(scale)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                lifecycleScope.launch { AppGraph.settings.setBubbleScale(scaleFromProgress(seekBar.progress)) }
            }
        })
        maxSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) maxValue.text = (progress + 1).toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                lifecycleScope.launch { AppGraph.settings.setMaxVisibleBubbles(seekBar.progress + 1) }
            }
        })
        completionSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) completionValue.text = "$progress с"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                lifecycleScope.launch { AppGraph.settings.setCompletedVisibleSeconds(seekBar.progress) }
            }
        })
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            if (!binding) lifecycleScope.launch { AppGraph.settings.setAutoTaskBubblesEnabled(checked) }
        }
        attentionSwitch.setOnCheckedChangeListener { _, checked ->
            if (!binding) lifecycleScope.launch { AppGraph.settings.setAttentionBubblesEnabled(checked) }
        }
        chatSwitch.setOnCheckedChangeListener { _, checked ->
            if (!binding) lifecycleScope.launch { AppGraph.settings.setChatMessageBubblesEnabled(checked) }
        }
        completionSwitch.setOnCheckedChangeListener { _, checked ->
            if (!binding) lifecycleScope.launch { AppGraph.settings.setCompletionBubblesEnabled(checked) }
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AppGraph.settings.settings.collect { settings ->
                        binding = true
                        PetUi.setSegmentedSelection(styleSegments, if (settings.speechStyle == SpeechStyle.FRIENDLY) 0 else 1)
                        bubbleScaleSeek.progress = progressFromScale(settings.bubbleScale)
                        bubbleScaleValue.text = "${"%.2f".format(settings.bubbleScale)}×"
                        maxSeek.progress = settings.maxVisibleBubbles - 1
                        maxValue.text = settings.maxVisibleBubbles.toString()
                        completionSeek.progress = settings.completedVisibleSeconds
                        completionValue.text = "${settings.completedVisibleSeconds} с"
                        autoSwitch.isChecked = settings.autoTaskBubblesEnabled
                        attentionSwitch.isChecked = settings.attentionBubblesEnabled
                        chatSwitch.isChecked = settings.chatMessageBubblesEnabled
                        completionSwitch.isChecked = settings.completionBubblesEnabled
                        styleExample.text = if (settings.speechStyle == SpeechStyle.FRIENDLY) {
                            "«Тут нужен ты: выбери вариант» · смысл и фактический текст уведомления сохраняются."
                        } else {
                            "«Choose an option» · показывается исходный текст уведомления."
                        }
                        binding = false
                        renderBubblePreview(settings.bubbleScale)
                    }
                }
                launch {
                    AppGraph.pets.visual.collect { pet ->
                        previewPet.setImageBitmap(pet?.bitmap)
                    }
                }
            }
        }
    }

    private fun renderBubblePreview(scaleValue: Float) {
        if (!::previewStage.isInitialized) return
        val scale = scaleValue.coerceIn(0.75f, 1.5f)
        val tail = PetUi.dp(this, (11f * scale).roundToInt().coerceIn(8, 17))
        val bubbleWidth = PetUi.dp(this, (178f * scale).roundToInt().coerceIn(142, 267))
        val horizontal = PetUi.dp(this, (12f * scale).roundToInt().coerceIn(9, 18))
        val vertical = PetUi.dp(this, (8f * scale).roundToInt().coerceIn(6, 12))
        val drawable = SpeechBubbleDrawable(
            color = 0xF226292E.toInt(),
            strokeColor = 0xFF59616B.toInt(),
            cornerRadiusPx = PetUi.dp(this, 18).toFloat() * scale,
            tailSizePx = tail.toFloat(),
            strokeWidthPx = PetUi.dp(this, 1).toFloat(),
        )
        previewBubble.background = drawable
        previewBubble.setPadding(tail, 0, 0, 0)
        previewBubble.layoutParams = (previewBubble.layoutParams as FrameLayout.LayoutParams).apply {
            width = bubbleWidth
            height = FrameLayout.LayoutParams.WRAP_CONTENT
        }
        previewBubbleText.textSize = (13f * scale).coerceIn(10f, 18f)
        previewBubbleText.setPadding(horizontal, vertical, horizontal, vertical)
        previewBubble.requestLayout()

        previewStage.post {
            val petSize = PetUi.dp(this, 72)
            previewPet.layoutParams = (previewPet.layoutParams as FrameLayout.LayoutParams).apply {
                width = petSize
                height = petSize
            }
            previewPet.x = PetUi.dp(this, 16).toFloat()
            previewPet.y = (previewStage.height - petSize - PetUi.dp(this, 18)).coerceAtLeast(0).toFloat()
            previewBubble.post {
                drawable.pointTo(TailEdge.LEFT, previewBubble.height / 2f)
                previewBubble.x = (previewPet.x + petSize + PetUi.dp(this, 6)).coerceAtMost(
                    (previewStage.width - previewBubble.width - PetUi.dp(this, 8)).coerceAtLeast(0).toFloat(),
                )
                previewBubble.y = (previewPet.y + petSize / 2f - previewBubble.height / 2f)
                    .coerceIn(
                        PetUi.dp(this, 8).toFloat(),
                        (previewStage.height - previewBubble.height - PetUi.dp(this, 8)).coerceAtLeast(0).toFloat(),
                    )
            }
        }
    }

    private fun scaleFromProgress(progress: Int): Float = 0.75f + progress.coerceIn(0, 75) / 100f

    private fun progressFromScale(scale: Float): Int =
        ((scale.coerceIn(0.75f, 1.5f) - 0.75f) * 100f).roundToInt()
}
