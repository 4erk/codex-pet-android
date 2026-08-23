package com.fourerk.codexpet.app

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
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PetUi.dp(this@BehaviorActivity, 18), PetUi.dp(this@BehaviorActivity, 20), PetUi.dp(this@BehaviorActivity, 18), PetUi.dp(this@BehaviorActivity, 36))
            setBackgroundColor(PetUi.BACKGROUND)
        }
        body.addView(PetUi.text(this, "Поведение", 28f, PetUi.TEXT, bold = true))
        body.addView(PetUi.text(this, "Жесты, запуск и то, как пет ведёт себя в фоне.", 14f, PetUi.MUTED))

        val launchCard = PetUi.card(this, "Запуск")
        val autoRow = PetUi.toggle(this, "Запускать после перезагрузки", "Работает только если системные разрешения уже выданы.")
        autoStartSwitch = PetUi.switchFrom(autoRow)
        launchCard.addView(autoRow)
        launchCard.addView(PetUi.action(this, "Настройки фоновой работы") {
            SystemAccess.openBatteryOptimizationSettings(this)
        })
        body.addView(launchCard, PetUi.marginParams(this, 16))

        val gestureCard = PetUi.card(this, "Долгое нажатие на пета")
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
            "Быстрое меню содержит только повседневные действия: текущий чат, настройки и скрытие. Диагностика вынесена в раздел помощи, чтобы не попадаться в обычном использовании.",
            12f,
            PetUi.MUTED,
        ))
        body.addView(gestureCard, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Жесты").apply {
            addView(PetUi.text(
                this@BehaviorActivity,
                "Нажатие — показать/скрыть реплики. Перетаскивание — передвинуть пета. Во время движения проигрывается бег в соответствующую сторону, после отпускания возвращается реальное состояние задачи.",
                13f,
                PetUi.MUTED,
            ))
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
