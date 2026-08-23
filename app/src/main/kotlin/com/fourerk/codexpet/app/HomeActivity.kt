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
            "Питомец, реплики и состояние Codex поверх ChatGPT.",
        )

        val hero = PetUi.heroCard(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = null
            contentDescription = "Текущий питомец"
        }
        hero.addView(preview, LinearLayout.LayoutParams(PetUi.dp(this, 102), PetUi.dp(this, 102)))

        val heroInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PetUi.dp(this@HomeActivity, 14), 0, 0, 0)
        }
        heroInfo.addView(PetUi.text(this, "Violet Vixen", 19f, PetUi.TEXT, bold = true))
        heroStatus = PetUi.statusPill(this, "Проверяю", PetUi.MUTED)
        heroInfo.addView(heroStatus, PetUi.marginParams(this, 8))
        startButton = PetUi.primaryAction(this, "Запустить", ::toggleOverlay)
        heroInfo.addView(startButton, PetUi.marginParams(this, 10))
        hero.addView(heroInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(hero, PetUi.marginParams(this, 16))

        setupCard = PetUi.card(this)
        setupCard.addView(PetUi.text(this, "Нужны доступы", 16f, PetUi.TEXT, bold = true))
        setupCard.addView(PetUi.helper(this, "Codex Pet сам проверяет обязательные системные разрешения."))
        setupCard.addView(PetUi.navigationRow(this, "◉", "Уведомления", "Чтение состояния ChatGPT") {
            SystemAccess.openNotificationListenerSettings(this)
        })
        PetUi.addDivider(setupCard, this)
        setupCard.addView(PetUi.navigationRow(this, "◫", "Поверх окон", "Показ питомца поверх приложений") {
            SystemAccess.openOverlaySettings(this)
        })
        if (Build.VERSION.SDK_INT >= 33) {
            PetUi.addDivider(setupCard, this)
            setupCard.addView(PetUi.navigationRow(this, "●", "Служебные", "Уведомление о фоновой работе Codex Pet") {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        body.addView(setupCard, PetUi.marginParams(this, 12))

        body.addView(PetUi.sectionTitle(this, "Сейчас"))
        val liveCard = PetUi.card(this)
        liveStatus = PetUi.text(this, "Собираю состояние…", 12.5f, PetUi.MUTED)
        liveCard.addView(liveStatus)
        PetUi.addDivider(liveCard, this)
        liveCard.addView(PetUi.navigationRow(this, "↗", "Подключение", "Состояние уведомлений и восстановление") {
            startActivity(Intent(this, IntegrationActivity::class.java))
        })
        body.addView(liveCard, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Питомец"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@HomeActivity, "◉", "Питомец", "Размер, набор и положение") {
                startActivity(Intent(this@HomeActivity, PetSettingsActivity::class.java))
            })
            PetUi.addDivider(this, this@HomeActivity)
            addView(PetUi.navigationRow(this@HomeActivity, "◰", "Реплики", "Масштаб, предпросмотр и правила") {
                startActivity(Intent(this@HomeActivity, SpeechSettingsActivity::class.java))
            })
            PetUi.addDivider(this, this@HomeActivity)
            addView(PetUi.navigationRow(this@HomeActivity, "✦", "Анимации", "Состояния и сценарии") {
                startActivity(Intent(this@HomeActivity, AnimationSettingsActivity::class.java))
            })
            PetUi.addDivider(this, this@HomeActivity)
            addView(PetUi.navigationRow(this@HomeActivity, "▱", "Стенд", "1–5 реплик и края экрана") {
                startActivity(Intent(this@HomeActivity, BubbleLabActivity::class.java))
            })
        }, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Система"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@HomeActivity, "⌁", "Поведение", "Жесты, автозапуск и работа в фоне") {
                startActivity(Intent(this@HomeActivity, BehaviorActivity::class.java))
            })
            PetUi.addDivider(this, this@HomeActivity)
            addView(PetUi.navigationRow(this@HomeActivity, "⇩", "Обновления", "Проверка и установка новых версий") {
                startActivity(Intent(this@HomeActivity, UpdatesActivity::class.java))
            })
            PetUi.addDivider(this, this@HomeActivity)
            addView(PetUi.navigationRow(this@HomeActivity, "?", "Помощь", "Восстановление и диагностика") {
                startActivity(Intent(this@HomeActivity, HelpActivity::class.java))
            })
        }, PetUi.marginParams(this, 4))

        return ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(body)
        }
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
        val statusText: String
        val statusColor: Int
        when {
            settings.overlayEnabled && settings.petVisible -> {
                statusText = "На экране"
                statusColor = PetUi.GOOD
            }
            settings.overlayEnabled -> {
                statusText = "Скрыт"
                statusColor = PetUi.MUTED
            }
            ready -> {
                statusText = "Готов"
                statusColor = PetUi.ACCENT
            }
            else -> {
                statusText = "Нужны доступы"
                statusColor = PetUi.WARN
            }
        }
        heroStatus.text = statusText
        heroStatus.setTextColor(statusColor)
        heroStatus.background = PetUi.rounded(this, statusColor.withAlpha(28), 13, statusColor.withAlpha(72))

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
            append(if (listener.connected) "Подключение работает" else "Подключение восстанавливается")
            listener.lastHeartbeatAt?.let {
                val age = ((System.currentTimeMillis() - it) / 1_000L).coerceAtLeast(0L)
                append(" · ${age} с")
            }
            append("\n${listener.activeNotificationCount} уведомл. · ${codexTasks.size} задач")
            if (attention > 0) append(" · внимания: $attention")
            if (update.phase in setOf(UpdatePhase.AVAILABLE, UpdatePhase.READY_TO_INSTALL)) {
                append("\nОбновление ${update.latestVersion ?: ""} доступно")
            }
        }
    }

    private fun Int.withAlpha(alpha: Int): Int =
        (this and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

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
