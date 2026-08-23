package com.fourerk.codexpet.app

import android.content.Intent
import android.os.Bundle
import android.view.View
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

    private fun buildContent(): View {
        val body = PetUi.page(
            this,
            "Подключение",
            "ChatGPT notifications, listener health и автоматическое восстановление.",
        )

        val statusCard = PetUi.card(this, "Состояние")
        statusText = PetUi.text(this, "Проверяю…", 13f, PetUi.MUTED)
        statusCard.addView(statusText)
        statusCard.addView(PetUi.primaryAction(this, "Проверить и пересинхронизировать") {
            ChatGptNotificationListener.refresh(this)
            Toast.makeText(this, "Сверяю activeNotifications и здоровье listener", Toast.LENGTH_SHORT).show()
        })
        statusCard.addView(PetUi.action(this, "Принудительно переподключить") {
            ChatGptNotificationListener.restart(this)
        })
        body.addView(statusCard, PetUi.marginParams(this, 16))

        val accessCard = PetUi.card(this, "Системные доступы")
        accessCard.addView(PetUi.navigationRow(this, "◉", "Доступ к уведомлениям", "Обязателен для состояния Codex") {
            SystemAccess.openNotificationListenerSettings(this)
        })
        accessCard.addView(PetUi.navigationRow(this, "◫", "Поверх приложений", "Прозрачный pet overlay") {
            SystemAccess.openOverlaySettings(this)
        })
        accessCard.addView(PetUi.navigationRow(this, "●", "Системные bubbles ChatGPT", "Можно отключить белый круг, уведомления оставить") {
            SystemAccess.openChatGptBubbleSettings(this, AppGraph.settings.settings.value.sourcePackage)
        })
        body.addView(accessCard, PetUi.marginParams(this))

        val sourceCard = PetUi.card(this, "Источник ChatGPT")
        sourcePackage = EditText(this).apply {
            setTextColor(PetUi.TEXT)
            setHintTextColor(PetUi.MUTED)
            hint = "com.openai.chatgpt"
            isSingleLine = true
        }
        sourceCard.addView(sourcePackage)
        sourceCard.addView(PetUi.action(this, "Сохранить и пересканировать") {
            lifecycleScope.launch {
                AppGraph.settings.setSourcePackage(sourcePackage.text.toString())
                ChatGptNotificationListener.refresh(this@IntegrationActivity)
            }
        })
        body.addView(sourceCard, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Самовосстановление").apply {
            addView(PetUi.text(
                this@IntegrationActivity,
                "Listener теперь имеет heartbeat. Если activeNotifications несколько раз подряд недоступны, Codex Pet перестаёт доверять ложному состоянию «connected», делает unbind и повторяет requestRebind с backoff до восстановления. Параллельные старые reconcile больше не могут перезаписать свежий snapshot.",
                13f,
                PetUi.MUTED,
            ))
        }, PetUi.marginParams(this))

        if (SystemAccess.isHonorDevice()) {
            body.addView(PetUi.card(this, "HONOR / MagicOS").apply {
                addView(PetUi.text(
                    this@IntegrationActivity,
                    "Для максимальной стабильности отключите автоматическое управление запуском Codex Pet и разрешите автозапуск, косвенный запуск и работу в фоне. Android всё равно может остановить процесс — listener теперь умеет это диагностировать и восстанавливаться.",
                    13f,
                    PetUi.MUTED,
                ))
                addView(PetUi.action(this@IntegrationActivity, "Настройки батареи") {
                    SystemAccess.openBatteryOptimizationSettings(this@IntegrationActivity)
                })
            }, PetUi.marginParams(this))
        }

        body.addView(PetUi.card(this, "Дальше").apply {
            addView(PetUi.navigationRow(this@IntegrationActivity, "⚙", "Фоновое поведение", "Автозапуск и жесты") {
                startActivity(Intent(this@IntegrationActivity, BehaviorActivity::class.java))
            })
            addView(PetUi.navigationRow(this@IntegrationActivity, "?", "Диагностика", "Снимки notification metadata") {
                startActivity(Intent(this@IntegrationActivity, DiagnosticsActivity::class.java))
            })
        }, PetUi.marginParams(this))

        return ScrollView(this).apply { addView(body) }
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
            append(if (SystemAccess.hasNotificationAccess(this@IntegrationActivity)) "✓ Доступ к уведомлениям" else "○ Нет доступа к уведомлениям")
            append(if (listener.connected) "\n✓ Listener отвечает" else "\n○ Listener восстанавливается")
            listener.lastHeartbeatAt?.let {
                append("\nHeartbeat: ${((System.currentTimeMillis() - it) / 1_000L).coerceAtLeast(0L)} сек назад")
            }
            append("\nАктивных ChatGPT notifications: ${listener.activeNotificationCount}")
            append(" · нормализовано: ${tasks.size}")
            append(" · точных переходов: $exact")
            if (listener.consecutiveScanFailures > 0) append("\nОшибок скана подряд: ${listener.consecutiveScanFailures}")
            if (listener.rebindAttempts > 0) append(" · rebind #${listener.rebindAttempts}")
            listener.lastError?.let { append("\nПоследняя ошибка: $it") }
            append(if (SystemAccess.canDrawOverlays(this@IntegrationActivity)) "\n✓ Overlay разрешён" else "\n○ Overlay не разрешён")
        }
    }
}
