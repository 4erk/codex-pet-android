package com.fourerk.codexpet.app

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
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PetUi.dp(this@PetSettingsActivity, 18), PetUi.dp(this@PetSettingsActivity, 20), PetUi.dp(this@PetSettingsActivity, 18), PetUi.dp(this@PetSettingsActivity, 36))
            setBackgroundColor(PetUi.BACKGROUND)
        }
        body.addView(PetUi.text(this, "Питомец", 28f, PetUi.TEXT, bold = true))
        body.addView(PetUi.text(this, "Внешность, размер и pet pack. Техническая диагностика отсюда убрана.", 14f, PetUi.MUTED))

        val petCard = PetUi.card(this, "Violet Vixen").apply {
            preview = ImageView(this@PetSettingsActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Предпросмотр питомца"
            }
            addView(preview, LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this@PetSettingsActivity, 190))
            sourceText = PetUi.text(this@PetSettingsActivity, "Загружаю…", 13f, PetUi.MUTED)
            addView(sourceText)
            addView(PetUi.action(this@PetSettingsActivity, "Импортировать ZIP / PNG / WebP") {
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
            addView(PetUi.action(this@PetSettingsActivity, "Проверить питомца из ChatGPT") {
                ChatGptNotificationListener.refresh(this@PetSettingsActivity)
            })
            addView(PetUi.text(
                this@PetSettingsActivity,
                "Для полной анимации лучше ZIP или spritesheet. Статичная картинка тоже поддерживается. Автоподхват из ChatGPT остаётся fallback и не заменяет полный pack одним случайным кадром.",
                12f,
                PetUi.MUTED,
            ))
        }
        body.addView(petCard, PetUi.marginParams(this, 16))

        val sizeCard = PetUi.card(this, "На экране")
        sizeLabel = PetUi.text(this, "Размер: 72 dp", 14f, PetUi.TEXT, bold = true)
        sizeSeek = SeekBar(this).apply { max = 112 }
        val snapRow = PetUi.toggle(this, "Прилипать к ближайшему краю", "После перетаскивания пет аккуратно уезжает к краю экрана.")
        snapSwitch = PetUi.switchFrom(snapRow)
        sizeCard.addView(sizeLabel)
        sizeCard.addView(sizeSeek)
        sizeCard.addView(snapRow)
        body.addView(sizeCard, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Подсказка").apply {
            addView(PetUi.text(
                this@PetSettingsActivity,
                "Нажатие на пета показывает его реплики. Долгое нажатие — быстрое меню. Перетаскивание не открывает чат и не мешает текущей анимации задачи после отпускания.",
                13f,
                PetUi.MUTED,
            ))
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
