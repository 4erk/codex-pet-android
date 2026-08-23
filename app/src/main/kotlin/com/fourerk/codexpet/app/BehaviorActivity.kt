package com.fourerk.codexpet.app

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.settings.LongPressAction
import com.fourerk.codexpet.system.SystemAccess
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class BehaviorActivity : AppCompatActivity() {
    private lateinit var autoStartSwitch: SwitchMaterial
    private lateinit var longPressSegments: LinearLayout
    private var binding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        observe()
    }

    private fun buildContent(): android.view.View {
        val body = PetUi.page(
            this,
            "Поведение",
            "Жесты, запуск после перезагрузки и работа в фоне.",
        )

        body.addView(PetUi.sectionTitle(this, "Запуск"))
        val launchCard = PetUi.card(this)
        val autoRow = PetUi.toggle(this, "После перезагрузки", "Пытаться восстановить питомца после запуска телефона.")
        autoStartSwitch = PetUi.switchFrom(autoRow)
        autoStartSwitch.setOnCheckedChangeListener { _, checked ->
            if (!binding) lifecycleScope.launch { AppGraph.settings.setAutoStart(checked) }
        }
        launchCard.addView(autoRow)
        PetUi.addDivider(launchCard, this)
        launchCard.addView(PetUi.navigationRow(this, "◷", "Фоновая работа", "Настройки батареи и ограничений системы") {
            SystemAccess.openBatteryOptimizationSettings(this)
        })
        body.addView(launchCard, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Долгое нажатие"))
        val gestureCard = PetUi.card(this)
        longPressSegments = PetUi.segmented(
            this,
            labels = listOf("Меню", "ChatGPT", "Скрыть"),
            selected = 0,
        ) { position ->
            if (!binding) {
                val value = LongPressAction.entries.getOrElse(position) { LongPressAction.MENU }
                lifecycleScope.launch { AppGraph.settings.setLongPressAction(value) }
            }
        }
        gestureCard.addView(longPressSegments)
        gestureCard.addView(PetUi.helper(this, "Короткий тап показывает реплики. Перетаскивание двигает питомца. Новое касание сразу отменяет движение к краю."))
        body.addView(gestureCard, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Связано"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@BehaviorActivity, "↗", "Подключение", "Уведомления и восстановление связи") {
                startActivity(Intent(this@BehaviorActivity, IntegrationActivity::class.java))
            })
            PetUi.addDivider(this, this@BehaviorActivity)
            addView(PetUi.navigationRow(this@BehaviorActivity, "⇩", "Обновления", "Проверка и установка новых версий") {
                startActivity(Intent(this@BehaviorActivity, UpdatesActivity::class.java))
            })
            PetUi.addDivider(this, this@BehaviorActivity)
            addView(PetUi.navigationRow(this@BehaviorActivity, "◉", "Питомец", "Размер и положение") {
                startActivity(Intent(this@BehaviorActivity, PetSettingsActivity::class.java))
            })
        }, PetUi.marginParams(this, 4))

        return ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(body)
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppGraph.settings.settings.collect { settings ->
                    binding = true
                    autoStartSwitch.isChecked = settings.autoStart
                    PetUi.setSegmentedSelection(longPressSegments, settings.longPressAction.ordinal)
                    binding = false
                }
            }
        }
    }
}
