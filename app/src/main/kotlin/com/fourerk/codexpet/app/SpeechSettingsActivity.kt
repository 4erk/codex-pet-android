package com.fourerk.codexpet.app

import android.content.Intent
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
        val body = PetUi.page(
            this,
            "Реплики",
            "Что пет говорит, когда всплывает, сколько показывает и как размещает несколько событий.",
        )

        val voiceCard = PetUi.card(this, "Текст")
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
            "Живой режим меняет только короткую вводную, но сохраняет фактический payload уведомления. Обычные сообщения ChatGPT не перефразируются. Точный режим показывает исходный notification-текст.",
            12f,
            PetUi.MUTED,
        ))
        body.addView(voiceCard, PetUi.marginParams(this, 16))

        val bubblesCard = PetUi.card(this, "Вид и плотность")
        bubbleScaleLabel = PetUi.text(this, "Масштаб баблов: 1.00×", 14f, PetUi.TEXT, bold = true)
        bubbleScaleSeek = SeekBar(this).apply { max = 75 }
        bubblesCard.addView(bubbleScaleLabel)
        bubblesCard.addView(bubbleScaleSeek)
        maxLabel = PetUi.text(this, "Одновременно: до 5 реплик", 14f, PetUi.TEXT, bold = true)
        maxSeek = SeekBar(this).apply { max = 4 }
        bubblesCard.addView(maxLabel)
        bubblesCard.addView(maxSeek)
        bubblesCard.addView(PetUi.text(
            this,
            "Размер бабла независим от размера пета. Хвост, ширина, текст и отступы адаптируются вместе; 1–5 значимых реплик показываются одновременно, overflow идёт страницами.",
            12f,
            PetUi.MUTED,
        ))
        bubblesCard.addView(PetUi.primaryAction(this, "Открыть стенд 1–5 баблов") {
            startActivity(Intent(this, BubbleLabActivity::class.java))
        })
        body.addView(bubblesCard, PetUi.marginParams(this))

        val autoCard = PetUi.card(this, "Автоматически")
        val autoRow = PetUi.toggle(this, "Автоматические реплики", "Главный выключатель. Ручной тап по пету всё равно показывает актуальный контекст.")
        autoSwitch = PetUi.switchFrom(autoRow)
        val attentionRow = PetUi.toggle(this, "Требует внимания", "Ответ, разрешение, ошибка и потеря связи остаются, пока ситуация не изменится.")
        attentionSwitch = PetUi.switchFrom(attentionRow)
        val chatRow = PetUi.toggle(this, "Обычные сообщения ChatGPT", "Короткие transient-реплики, которые не считаются Codex-задачами.")
        chatSwitch = PetUi.switchFrom(chatRow)
        val completionRow = PetUi.toggle(this, "Успешное завершение", "Короткий результат; сама анимация успеха проигрывается один раз.")
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

        body.addView(PetUi.card(this, "Приоритет").apply {
            addView(PetUi.text(
                this@SpeechSettingsActivity,
                "Сначала показывается то, где нужен пользователь, затем блокировки/ошибки, затем готовый результат и текущая работа. Attention-события не исчезают по таймеру; завершения и обычные сообщения — исчезают.",
                13f,
                PetUi.MUTED,
            ))
        }, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Переходы").apply {
            addView(PetUi.navigationRow(this@SpeechSettingsActivity, "✦", "Анимации", "Как реплика влияет на реакцию пета") {
                startActivity(Intent(this@SpeechSettingsActivity, AnimationSettingsActivity::class.java))
            })
            addView(PetUi.navigationRow(this@SpeechSettingsActivity, "↗", "Подключение", "Если реплики перестали обновляться") {
                startActivity(Intent(this@SpeechSettingsActivity, IntegrationActivity::class.java))
            })
        }, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Открытие чата").apply {
            addView(PetUi.text(
                this@SpeechSettingsActivity,
                "Тап по конкретному баблу сначала использует PendingIntent именно этого уведомления ChatGPT. Если точного маршрута нет, открывается ChatGPT без подделки нестабильных внутренних URI.",
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
                        "Пример: «Тут нужен ты: выбери вариант» · «Связь потерялась: remote computer offline» · «Есть, сделал: тесты прошли»"
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
