package com.fourerk.codexpet.app

import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.fourerk.codexpet.BuildConfig
import com.fourerk.codexpet.notification.ChatGptNotificationListener
import com.fourerk.codexpet.system.SystemAccess

class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    private fun buildContent(): android.view.View {
        val body = PetUi.page(
            this,
            "Помощь",
            "Быстрые маршруты для восстановления работы и проверки конкретного слоя.",
        )

        body.addView(PetUi.card(this, "Как начать").apply {
            addView(PetUi.text(
                this@HelpActivity,
                "1. Разрешите чтение уведомлений и overlay.\n2. Оставьте обычные notifications ChatGPT включёнными.\n3. Запустите Codex Pet.\n4. Тап по реплике использует точный PendingIntent, если ChatGPT его передал.",
                13f,
                PetUi.MUTED,
            ))
        }, PetUi.marginParams(this, 16))

        body.addView(PetUi.card(this, "Если перестало обновляться").apply {
            addView(PetUi.text(
                this@HelpActivity,
                "Listener теперь восстанавливается сам: проверяет heartbeat, activeNotifications и после повторных ошибок делает controlled rebind. Если нужно ускорить проверку — используйте действия ниже.",
                13f,
                PetUi.MUTED,
            ))
            addView(PetUi.primaryAction(this@HelpActivity, "Проверить activeNotifications") {
                ChatGptNotificationListener.refresh(this@HelpActivity)
            })
            addView(PetUi.action(this@HelpActivity, "Перезапустить listener") {
                ChatGptNotificationListener.restart(this@HelpActivity)
            })
            addView(PetUi.navigationRow(this@HelpActivity, "↗", "Открыть подключение", "Heartbeat, rebind и настройки MagicOS") {
                startActivity(Intent(this@HelpActivity, IntegrationActivity::class.java))
            })
        }, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Проверить внешний вид").apply {
            addView(PetUi.navigationRow(this@HelpActivity, "◫", "Стенд 1–5 баблов", "Без реальных уведомлений") {
                startActivity(Intent(this@HelpActivity, BubbleLabActivity::class.java))
            })
            addView(PetUi.navigationRow(this@HelpActivity, "✦", "Все анимации", "Триггеры и ручной тест") {
                startActivity(Intent(this@HelpActivity, AnimationSettingsActivity::class.java))
            })
        }, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Обновления и сеть").apply {
            addView(PetUi.text(
                this@HelpActivity,
                "Основная работа пета остаётся локальной: notification-тексты не отправляются на сервер. INTERNET используется только для проверки публичного GitHub Releases API и скачивания stable APK. Перед установкой проверяются GitHub SHA-256, package name и сертификат подписи.",
                13f,
                PetUi.MUTED,
            ))
            addView(PetUi.navigationRow(this@HelpActivity, "⇩", "Обновления", "Stable channel и параметры автозагрузки") {
                startActivity(Intent(this@HelpActivity, UpdatesActivity::class.java))
            })
        }, PetUi.marginParams(this))

        body.addView(PetUi.sectionTitle(this, "Расширенное"))
        body.addView(PetUi.card(this, "Диагностика").apply {
            addView(PetUi.text(
                this@HelpActivity,
                "Для разбора конкретной версии ChatGPT/Android. Экспорт остаётся санитизированным; release-build не экспортирует полный текст уведомлений.",
                12f,
                PetUi.MUTED,
            ))
            addView(PetUi.action(this@HelpActivity, "Открыть диагностику уведомлений") {
                startActivity(Intent(this@HelpActivity, DiagnosticsActivity::class.java))
            })
            addView(PetUi.action(this@HelpActivity, "Сведения о приложении") {
                SystemAccess.openAppDetails(this@HelpActivity)
            })
        }, PetUi.marginParams(this, 8))

        body.addView(PetUi.text(this, "Codex Pet ${BuildConfig.VERSION_NAME}", 12f, PetUi.MUTED).apply {
            setPadding(PetUi.dp(this@HelpActivity, 4), PetUi.dp(this@HelpActivity, 18), 0, 0)
        })

        return ScrollView(this).apply { addView(body) }
    }
}
