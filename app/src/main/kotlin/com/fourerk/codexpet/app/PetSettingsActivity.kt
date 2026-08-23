package com.fourerk.codexpet.app

import android.content.Intent
import android.os.Bundle
import android.view.View
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
    private lateinit var preview: ImageView
    private lateinit var sourceText: TextView
    private lateinit var sizeLabel: TextView
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
                    Toast.makeText(this@PetSettingsActivity, "Не удалось импортировать: ${it.message}", Toast.LENGTH_LONG).show()
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
            "Внешность, размер, позиция и полный анимационный pack.",
        )

        val petCard = PetUi.card(this, "Violet Vixen").apply {
            preview = ImageView(this@PetSettingsActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Предпросмотр питомца"
            }
            addView(preview, LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this@PetSettingsActivity, 190))
            sourceText = PetUi.text(this@PetSettingsActivity, "Загружаю…", 13f, PetUi.MUTED)
            addView(sourceText)
            addView(PetUi.primaryAction(this@PetSettingsActivity, "Импортировать ZIP / PNG / WebP") {
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
            addView(PetUi.action(this@PetSettingsActivity, "Перепроверить питомца из ChatGPT") {
                ChatGptNotificationListener.refresh(this@PetSettingsActivity)
            })
            addView(PetUi.text(
                this@PetSettingsActivity,
                "Для полной реакции нужен ZIP/spritesheet. Автоподхват из notification icon остаётся fallback и не должен заменять полный pack одиночным случайным кадром.",
                12f,
                PetUi.MUTED,
            ))
        }
        body.addView(petCard, PetUi.marginParams(this, 16))

        val sizeCard = PetUi.card(this, "На экране")
        sizeLabel = PetUi.text(this, "Размер: 72 dp", 14f, PetUi.TEXT, bold = true)
        sizeSeek = SeekBar(this).apply { max = 112 }
        val snapRow = PetUi.toggle(this, "Прилипать к ближайшему краю", "Snap проигрывает бег в нужную сторону и не пересоздаёт реплики.")
        snapSwitch = PetUi.switchFrom(snapRow)
        sizeCard.addView(sizeLabel)
        sizeCard.addView(sizeSeek)
        sizeCard.addView(snapRow)
        body.addView(sizeCard, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Рядом с питомцем").apply {
            addView(PetUi.navigationRow(this@PetSettingsActivity, "💬", "Реплики", "Размер и количество баблов независимы от размера пета") {
                startActivity(Intent(this@PetSettingsActivity, SpeechSettingsActivity::class.java))
            })
            addView(PetUi.navigationRow(this@PetSettingsActivity, "✦", "Анимации", "Проверить все 9 состояний текущего pack") {
                startActivity(Intent(this@PetSettingsActivity, AnimationSettingsActivity::class.java))
            })
            addView(PetUi.navigationRow(this@PetSettingsActivity, "◫", "Стенд баблов", "Потаскать пета с 1–5 репликами") {
                startActivity(Intent(this@PetSettingsActivity, BubbleLabActivity::class.java))
            })
        }, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Жесты").apply {
            addView(PetUi.text(
                this@PetSettingsActivity,
                "Тап — показать/скрыть реплики. Долгое нажатие — быстрое меню. Drag не открывает чат; после отпускания пет возвращается к фактическому состоянию задачи.",
                13f,
                PetUi.MUTED,
            ))
            addView(PetUi.navigationRow(this@PetSettingsActivity, "⚙", "Настроить жесты", "Долгое нажатие и автозапуск") {
                startActivity(Intent(this@PetSettingsActivity, BehaviorActivity::class.java))
            })
        }, PetUi.marginParams(this))

        return ScrollView(this).apply { addView(body) }
    }

    private fun bind() {
        sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) sizeLabel.text = "Размер: ${progress + 48} dp"
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
                        sizeLabel.text = "Размер: ${settings.petSizeDp} dp"
                        snapSwitch.isChecked = settings.snapEnabled
                        binding = false
                    }
                }
                launch {
                    AppGraph.pets.visual.collect { pet ->
                        preview.setImageBitmap(pet?.bitmap)
                        sourceText.text = when {
                            pet == null -> "Питомец пока не загружен"
                            pet.source == PetSource.BUILT_IN -> "Встроенный pack · ${pet.frameSequences.size}/9 анимаций"
                            pet.frameSequences.isNotEmpty() -> "Импортированный pack · ${pet.frameSequences.size}/9 анимаций"
                            else -> "Статичное изображение · прозрачность ${if (pet.hasMeaningfulTransparency) "есть" else "ограничена"}"
                        }
                    }
                }
            }
        }
    }
}
