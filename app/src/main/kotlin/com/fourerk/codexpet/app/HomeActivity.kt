package com.fourerk.codexpet.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.overlay.OverlayService
import com.fourerk.codexpet.system.SystemAccess
import com.fourerk.codexpet.task.TaskAnimationCue
import com.fourerk.codexpet.task.TaskStatus
import com.fourerk.codexpet.task.isCodexTask
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
        render()
    }

    private fun buildContent(): View {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PetUi.dp(this@HomeActivity, 18), PetUi.dp(this@HomeActivity, 20), PetUi.dp(this@HomeActivity, 18), PetUi.dp(this@HomeActivity, 36))
            setBackgroundColor(PetUi.BACKGROUND)
        }
        body.addView(PetUi.text(this, "Codex Pet", 30f, PetUi.TEXT, bold = true))
        body.addView(PetUi.text(this, "Живой помощник поверх ChatGPT — без отдельной панели и лишних действий.", 14f, PetUi.MUTED))

        val hero = PetUi.card(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = null
            contentDescription = "Текущий питомец"
        }
        hero.addView(preview, LinearLayout.LayoutParams(PetUi.dp(this, 108), PetUi.dp(this, 108)))
        val heroInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PetUi.dp(this@HomeActivity, 12), 0, 0, 0)
        }
        heroInfo.addView(PetUi.text(this, "Текущий пет", 12f, PetUi.MUTED))
        heroInfo.addView(PetUi.text(this, "Violet Vixen", 20f, PetUi.TEXT, bold = true))
        heroStatus = PetUi.text(this, "Проверяю…", 13f, PetUi.MUTED)
        heroInfo.addView(heroStatus)
        startButton = PetUi.action(this, "Запустить", ::toggleOverlay)
        heroInfo.addView(startButton)
        hero.addView(heroInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(hero, PetUi.marginParams(this, 16))

        setupCard = PetUi.card(this, "Быстрая настройка").apply {
            addView(PetUi.text(this@HomeActivity, "Нужны только системные доступы. Codex Pet сам проверит каждый шаг.", 13f, PetUi.MUTED))
            addView(PetUi.action(this@HomeActivity, "1. Разрешить чтение уведомлений") {
                SystemAccess.openNotificationListenerSettings(this@HomeActivity)
            })
            addView(PetUi.action(this@HomeActivity, "2. Разрешить поверх приложений") {
                SystemAccess.openOverlaySettings(this@HomeActivity)
            })
            if (Build.VERSION.SDK_INT >= 33) {
                addView(PetUi.action(this@HomeActivity, "3. Разрешить уведомление Codex Pet") {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
        }
        body.addView(setupCard, PetUi.marginParams(this))

        body.addView(PetUi.sectionTitle(this, "НАСТРОИТЬ"))
        val grid = GridLayout(this).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        addTile(grid, "🐾", "Питомец", "Вид, размер и pet pack", PetSettingsActivity::class.java)
        addTile(grid, "💬", "Реплики", "Живой/точный текст и баблы", SpeechSettingsActivity::class.java)
        addTile(grid, "✦", "Анимации", "Когда играют и тест каждой", AnimationSettingsActivity::class.java)
        addTile(grid, "↗", "Подключение", "ChatGPT, уведомления и синхронизация", IntegrationActivity::class.java)
        addTile(grid, "⚙", "Поведение", "Запуск, жесты и фон", BehaviorActivity::class.java)
        addTile(grid, "?", "Помощь", "Подсказки и расширенные инструменты", HelpActivity::class.java)
        body.addView(grid)

        val liveCard = PetUi.card(this, "Сейчас").apply {
            liveStatus = PetUi.text(this@HomeActivity, "Собираю состояние…", 13f, PetUi.MUTED)
            addView(liveStatus)
        }
        body.addView(liveCard, PetUi.marginParams(this, 16))

        body.addView(PetUi.text(this, "Локально: без INTERNET, аналитики и телеметрии.", 12f, PetUi.MUTED).apply {
            setPadding(PetUi.dp(this@HomeActivity, 4), PetUi.dp(this@HomeActivity, 16), 0, 0)
        })

        return ScrollView(this).apply { addView(body) }
    }

    private fun addTile(
        grid: GridLayout,
        icon: String,
        title: String,
        subtitle: String,
        activity: Class<out AppCompatActivity>,
    ) {
        val tile = PetUi.tile(this, icon, title, subtitle) {
            startActivity(Intent(this, activity))
        }
        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(PetUi.dp(this@HomeActivity, 4), PetUi.dp(this@HomeActivity, 4), PetUi.dp(this@HomeActivity, 4), PetUi.dp(this@HomeActivity, 4))
        }
        grid.addView(tile, params)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { AppGraph.settings.settings.collect { render() } }
                launch { AppGraph.pets.visual.collect { render() } }
                launch { AppGraph.tasks.tasks.collect { render() } }
                launch { AppGraph.diagnostics.listener.collect { render() } }
            }
        }
    }

    private fun render() {
        if (!::preview.isInitialized) return
        val settings = AppGraph.settings.settings.value
        val pet = AppGraph.pets.visual.value
        val listener = AppGraph.diagnostics.listener.value
        val tasks = AppGraph.tasks.tasks.value
        val notificationAccess = SystemAccess.hasNotificationAccess(this)
        val overlayAccess = SystemAccess.canDrawOverlays(this)
        val ownNotifications = SystemAccess.hasOwnNotificationPermission(this)
        val ready = notificationAccess && overlayAccess && ownNotifications

        preview.setImageBitmap(pet?.bitmap)
        heroStatus.text = when {
            settings.overlayEnabled && settings.petVisible -> "● На экране"
            settings.overlayEnabled -> "Пет запущен, но скрыт"
            ready -> "Готов к запуску"
            else -> "Нужно закончить быструю настройку"
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
        val exactTargets = tasks.count { it.contentIntent != null || it.bubbleIntent != null }
        liveStatus.text = buildString {
            append(if (listener.connected) "✓ С ChatGPT на связи" else "○ Listener переподключается")
            append("\nАктивных уведомлений ChatGPT: ${listener.activeNotificationCount}")
            append(" · Codex-задач: ${codexTasks.size}")
            if (attention > 0) append("\nНужно внимания: $attention")
            append("\nТочных переходов в чат/задачу: $exactTargets")
            if (pet != null) append("\nPet pack: ${pet.frameSequences.size}/9 анимаций")
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
