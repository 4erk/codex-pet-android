package com.fourerk.codexpet.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
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
    private lateinit var longPressSpinner: Spinner
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
            "Поведение",
            "Запуск, жесты и работа поверх других приложений.",
        )

        val launchCard = PetUi.card(this, "Запуск")
        val autoRow = PetUi.toggle(this, "Запускать после перезагрузки", "Best effort: Android/MagicOS всё равно может ограничить background FGS.")
        autoStartSwitch = PetUi.switchFrom(autoRow)
        launchCard.addView(autoRow)
        launchCard.addView(PetUi.action(this, "Настройки фоновой работы") {
            SystemAccess.openBatteryOptimizationSettings(this)
        })
        body.addView(launchCard, PetUi.marginParams(this, 16))

        val gestureCard = PetUi.card(this, "Долгое нажатие")
        longPressSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@BehaviorActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Быстрое меню", "Сразу открыть ChatGPT", "Скрыть питомца"),
            )
        }
        gestureCard.addView(longPressSpinner)
        gestureCard.addView(PetUi.text(
            this,
            "Короткий тап — реплики. Drag — перемещение. Snap использует бег к краю и отменяется новым касанием. После движения всегда восстанавливается реальное состояние задачи.",
            12f,
            PetUi.MUTED,
        ))
        body.addView(gestureCard, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Связано с фоном").apply {
            addView(PetUi.navigationRow(this@BehaviorActivity, "↗", "Подключение", "Listener health, heartbeat и MagicOS recovery") {
                startActivity(Intent(this@BehaviorActivity, IntegrationActivity::class.java))
            })
            addView(PetUi.navigationRow(this@BehaviorActivity, "⇩", "Обновления", "Автопроверка stable GitHub Releases") {
                startActivity(Intent(this@BehaviorActivity, UpdatesActivity::class.java))
            })
            addView(PetUi.navigationRow(this@BehaviorActivity, "🐾", "Питомец", "Размер, позиция и snap") {
                startActivity(Intent(this@BehaviorActivity, PetSettingsActivity::class.java))
            })
        }, PetUi.marginParams(this))

        return ScrollView(this).apply { addView(body) }
    }

    private fun bind() {
        autoStartSwitch.setOnCheckedChangeListener { _, checked ->
            if (!binding) lifecycleScope.launch { AppGraph.settings.setAutoStart(checked) }
        }
        longPressSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (binding) return
                val value = LongPressAction.entries.getOrElse(position) { LongPressAction.MENU }
                lifecycleScope.launch { AppGraph.settings.setLongPressAction(value) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppGraph.settings.settings.collect { settings ->
                    binding = true
                    autoStartSwitch.isChecked = settings.autoStart
                    longPressSpinner.setSelection(settings.longPressAction.ordinal, false)
                    binding = false
                }
            }
        }
    }
}
