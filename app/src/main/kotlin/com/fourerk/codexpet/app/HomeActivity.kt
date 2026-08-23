package com.fourerk.codexpet.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.notification.ChatGptNotificationListener
import com.fourerk.codexpet.system.SystemAccess
import com.fourerk.codexpet.task.TaskAnimationCue
import com.fourerk.codexpet.task.TaskStatus
import com.fourerk.codexpet.task.isCodexTask
import com.fourerk.codexpet.update.UpdatePhase
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    private lateinit var preview: ImageView
    private lateinit var heroStatus: TextView
    private lateinit var liveStatus: TextView
    private lateinit var startButton: android.widget.Button
    private lateinit var setupCard: LinearLayout

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        observeState()
    }

    override fun onResume() {
        super.onResume()
        ensureOverlayServiceIfExpected()
        if (SystemAccess.hasNotificationAccess(this)) ChatGptNotificationListener.ensureHealthy(this)
        AppGraph.updates.checkIfDue()
        render()
    }

    private fun buildContent(): View {
        val body = PetUi.page(
            this,
            "Codex Pet",
            "Пет, реплики и состояние Codex — поверх ChatGPT, без лишней панели.",
        )

        val hero = PetUi.card(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = null
            contentDescription = "Текущий питомец"
        }
        hero.addView(preview, LinearLayout.LayoutParams(PetUi.dp(this, 104), PetUi.dp(this, 104)))
        val heroInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PetUi.dp(this@HomeActivity, 12), 0, 0, 0)
        }
        heroInfo.addView(PetUi.text(this, "Violet Vixen", 20f, PetUi.TEXT, bold = true))
        heroStatus = PetUi.text(this, "Проверяю…", 13f, PetUi.MUTED, bold = true)
        heroInfo.addView(heroStatus)
        startButton = PetUi.primaryAction(this, "Запустить", ::toggleOverlay)
        heroInfo.addView(startButton, PetUi.marginParams(this, 8))
        hero.addView(heroInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(hero, PetUi.marginParams(this, 16))

        setupCard = PetUi.card(this, "Нужно закончить настройку").apply {
            addView(PetUi.text(this@HomeActivity, "Codex Pet сам проверяет обязательные системные доступы.", 12f, PetUi.MUTED))
            addView(PetUi.action(this@HomeActivity, "Доступ к уведомлениям") {
                SystemAccess.openNotificationListenerSettings(this@HomeActivity)
            })
            addView(PetUi.action(this@HomeActivity, "Отображение поверх приложений") {
                SystemAccess.openOverlaySettings(this@HomeActivity)
            })
            if (Build.VERSION.SDK_INT >= 33) {
                addView(PetUi.action(this@HomeActivity, "Уведомления Codex Pet") {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
        }
        body.addView(setupCard, PetUi.marginParams(this))

        val liveCard = PetUi.card(this, "Сейчас")
        liveStatus = PetUi.text(this, "Собираю состояние…", 13f, PetUi.MUTED)
        liveCard.addView(liveStatus)
        liveCard.addView(PetUi.navigationRow(this, "↗", "Подключение", "Listener, ChatGPT notifications и восстановление") {
            startActivity(Intent(this, IntegrationActivity::class.java))
        })
        body.addView(liveCard, PetUi.marginParams(this))

        body.addView(PetUi.sectionTitle(this, "Внешний вид и реакции"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@HomeActivity, "🐾", "Питомец", "Размер, позиция и pet pack") {
                startActivity(Intent(this@HomeActivity, PetSettingsActivity::class.java))
            })
            addView(PetUi.divider(this@HomeActivity), LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this@HomeActivity, 1))
            addView(PetUi.navigationRow(this@HomeActivity, "💬", "Реплики", "Текст, масштаб, приоритеты и lifetimes") {
                startActivity(Intent(this@HomeActivity, SpeechSettingsActivity::class.java))
            })
            addView(PetUi.divider(this@HomeActivity), LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this@HomeActivity, 1))
            addView(PetUi.navigationRow(this@HomeActivity, "✦", "Анимации", "Сценарии, эвристики и тест каждого состояния") {
                startActivity(Intent(this@HomeActivity, AnimationSettingsActivity::class.java))
            })
            addView(PetUi.divider(this@HomeActivity), LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this@HomeActivity, 1))
            addView(PetUi.navigationRow(this@HomeActivity, "◫", "Стенд 1–5 баблов", "Интерактивная проверка компоновки") {
                startActivity(Intent(this@HomeActivity, BubbleLabActivity::class.java))
            })
        }, PetUi.marginParams(this, 6))

        body.addView(PetUi.sectionTitle(this, "Приложение"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@HomeActivity, "⚙", "Поведение", "Жесты, автозапуск и фон") {
                startActivity(Intent(this@HomeActivity, BehaviorActivity::class.java))
            })
            addView(PetUi.divider(this@HomeActivity), LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this@HomeActivity, 1))
            addView(PetUi.navigationRow(this@HomeActivity, "⇩", "Обновления", "GitHub Releases, SHA-256 и установка") {
                startActivity(Intent(this@HomeActivity, UpdatesActivity::class.java))
            })
            addView(PetUi.divider(this@HomeActivity), LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this@HomeActivity, 1))
            addView(PetUi.navigationRow(this@HomeActivity, "?", "Помощь", "Диагностика и сценарии восстановления") {
                startActivity(Intent(this@HomeActivity, HelpActivity::class.java))
            })
        }, PetUi.marginParams(this, 6))

        return ScrollView(this).apply { addView(body) }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { AppGraph.settings.settings.collect { render() } }
                launch { AppGraph.pets.visual.collect { render() } }
                launch { AppGraph.tasks.tasks.collect { render() } }
                launch { AppGraph.diagnostics.listener.collect { render() } }
                launch { AppGraph.updates.state.collect { render() } }
            }
        }
    }

    private fun render() {
        if (!::preview.isInitialized) return
        val settings = AppGraph.settings.settings.value
        val pet = AppGraph.pets.visual.value
        val listener = AppGraph.diagnostics.listener.value
        val tasks = AppGraph.tasks.tasks.value
        val update = AppGraph.updates.state.value
        val notificationAccess = SystemAccess.hasNotificationAccess(this)
        val overlayAccess = SystemAccess.canDrawOverlays(this)
        val ownNotifications = SystemAccess.hasOwnNotificationPermission(this)
        val ready = notificationAccess && overlayAccess && ownNotifications

        preview.setImageBitmap(pet?.bitmap)
        heroStatus.text = when {
            settings.overlayEnabled && settings.petVisible -> "● На экране"
            settings.overlayEnabled -> "○ Запущен, но скрыт"
            ready -> "Готов к запуску"
            else -> "Нужны разрешения"
        }
        heroStatus.setTextColor(
            when {
                settings.overlayEnabled && settings.petVisible -> PetUi.GOOD
                ready -> PetUi.MUTED
                else -> PetUi.WARN
            },
        )
        startButton.text = when {
            settings.overlayEnabled && settings.petVisible -> "Остановить"
            settings.overlayEnabled -> "Показать"
            else -> "Запустить"
        }
        setupCard.visibility = if (ready) View.GONE else View.VISIBLE

        val codexTasks = tasks.filter { it.isCodexTask() }
        val attention = codexTasks.count {
            it.status == TaskStatus.ERROR ||
                it.animationCue == TaskAnimationCue.WAITING_FOR_INPUT ||
                it.animationCue == TaskAnimationCue.DISCONNECTED
        }
        liveStatus.text = buildString {
            append(if (listener.connected) "✓ Listener работает" else "○ Listener восстанавливается")
            listener.lastHeartbeatAt?.let {
                val age = ((System.currentTimeMillis() - it) / 1_000L).coerceAtLeast(0L)
                append(" · heartbeat ${age}с")
            }
            if (listener.consecutiveScanFailures > 0) append("\nСбоев сканирования подряд: ${listener.consecutiveScanFailures}")
            if (listener.rebindAttempts > 0) append(" · rebind #${listener.rebindAttempts}")
            append("\nChatGPT notifications: ${listener.activeNotificationCount} · Codex-задач: ${codexTasks.size}")
            if (attention > 0) append(" · требуют внимания: $attention")
            if (update.phase in setOf(UpdatePhase.AVAILABLE, UpdatePhase.READY_TO_INSTALL)) {
                append("\n↑ Доступно обновление ${update.latestVersion ?: ""}")
            }
        }
    }

    private fun ensureOverlayServiceIfExpected() {
        val settings = AppGraph.settings.settings.value
        if (!settings.overlayEnabled || !settings.petVisible) return
        if (!SystemAccess.hasNotificationAccess(this)) return
        if (!SystemAccess.canDrawOverlays(this)) return
        if (Build.VERSION.SDK_INT >= 33 && !SystemAccess.hasOwnNotificationPermission(this)) return
        SystemAccess.startOverlay(this).onFailure {
            AppGraph.diagnostics.error("restore overlay from HomeActivity: ${it.javaClass.simpleName}")
        }
    }

    private fun toggleOverlay() {
        lifecycleScope.launch {
            val settings = AppGraph.settings.settings.value
            if (settings.overlayEnabled && !settings.petVisible) {
                AppGraph.settings.setPetVisible(true)
                SystemAccess.startOverlay(this@HomeActivity).onFailure {
                    AppGraph.settings.setPetVisible(false)
                    AppGraph.diagnostics.error("show overlay: ${it.javaClass.simpleName}")
                }
                return@launch
            }
            if (settings.overlayEnabled) {
                AppGraph.settings.setOverlayEnabled(false)
                SystemAccess.stopOverlay(this@HomeActivity)
                return@launch
            }
            if (!SystemAccess.hasNotificationAccess(this@HomeActivity)) {
                SystemAccess.openNotificationListenerSettings(this@HomeActivity)
                return@launch
            }
            if (!SystemAccess.canDrawOverlays(this@HomeActivity)) {
                SystemAccess.openOverlaySettings(this@HomeActivity)
                return@launch
            }
            if (Build.VERSION.SDK_INT >= 33 && !SystemAccess.hasOwnNotificationPermission(this@HomeActivity)) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@launch
            }
            AppGraph.settings.setPetVisible(true)
            AppGraph.settings.setOverlayEnabled(true)
            SystemAccess.startOverlay(this@HomeActivity).onFailure {
                AppGraph.settings.setOverlayEnabled(false)
                AppGraph.diagnostics.error("start overlay: ${it.javaClass.simpleName}")
            }
        }
    }
}
