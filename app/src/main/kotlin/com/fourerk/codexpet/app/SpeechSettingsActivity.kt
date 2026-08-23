package com.fourerk.codexpet.app

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.settings.SpeechStyle
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SpeechSettingsActivity : AppCompatActivity() {
    private lateinit var styleSpinner: Spinner
    private lateinit var styleExample: TextView
    private lateinit var bubbleScaleLabel: TextView
    private lateinit var bubbleScaleSeek: SeekBar
    private lateinit var maxLabel: TextView
    private lateinit var maxSeek: SeekBar
    private lateinit var completionLabel: TextView
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

    private fun buildContent(): View {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PetUi.dp(this@SpeechSettingsActivity, 18), PetUi.dp(this@SpeechSettingsActivity, 20), PetUi.dp(this@SpeechSettingsActivity, 18), PetUi.dp(this@SpeechSettingsActivity, 36))
            setBackgroundColor(PetUi.BACKGROUND)
        }
        body.addView(PetUi.text(this, "Реплики", 28f, PetUi.TEXT, bold = true))
        body.addView(PetUi.text(this, "Пет говорит коротко и по делу. При желании — вообще без перефразирования.", 14f, PetUi.MUTED))

        val voiceCard = PetUi.card(this, "Как говорить")
        styleSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SpeechSettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Живой — по-свойски", "Точный — как в уведомлении"),
            )
        }
        voiceCard.addView(styleSpinner)
        styleExample = PetUi.text(this, "", 13f, PetUi.MUTED)
        voiceCard.addView(styleExample)
        voiceCard.addView(PetUi.text(
            this,
            "Живой режим не пересказывает содержимое обычных сообщений ChatGPT и не придумывает факты: он только заменяет сухой статус понятной вводной и оставляет исходную полезную часть после двоеточия. Точный режим показывает notification-текст как есть.",
            12f,
            PetUi.MUTED,
        ))
        body.addView(voiceCard, PetUi.marginParams(this, 16))

        val bubblesCard = PetUi.card(this, "Баблы")
        bubbleScaleLabel = PetUi.text(this, "Масштаб баблов: 1.00×", 14f, PetUi.TEXT, bold = true)
        bubbleScaleSeek = SeekBar(this).apply { max = 75 }
        bubblesCard.addView(bubbleScaleLabel)
        bubblesCard.addView(bubbleScaleSeek)
        bubblesCard.addView(PetUi.text(
            this,
            "Отдельно от размера пета. Масштабируются ширина, текст, отступы, скругление и хвост реплики; размещение автоматически остаётся в безопасной области экрана.",
            12f,
            PetUi.MUTED,
        ))
        maxLabel = PetUi.text(this, "Одновременно: до 5 реплик", 14f, PetUi.TEXT, bold = true)
        maxSeek = SeekBar(this).apply { max = 4 }
        bubblesCard.addView(maxLabel)
        bubblesCard.addView(maxSeek)
        bubblesCard.addView(PetUi.text(
            this,
            "Если значимых реплик больше лимита, они переключаются страницами. До лимита каждая реплика остаётся отдельным кликабельным баблом.",
            12f,
            PetUi.MUTED,
        ))
        body.addView(bubblesCard, PetUi.marginParams(this))

        val autoCard = PetUi.card(this, "Что показывать автоматически")
        val autoRow = PetUi.toggle(this, "Автоматические реплики", "Главный выключатель. При выключении нажатие на пета всё равно покажет актуальные реплики вручную.")
        autoSwitch = PetUi.switchFrom(autoRow)
        val attentionRow = PetUi.toggle(this, "То, что требует внимания", "Ответ, подтверждение, ошибка, потеря связи — остаются, пока ситуация не изменится.")
        attentionSwitch = PetUi.switchFrom(attentionRow)
        val chatRow = PetUi.toggle(this, "Обычные сообщения ChatGPT", "Показываются как сообщения, а не как Codex-задачи.")
        chatSwitch = PetUi.switchFrom(chatRow)
        val completionRow = PetUi.toggle(this, "Завершение задачи", "Короткая реплика после успешного завершения.")
        completionSwitch = PetUi.switchFrom(completionRow)
        completionLabel = PetUi.text(this, "Готово показывать: 5 сек.", 13f, PetUi.TEXT)
        completionSeek = SeekBar(this).apply { max = 30 }
        autoCard.addView(autoRow)
        autoCard.addView(attentionRow)
        autoCard.addView(chatRow)
        autoCard.addView(completionRow)
        autoCard.addView(completionLabel)
        autoCard.addView(completionSeek)
        body.addView(autoCard, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Открытие чата").apply {
            addView(PetUi.text(
                this@SpeechSettingsActivity,
                "Нажатие на конкретный бабл сначала использует PendingIntent именно этого уведомления ChatGPT. Если точного маршрута в уведомлении нет, приложение честно открывает сам ChatGPT, а не подделывает нестабильную внутреннюю ссылку.",
                13f,
                PetUi.MUTED,
            ))
        }, PetUi.marginParams(this))

        return ScrollView(this).apply { addView(body) }
    }

    private fun bind() {
        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (binding) return
                lifecycleScope.launch {
                    AppGraph.settings.setSpeechStyle(if (position == 0) SpeechStyle.FRIENDLY else SpeechStyle.EXACT)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        bubbleScaleSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) bubbleScaleLabel.text = "Масштаб баблов: ${"%.2f".format(scaleFromProgress(progress))}×"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                lifecycleScope.launch { AppGraph.settings.setBubbleScale(scaleFromProgress(seekBar.progress)) }
            }
        })
        maxSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) maxLabel.text = "Одновременно: до ${progress + 1} реплик"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                lifecycleScope.launch { AppGraph.settings.setMaxVisibleBubbles(seekBar.progress + 1) }
            }
        })
        completionSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) completionLabel.text = "Готово показывать: $progress сек."
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
                AppGraph.settings.settings.collect { settings ->
                    binding = true
                    styleSpinner.setSelection(if (settings.speechStyle == SpeechStyle.FRIENDLY) 0 else 1, false)
                    bubbleScaleSeek.progress = progressFromScale(settings.bubbleScale)
                    bubbleScaleLabel.text = "Масштаб баблов: ${"%.2f".format(settings.bubbleScale)}×"
                    maxSeek.progress = settings.maxVisibleBubbles - 1
                    maxLabel.text = "Одновременно: до ${settings.maxVisibleBubbles} реплик"
                    completionSeek.progress = settings.completedVisibleSeconds
                    completionLabel.text = "Готово показывать: ${settings.completedVisibleSeconds} сек."
                    autoSwitch.isChecked = settings.autoTaskBubblesEnabled
                    attentionSwitch.isChecked = settings.attentionBubblesEnabled
                    chatSwitch.isChecked = settings.chatMessageBubblesEnabled
                    completionSwitch.isChecked = settings.completionBubblesEnabled
                    styleExample.text = if (settings.speechStyle == SpeechStyle.FRIENDLY) {
                        "Пример: «Тут нужен ты: выбери вариант» · «Похоже, связь отвалилась: remote computer offline» · «Есть, сделал: тесты прошли»"
                    } else {
                        "Пример: «Choose an option» · «Remote computer offline» · «All tests pass»"
                    }
                    binding = false
                }
            }
        }
    }

    private fun scaleFromProgress(progress: Int): Float = 0.75f + progress.coerceIn(0, 75) / 100f

    private fun progressFromScale(scale: Float): Int =
        ((scale.coerceIn(0.75f, 1.5f) - 0.75f) * 100f).roundToInt()
}
