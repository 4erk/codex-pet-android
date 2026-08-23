package com.fourerk.codexpet.app

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
        render()
    }

    private fun buildContent(): View {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PetUi.dp(this@IntegrationActivity, 18), PetUi.dp(this@IntegrationActivity, 20), PetUi.dp(this@IntegrationActivity, 18), PetUi.dp(this@IntegrationActivity, 36))
            setBackgroundColor(PetUi.BACKGROUND)
        }
        body.addView(PetUi.text(this, "Подключение", 28f, PetUi.TEXT, bold = true))
        body.addView(PetUi.text(this, "Уведомления ChatGPT, точные переходы в чаты и восстановление синхронизации.", 14f, PetUi.MUTED))

        val statusCard = PetUi.card(this, "Состояние")
        statusText = PetUi.text(this, "Проверяю…", 13f, PetUi.MUTED)
        statusCard.addView(statusText)
        statusCard.addView(PetUi.action(this, "Пересинхронизировать сейчас") {
            ChatGptNotificationListener.refresh(this)
            Toast.makeText(this, "Сверяю полный список активных уведомлений ChatGPT", Toast.LENGTH_SHORT).show()
        })
        statusCard.addView(PetUi.action(this, "Переподключить listener") {
            ChatGptNotificationListener.restart(this)
        })
        body.addView(statusCard, PetUi.marginParams(this, 16))

        val accessCard = PetUi.card(this, "Системные доступы")
        accessCard.addView(PetUi.action(this, "Доступ к уведомлениям") {
            SystemAccess.openNotificationListenerSettings(this)
        })
        accessCard.addView(PetUi.action(this, "Отображение поверх приложений") {
            SystemAccess.openOverlaySettings(this)
        })
        accessCard.addView(PetUi.action(this, "Настройки bubbles ChatGPT") {
            SystemAccess.openChatGptBubbleSettings(this, AppGraph.settings.settings.value.sourcePackage)
        })
        accessCard.addView(PetUi.text(
            this,
            "Системные bubbles ChatGPT лучше отключить, чтобы не было второго белого круга. Сами уведомления ChatGPT оставьте включёнными — из них Codex Pet получает состояние и точные PendingIntent переходов.",
            12f,
            PetUi.MUTED,
        ))
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

        val syncCard = PetUi.card(this, "Если обновления иногда пропадают")
        syncCard.addView(PetUi.text(
            this,
            "Теперь Codex Pet не полагается только на один callback. После posted/removed/ranking-change он делает короткую полную сверку activeNotifications, убирает лишний group summary при наличии реальных children и дополнительно сверяется раз в 10 секунд. После разрыва listener автоматически запрашивает rebind.",
            13f,
            PetUi.MUTED,
        ))
        syncCard.addView(PetUi.text(
            this,
            "Если Android/MagicOS всё равно не отдаёт часть типов уведомлений, проверьте настройки доступа к уведомлениям и фоновые ограничения самого Codex Pet — приложение не может прочитать notification, которое система вообще не передала listener'у.",
            12f,
            PetUi.MUTED,
        ))
        body.addView(syncCard, PetUi.marginParams(this))

        if (SystemAccess.isHonorDevice()) {
            body.addView(PetUi.card(this, "HONOR / MagicOS").apply {
                addView(PetUi.text(
                    this@IntegrationActivity,
                    "Для стабильного listener/overlay отключите автоматическое управление запуском Codex Pet, разрешите автозапуск, косвенный запуск и работу в фоне. В оптимизации батареи выберите режим без ограничения.",
                    13f,
                    PetUi.MUTED,
                ))
                addView(PetUi.action(this@IntegrationActivity, "Настройки батареи") {
                    SystemAccess.openBatteryOptimizationSettings(this@IntegrationActivity)
                })
                addView(PetUi.action(this@IntegrationActivity, "Сведения о Codex Pet") {
                    SystemAccess.openAppDetails(this@IntegrationActivity)
                })
            }, PetUi.marginParams(this))
        }

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
            append(if (listener.connected) "\n✓ Listener подключён" else "\n○ Listener сейчас не подключён")
            append("\nСистема отдаёт активных ChatGPT notifications: ${listener.activeNotificationCount}")
            append("\nПосле нормализации видно элементов: ${tasks.size}")
            append(" · с точным переходом: $exact")
            append(if (SystemAccess.canDrawOverlays(this@IntegrationActivity)) "\n✓ Overlay разрешён" else "\n○ Overlay не разрешён")
        }
    }
}
