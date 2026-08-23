package com.fourerk.codexpet.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
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

    private fun buildContent(): View {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PetUi.dp(this@HelpActivity, 18), PetUi.dp(this@HelpActivity, 20), PetUi.dp(this@HelpActivity, 18), PetUi.dp(this@HelpActivity, 36))
            setBackgroundColor(PetUi.BACKGROUND)
        }
        body.addView(PetUi.text(this, "Помощь", 28f, PetUi.TEXT, bold = true))
        body.addView(PetUi.text(this, "Короткие инструкции для обычной работы; технические инструменты спрятаны ниже.", 14f, PetUi.MUTED))

        body.addView(PetUi.card(this, "Как начать").apply {
            addView(PetUi.text(
                this@HelpActivity,
                "1. Дайте доступ к уведомлениям и overlay.\n2. Оставьте уведомления ChatGPT включёнными, а системные bubbles при желании выключите.\n3. Запустите Codex Pet с главной.\n4. Нажимайте на реплики, чтобы открыть именно связанный чат/задачу, когда ChatGPT передал точный PendingIntent.",
                13f,
                PetUi.MUTED,
            ))
        }, PetUi.marginParams(this, 16))

        body.addView(PetUi.card(this, "Если что-то не обновляется").apply {
            addView(PetUi.text(
                this@HelpActivity,
                "Откройте «Подключение» и нажмите «Пересинхронизировать». Если listener не подключён — используйте «Переподключить listener». На HONOR дополнительно разрешите фоновую работу Codex Pet.",
                13f,
                PetUi.MUTED,
            ))
            addView(PetUi.action(this@HelpActivity, "Пересинхронизировать") {
                ChatGptNotificationListener.refresh(this@HelpActivity)
            })
        }, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Приватность").apply {
            addView(PetUi.text(
                this@HelpActivity,
                "Codex Pet работает локально и не запрашивает INTERNET. Для интерфейса используются данные публичных Android notifications; текст переписки не сохраняется на диск как история.",
                13f,
                PetUi.MUTED,
            ))
        }, PetUi.marginParams(this))

        body.addView(PetUi.sectionTitle(this, "РАСШИРЕННОЕ"))
        body.addView(PetUi.card(this, "Диагностика").apply {
            addView(PetUi.text(
                this@HelpActivity,
                "Нужна только при разборе проблем с конкретной версией ChatGPT/Android. В обычные настройки питомца и long-press меню она больше не встроена.",
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
