package com.fourerk.codexpet.app

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.overlay.OverlayService
import com.fourerk.codexpet.pet.PetAnimationCatalog
import com.fourerk.codexpet.pet.PetAnimationState
import com.fourerk.codexpet.pet.PetFrameSequence
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AnimationSettingsActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var preview: ImageView
    private lateinit var statusText: TextView
    private lateinit var enabledSwitch: SwitchMaterial
    private lateinit var speedValue: TextView
    private lateinit var speedSeek: SeekBar
    private var binding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        bind()
        observe()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        (preview.drawable as? Animatable)?.stop()
        super.onDestroy()
    }

    private fun buildContent(): View {
        val body = PetUi.page(
            this,
            "Анимации",
            "Питомец выбирает реакцию по смыслу происходящего и текущему состоянию задачи.",
        )

        val previewCard = PetUi.heroCard(this)
        previewCard.addView(PetUi.text(this, "Предпросмотр", 17f, PetUi.TEXT, bold = true))
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Предпросмотр анимации"
        }
        previewCard.addView(preview, LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this, 190))
        statusText = PetUi.text(this, "Загружаю набор питомца…", 12f, PetUi.MUTED)
        previewCard.addView(statusText)

        val enabledRow = PetUi.toggle(this, "Анимации", "Если системные анимации Android отключены, показывается статичный кадр.")
        enabledSwitch = PetUi.switchFrom(enabledRow)
        previewCard.addView(enabledRow)
        PetUi.addDivider(previewCard, this)

        val speedHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(PetUi.dp(this@AnimationSettingsActivity, 2), PetUi.dp(this@AnimationSettingsActivity, 12), 0, 0)
            addView(
                PetUi.text(this@AnimationSettingsActivity, "Скорость", 14.5f, PetUi.TEXT, bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            speedValue = PetUi.valuePill(this@AnimationSettingsActivity, "1.00×")
            addView(speedValue)
        }
        previewCard.addView(speedHeader)
        speedSeek = SeekBar(this).apply { max = 150 }
        previewCard.addView(speedSeek)
        body.addView(previewCard, PetUi.marginParams(this, 16))

        body.addView(PetUi.sectionTitle(this, "Как выбирается реакция"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.helper(
                this@AnimationSettingsActivity,
                "Приоритет: нужен ответ пользователя → ошибка или потеря связи → восстановление связи → проверка → обычная работа → покой. Завершение, новое сообщение и восстановление связи дают короткую отдельную реакцию.",
            ))
            addView(PetUi.helper(
                this@AnimationSettingsActivity,
                "Явные признаки состояния задачи важнее случайных слов. Например, сообщение об ошибке не станет обычной работой только из-за слова «собираю».",
            ))
        }, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Состояния"))
        PetAnimationCatalog.all.forEach { descriptor ->
            val card = PetUi.card(this, descriptor.title)
            card.addView(PetUi.text(this, descriptor.whenShort, 12f, PetUi.MUTED))
            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            buttons.addView(
                PetUi.action(this, "Проверить") { testState(descriptor.state) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            buttons.addView(
                PetUi.action(this, "Условия") {
                    AlertDialog.Builder(this)
                        .setTitle(descriptor.title)
                        .setMessage(descriptor.fullConditions)
                        .setPositiveButton("Понятно", null)
                        .show()
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            card.addView(buttons, PetUi.marginParams(this, 9))
            body.addView(card, PetUi.marginParams(this, 8))
        }

        body.addView(PetUi.sectionTitle(this, "Связано"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@AnimationSettingsActivity, "◰", "Реплики", "Какие события показываются рядом с питомцем") {
                startActivity(Intent(this@AnimationSettingsActivity, SpeechSettingsActivity::class.java))
            })
            PetUi.addDivider(this, this@AnimationSettingsActivity)
            addView(PetUi.navigationRow(this@AnimationSettingsActivity, "▱", "Стенд", "Проверка 1–5 реплик") {
                startActivity(Intent(this@AnimationSettingsActivity, BubbleLabActivity::class.java))
            })
            PetUi.addDivider(this, this@AnimationSettingsActivity)
            addView(PetUi.navigationRow(this@AnimationSettingsActivity, "↗", "Подключение", "Источник состояния и восстановление") {
                startActivity(Intent(this@AnimationSettingsActivity, IntegrationActivity::class.java))
            })
        }, PetUi.marginParams(this, 4))

        return ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(body)
        }
    }

    private fun bind() {
        enabledSwitch.setOnCheckedChangeListener { _, checked ->
            if (!binding) lifecycleScope.launch { AppGraph.settings.setAnimationsEnabled(checked) }
        }
        speedSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val speed = (progress + 50) / 100f
                speedValue.text = "${"%.2f".format(speed)}×"
                // Live preview: the selected speed is visible immediately, save on release.
                renderIdle(previewSpeed = speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                lifecycleScope.launch { AppGraph.settings.setAnimationSpeed((seekBar.progress + 50) / 100f) }
            }
        })
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AppGraph.settings.settings.collect { settings ->
                        binding = true
                        enabledSwitch.isChecked = settings.animationsEnabled
                        speedSeek.progress = (settings.animationSpeed * 100).roundToInt() - 50
                        speedValue.text = "${"%.2f".format(settings.animationSpeed)}×"
                        binding = false
                        renderIdle()
                    }
                }
                launch { AppGraph.pets.visual.collect { renderIdle() } }
            }
        }
    }

    private fun testState(state: PetAnimationState) {
        val pet = AppGraph.pets.visual.value
        val sequence = pet?.frameSequences?.get(state)
        if (sequence == null) {
            Toast.makeText(this, "В текущем наборе нет этой анимации", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            Toast.makeText(this, "Системные анимации Android отключены", Toast.LENGTH_LONG).show()
        }
        showSequence(sequence, oneShot = false)
        statusText.text = "Проверка: ${PetAnimationCatalog.descriptor(state)?.title ?: state.name}"
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(::renderIdle, 2_200L)

        if (AppGraph.settings.settings.value.overlayEnabled) {
            startService(
                Intent(this, OverlayService::class.java)
                    .setAction(OverlayService.ACTION_PREVIEW_ANIMATION)
                    .putExtra(OverlayService.EXTRA_ANIMATION_STATE, state.name),
            )
        }
    }

    private fun renderIdle(previewSpeed: Float? = null) {
        if (!::preview.isInitialized) return
        val pet = AppGraph.pets.visual.value
        val settings = AppGraph.settings.settings.value
        (preview.drawable as? Animatable)?.stop()
        if (pet == null) {
            preview.setImageDrawable(null)
            statusText.text = "Набор питомца пока не загружен"
            return
        }
        val idle = pet.frameSequences[PetAnimationState.IDLE]
        if (idle != null && settings.animationsEnabled && ValueAnimator.areAnimatorsEnabled()) {
            showSequence(idle, oneShot = false, speedOverride = previewSpeed)
        } else {
            preview.setImageBitmap(pet.bitmap)
        }
        statusText.text = buildString {
            append("Доступно ${pet.frameSequences.size}/9 состояний")
            if (pet.lookDirections.size == 16) append(" · 16 направлений взгляда")
            if (!settings.animationsEnabled) append(" · выключено")
        }
    }

    private fun showSequence(
        sequence: PetFrameSequence,
        oneShot: Boolean,
        speedOverride: Float? = null,
    ) {
        (preview.drawable as? Animatable)?.stop()
        val speed = (speedOverride ?: AppGraph.settings.settings.value.animationSpeed).coerceIn(0.5f, 2f)
        val animation = AnimationDrawable().apply {
            isOneShot = oneShot
            sequence.frames.forEachIndexed { index, bitmap ->
                addFrame(
                    bitmap.toDrawable(resources).apply { isFilterBitmap = true },
                    (sequence.frameDurationsMs.getOrElse(index) { 150 } / speed).roundToInt().coerceAtLeast(40),
                )
            }
        }
        preview.setImageDrawable(animation)
        preview.post(animation::start)
    }
}
