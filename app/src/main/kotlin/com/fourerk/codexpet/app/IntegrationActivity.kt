package com.fourerk.codexpet.app

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.notification.ChatGptNotificationListener
import com.fourerk.codexpet.system.SystemAccess
import com.fourerk.codexpet.task.hasExactOpenTarget
import kotlinx.coroutines.launch

class IntegrationActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var sourcePackage: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        observe()
    }

    override fun onResume() {
        super.onResume()
        if (SystemAccess.hasNotificationAccess(this)) ChatGptNotificationListener.ensureHealthy(this)
        render()
    }

    private fun buildContent(): android.view.View {
        val body = PetUi.page(
            this,
            "Подключение",
            "Получение уведомлений ChatGPT и автоматическое восстановление при сбоях.",
        )

        val statusCard = PetUi.heroCard(this)
        statusCard.addView(PetUi.text(this, "Состояние", 17f, PetUi.TEXT, bold = true))
        statusText = PetUi.text(this, "Проверяю…", 12.5f, PetUi.MUTED).apply {
            setPadding(0, PetUi.dp(this@IntegrationActivity, 8), 0, PetUi.dp(this@IntegrationActivity, 12))
        }
        statusCard.addView(statusText)
        statusCard.addView(PetUi.primaryAction(this, "Проверить") {
            ChatGptNotificationListener.refresh(this)
            Toast.makeText(this, "Проверяю уведомления и подключение", Toast.LENGTH_SHORT).show()
        })
        statusCard.addView(PetUi.action(this, "Переподключить") {
            ChatGptNotificationListener.restart(this)
        }, PetUi.marginParams(this, 8))
        body.addView(statusCard, PetUi.marginParams(this, 16))

        body.addView(PetUi.sectionTitle(this, "Доступы"))
        val accessCard = PetUi.card(this)
        accessCard.addView(PetUi.navigationRow(this, "◉", "Уведомления", "Нужны для состояния задач") {
            SystemAccess.openNotificationListenerSettings(this)
        })
        PetUi.addDivider(accessCard, this)
        accessCard.addView(PetUi.navigationRow(this, "◫", "Поверх окон", "Показ питомца поверх приложений") {
            SystemAccess.openOverlaySettings(this)
        })
        PetUi.addDivider(accessCard, this)
        accessCard.addView(PetUi.navigationRow(this, "●", "Кружок ChatGPT", "Настройки системных пузырей ChatGPT") {
            SystemAccess.openChatGptBubbleSettings(this, AppGraph.settings.settings.value.sourcePackage)
        })
        body.addView(accessCard, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Источник"))
        val sourceCard = PetUi.card(this)
        sourcePackage = EditText(this).apply {
            setTextColor(PetUi.TEXT)
            setHintTextColor(PetUi.MUTED)
            hint = "com.openai.chatgpt"
            isSingleLine = true
            backgroundTintList = android.content.res.ColorStateList.valueOf(PetUi.ACCENT)
        }
        sourceCard.addView(sourcePackage)
        sourceCard.addView(PetUi.action(this, "Сохранить") {
            lifecycleScope.launch {
                AppGraph.settings.setSourcePackage(sourcePackage.text.toString())
                ChatGptNotificationListener.refresh(this@IntegrationActivity)
            }
        }, PetUi.marginParams(this, 8))
        sourceCard.addView(PetUi.helper(this, "Обычно менять не нужно. По умолчанию используется официальное приложение ChatGPT."))
        body.addView(sourceCard, PetUi.marginParams(this, 4))

        if (SystemAccess.isHonorDevice()) {
            body.addView(PetUi.sectionTitle(this, "HONOR / MagicOS"))
            body.addView(PetUi.card(this).apply {
                addView(PetUi.helper(this@IntegrationActivity, "Для надёжной работы разрешите автозапуск и работу в фоне. Если система всё же остановит подключение, Codex Pet попытается восстановить его автоматически."))
                addView(PetUi.navigationRow(this@IntegrationActivity, "◷", "Батарея", "Ограничения фоновой работы") {
                    SystemAccess.openBatteryOptimizationSettings(this@IntegrationActivity)
                })
            }, PetUi.marginParams(this, 4))
        }

        body.addView(PetUi.sectionTitle(this, "Связано"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@IntegrationActivity, "⌁", "Поведение", "Запуск и работа в фоне") {
                startActivity(Intent(this@IntegrationActivity, BehaviorActivity::class.java))
            })
            PetUi.addDivider(this, this@IntegrationActivity)
            addView(PetUi.navigationRow(this@IntegrationActivity, "?", "Диагностика", "Подробности о полученных уведомлениях") {
                startActivity(Intent(this@IntegrationActivity, DiagnosticsActivity::class.java))
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
                launch { AppGraph.settings.settings.collect { render() } }
                launch { AppGraph.tasks.tasks.collect { render() } }
                launch { AppGraph.diagnostics.listener.collect { render() } }
            }
        }
    }

    private fun render() {
        if (!::statusText.isInitialized) return
        val settings = AppGraph.settings.settings.value
        val listener = AppGraph.diagnostics.listener.value
        val tasks = AppGraph.tasks.tasks.value
        if (!sourcePackage.hasFocus()) sourcePackage.setText(settings.sourcePackage)
        val exact = tasks.count { it.hasExactOpenTarget() }
        statusText.text = buildString {
            append(if (SystemAccess.hasNotificationAccess(this@IntegrationActivity)) "Уведомления: разрешены" else "Уведомления: нет доступа")
            append(if (listener.connected) "\nПодключение: работает" else "\nПодключение: восстанавливается")
            listener.lastHeartbeatAt?.let {
                append("\nПоследняя успешная проверка: ${((System.currentTimeMillis() - it) / 1_000L).coerceAtLeast(0L)} сек назад")
            }
            append("\nУведомлений ChatGPT: ${listener.activeNotificationCount}")
            append(" · задач: ${tasks.size}")
            append(" · точных переходов: $exact")
            if (listener.consecutiveScanFailures > 0) append("\nОшибок подряд: ${listener.consecutiveScanFailures}")
            if (listener.rebindAttempts > 0) append(" · попыток переподключения: ${listener.rebindAttempts}")
            listener.lastError?.let { append("\nПоследняя ошибка: $it") }
            append(if (SystemAccess.canDrawOverlays(this@IntegrationActivity)) "\nПоверх окон: разрешено" else "\nПоверх окон: нет доступа")
        }
    }
}
