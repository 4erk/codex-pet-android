package com.fourerk.codexpet.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.notification.ChatGptNotificationListener
import com.fourerk.codexpet.pet.PetSource
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class PetSettingsActivity : AppCompatActivity() {
    private lateinit var previewStage: FrameLayout
    private lateinit var preview: ImageView
    private lateinit var sourceText: TextView
    private lateinit var sizeValue: TextView
    private lateinit var sizeSeek: SeekBar
    private lateinit var snapSwitch: SwitchMaterial
    private var binding = false

    private val importPet = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            AppGraph.pets.importManual(uri)
                .onSuccess { pet ->
                    val type = if (pet.frameSequences.isNotEmpty()) "pet pack" else "изображение"
                    Toast.makeText(this@PetSettingsActivity, "Импортирован $type", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(this@PetSettingsActivity, "Импорт не удался: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        bind()
        observe()
    }

    private fun buildContent(): View {
        val body = PetUi.page(
            this,
            "Питомец",
            "Внешность, реальный размер на экране и поведение overlay.",
        )

        val hero = PetUi.heroCard(this)
        hero.addView(PetUi.text(this, "Предпросмотр", 17f, PetUi.TEXT, bold = true))
        sourceText = PetUi.text(this, "Загружаю…", 12f, PetUi.MUTED).apply {
            setPadding(0, PetUi.dp(this@PetSettingsActivity, 4), 0, PetUi.dp(this@PetSettingsActivity, 12))
        }
        hero.addView(sourceText)

        previewStage = PetUi.previewSurface(this)
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Предпросмотр размера питомца"
        }
        previewStage.addView(preview, FrameLayout.LayoutParams(PetUi.dp(this, 72), PetUi.dp(this, 72)))
        hero.addView(previewStage, LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this, 238))

        val sizeHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, PetUi.dp(this@PetSettingsActivity, 14), 0, 0)
            addView(
                PetUi.text(this@PetSettingsActivity, "Размер", 14.5f, PetUi.TEXT, bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            sizeValue = PetUi.valuePill(this@PetSettingsActivity, "72 dp")
            addView(sizeValue)
        }
        hero.addView(sizeHeader)
        sizeSeek = SeekBar(this).apply { max = 112 }
        hero.addView(sizeSeek)
        hero.addView(PetUi.helper(this, "Размер меняется прямо в предпросмотре и соответствует размеру overlay в dp."))
        body.addView(hero, PetUi.marginParams(this, 16))

        body.addView(PetUi.sectionTitle(this, "Образ"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.primaryAction(this@PetSettingsActivity, "Импорт") {
                importPet.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "image/png",
                        "image/webp",
                        "image/*",
                    ),
                )
            })
            addView(PetUi.action(this@PetSettingsActivity, "Обновить из ChatGPT") {
                ChatGptNotificationListener.refresh(this@PetSettingsActivity)
            }, PetUi.marginParams(this@PetSettingsActivity, 8))
            addView(PetUi.helper(this@PetSettingsActivity, "ZIP/spritesheet — основной источник полного pack. Notification icon остаётся только fallback."))
        }, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "На экране"))
        val screen = PetUi.card(this)
        val snapRow = PetUi.toggle(this, "Прилипать к краю", "После drag пет плавно доезжает до ближайшего края; баблы остаются прикреплены.")
        snapSwitch = PetUi.switchFrom(snapRow)
        screen.addView(snapRow)
        PetUi.addDivider(screen, this)
        screen.addView(PetUi.navigationRow(this, "⌁", "Поведение", "Жесты, автозапуск и фон") {
            startActivity(Intent(this@PetSettingsActivity, BehaviorActivity::class.java))
        })
        body.addView(screen, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Связано"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@PetSettingsActivity, "◰", "Реплики", "Масштаб и живой preview бабла") {
                startActivity(Intent(this@PetSettingsActivity, SpeechSettingsActivity::class.java))
            })
            PetUi.addDivider(this, this@PetSettingsActivity)
            addView(PetUi.navigationRow(this@PetSettingsActivity, "✦", "Анимации", "Состояния и сценарии") {
                startActivity(Intent(this@PetSettingsActivity, AnimationSettingsActivity::class.java))
            })
            PetUi.addDivider(this, this@PetSettingsActivity)
            addView(PetUi.navigationRow(this@PetSettingsActivity, "▱", "Стенд", "Проверка 1–5 баблов") {
                startActivity(Intent(this@PetSettingsActivity, BubbleLabActivity::class.java))
            })
        }, PetUi.marginParams(this, 4))

        return ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(body)
        }
    }

    private fun bind() {
        sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val size = progress + 48
                sizeValue.text = "$size dp"
                renderPetPreview(size)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                lifecycleScope.launch { AppGraph.settings.setPetSizeDp(seekBar.progress + 48) }
            }
        })
        snapSwitch.setOnCheckedChangeListener { _, checked ->
            if (!binding) lifecycleScope.launch { AppGraph.settings.setSnapEnabled(checked) }
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AppGraph.settings.settings.collect { settings ->
                        binding = true
                        sizeSeek.progress = settings.petSizeDp - 48
                        sizeValue.text = "${settings.petSizeDp} dp"
                        snapSwitch.isChecked = settings.snapEnabled
                        binding = false
                        renderPetPreview(settings.petSizeDp)
                    }
                }
                launch {
                    AppGraph.pets.visual.collect { pet ->
                        preview.setImageBitmap(pet?.bitmap)
                        sourceText.text = when {
                            pet == null -> "Питомец не загружен"
                            pet.source == PetSource.BUILT_IN -> "Встроенный pack · ${pet.frameSequences.size}/9 анимаций"
                            pet.frameSequences.isNotEmpty() -> "Импортированный pack · ${pet.frameSequences.size}/9 анимаций"
                            else -> "Статичное изображение"
                        }
                    }
                }
            }
        }
    }

    private fun renderPetPreview(sizeDp: Int) {
        if (!::previewStage.isInitialized) return
        val size = PetUi.dp(this, sizeDp.coerceIn(48, 160))
        preview.layoutParams = (preview.layoutParams as FrameLayout.LayoutParams).apply {
            width = size
            height = size
        }
        previewStage.post {
            preview.x = ((previewStage.width - size) / 2f).coerceAtLeast(0f)
            preview.y = ((previewStage.height - size) / 2f).coerceAtLeast(0f)
        }
    }
}
