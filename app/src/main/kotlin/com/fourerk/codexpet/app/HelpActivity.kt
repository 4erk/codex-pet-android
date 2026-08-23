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
            "Быстрые проверки, если питомец, реплики или обновления работают не так, как ожидается.",
        )

        body.addView(PetUi.sectionTitle(this, "Первый запуск"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.helper(
                this@HelpActivity,
                "Разрешите доступ к уведомлениям, показ поверх окон и служебные уведомления Codex Pet. Обычные уведомления ChatGPT должны оставаться включёнными.",
            ))
        }, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Не обновляется состояние"))
        val connection = PetUi.card(this)
        connection.addView(PetUi.primaryAction(this, "Проверить") {
            ChatGptNotificationListener.refresh(this)
        })
        connection.addView(PetUi.action(this, "Переподключить") {
            ChatGptNotificationListener.restart(this)
        }, PetUi.marginParams(this, 8))
        connection.addView(PetUi.navigationRow(this, "↗", "Подключение", "Состояние уведомлений и восстановление") {
            startActivity(Intent(this, IntegrationActivity::class.java))
        })
        body.addView(connection, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Проверка внешнего вида"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@HelpActivity, "▱", "Стенд", "1–5 баблов, короткая строка и края экрана") {
                startActivity(Intent(this@HelpActivity, BubbleLabActivity::class.java))
            })
            PetUi.addDivider(this, this@HelpActivity)
            addView(PetUi.navigationRow(this@HelpActivity, "✦", "Анимации", "Проверка каждого состояния") {
                startActivity(Intent(this@HelpActivity, AnimationSettingsActivity::class.java))
            })
            PetUi.addDivider(this, this@HelpActivity)
            addView(PetUi.navigationRow(this@HelpActivity, "◰", "Реплики", "Масштаб и правила показа") {
                startActivity(Intent(this@HelpActivity, SpeechSettingsActivity::class.java))
            })
        }, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Обновления"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.helper(
                this@HelpActivity,
                "Проверка новых версий обращается только к GitHub. Тексты уведомлений ChatGPT и содержимое задач туда не отправляются.",
            ))
            addView(PetUi.navigationRow(this@HelpActivity, "⇩", "Обновления", "Проверить, скачать или выбрать APK") {
                startActivity(Intent(this@HelpActivity, UpdatesActivity::class.java))
            })
        }, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Дополнительно"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@HelpActivity, "≡", "Диагностика", "Технические сведения об уведомлениях") {
                startActivity(Intent(this@HelpActivity, DiagnosticsActivity::class.java))
            })
            PetUi.addDivider(this, this@HelpActivity)
            addView(PetUi.navigationRow(this@HelpActivity, "i", "О приложении", "Системные настройки Codex Pet") {
                SystemAccess.openAppDetails(this@HelpActivity)
            })
        }, PetUi.marginParams(this, 4))

        body.addView(PetUi.text(this, "Codex Pet ${BuildConfig.VERSION_NAME}", 11.5f, PetUi.MUTED).apply {
            setPadding(PetUi.dp(this@HelpActivity, 4), PetUi.dp(this@HelpActivity, 18), 0, 0)
        })

        return ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(body)
        }
    }
}
