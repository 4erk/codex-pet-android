package com.fourerk.codexpet.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.fourerk.codexpet.R
import com.fourerk.codexpet.app.AppGraph
import com.fourerk.codexpet.app.DiagnosticsActivity
import com.fourerk.codexpet.app.MainActivity
import com.fourerk.codexpet.pet.PetVisual
import com.fourerk.codexpet.pet.PetAnimationState
import com.fourerk.codexpet.pet.PetFrameSequence
import com.fourerk.codexpet.settings.AppSettings
import com.fourerk.codexpet.settings.LongPressAction
import com.fourerk.codexpet.system.TaskOpener
import com.fourerk.codexpet.task.CodexTask
import com.fourerk.codexpet.task.TaskKind
import com.fourerk.codexpet.task.TaskStatus
import com.fourerk.codexpet.task.TaskTransition
import com.fourerk.codexpet.task.TaskAnimationCue
import com.fourerk.codexpet.task.isDisplayTask
import com.fourerk.codexpet.task.isCodexTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class OverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var settings = AppSettings()
    private var tasks: List<CodexTask> = emptyList()
    private var pet: PetVisual? = null
    private var petRoot: FrameLayout? = null
    private var petImage: ImageView? = null
    private var petParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelUi: PanelUi? = null
    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var displayedState: PetAnimationState? = null
    private var displayedSequence: PetFrameSequence? = null
    private var transientRunnable: Runnable? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var downWindowX = 0
    private var downWindowY = 0
    private var moved = false
    private var longPressTriggered = false

    private val longPressRunnable = Runnable {
        if (moved || petRoot == null) return@Runnable
        longPressTriggered = true
        petRoot?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        when (settings.longPressAction) {
            LongPressAction.MENU -> showMenu()
            LongPressAction.OPEN_CHATGPT -> TaskOpener.openBestAvailable(context, tasks, settings.sourcePackage)
            LongPressAction.HIDE -> hidePet()
        }
    }

    fun show() {
        if (petRoot != null) return
        val size = dp(settings.petSizeDp)
        val image = ImageView(context).apply {
            background = null
            elevation = 0f
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Codex Pet"
        }
        val root = FrameLayout(context).apply {
            background = null
            elevation = 0f
            clipChildren = false
            clipToPadding = false
            addView(image, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setOnTouchListener(::onPetTouch)
            visibility = if (pet == null || !settings.petVisible) View.INVISIBLE else View.VISIBLE
        }
        val params = baseParams(size, size)
        applySavedPosition(params, size)
        runCatching { windowManager.addView(root, params) }
            .onFailure { AppGraph.diagnostics.error("add pet overlay: ${it.javaClass.simpleName}") }
            .onSuccess {
                petRoot = root
                petImage = image
                petParams = params
                renderTaskAnimation(force = true)
                if (settings.panelPinned && pet != null) root.post(::showPanel)
            }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        transientRunnable?.let(handler::removeCallbacks)
        transientRunnable = null
        displayedState = null
        displayedSequence = null
        stopNativeAnimation()
        removePanel()
        removeMenu()
        petRoot?.let { runCatching { windowManager.removeViewImmediate(it) } }
        petRoot = null
        petImage = null
        petParams = null
    }

    fun applySettings(newSettings: AppSettings) {
        val oldSize = settings.petSizeDp
        val oldAnimations = settings.animationsEnabled
        val oldSpeed = settings.animationSpeed
        val oldPanelPinned = settings.panelPinned
        val oldPetVisible = settings.petVisible
        val oldAutoTaskBubbles = settings.autoTaskBubblesEnabled
        val oldChatMessages = settings.chatMessageBubblesEnabled
        val oldCompletedVisibleSeconds = settings.completedVisibleSeconds
        settings = newSettings
        val root = petRoot ?: return
        root.visibility = if (newSettings.overlayEnabled && newSettings.petVisible && pet != null) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        if (!newSettings.petVisible) {
            removePanel()
            removeMenu()
        } else if (!oldPetVisible && newSettings.petVisible && pet != null) {
            if (newSettings.panelPinned) showPanel() else maybeShowCurrentPanel()
        }
        if (oldSize != newSettings.petSizeDp) {
            val size = dp(newSettings.petSizeDp)
            petParams?.let { params ->
                params.width = size
                params.height = size
                clampPosition(params, size)
                updatePetLayout()
            }
            repositionPanel()
            repositionMenu()
        }
        if (oldAnimations != newSettings.animationsEnabled || oldSpeed != newSettings.animationSpeed) {
            renderTaskAnimation(force = true)
        }
        if (oldPanelPinned != newSettings.panelPinned) {
            updatePinState()
            if (newSettings.panelPinned && panelView == null && pet != null) showPanel()
        }
        if (oldAutoTaskBubbles && !newSettings.autoTaskBubblesEnabled && !newSettings.panelPinned) {
            removePanel()
        } else if (!oldAutoTaskBubbles && newSettings.autoTaskBubblesEnabled && panelView == null) {
            maybeShowCurrentPanel()
        }
        if (panelView != null &&
            (oldChatMessages != newSettings.chatMessageBubblesEnabled ||
                oldCompletedVisibleSeconds != newSettings.completedVisibleSeconds)
        ) {
            renderPanel()
        }
    }

    fun setPet(newPet: PetVisual?) {
        if (pet === newPet) return
        pet = newPet
        petRoot?.visibility = if (newPet != null && settings.overlayEnabled && settings.petVisible) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        renderTaskAnimation(force = true)
        if (panelView == null && newPet != null) {
            if (settings.panelPinned) showPanel() else maybeShowCurrentPanel()
        }
    }

    fun setTasks(newTasks: List<CodexTask>) {
        if (tasks == newTasks) return
        val previousTasks = tasks
        tasks = newTasks
        if (panelView != null) renderPanel()
        else if (settings.panelPinned && pet != null) showPanel()
        else if (shouldAutoShowPanel(previousTasks, newTasks)) showPanel()
        renderTaskAnimation()
    }

    fun onTaskTransition(transition: TaskTransition) {
        val persistentState = taskAnimationState()
        when {
            transition.toCue == TaskAnimationCue.DISCONNECTED ||
                transition.toCue == TaskAnimationCue.FAILED || transition.to == TaskStatus.ERROR ->
                renderState(PetAnimationState.FAILED, force = true)
            transition.toCue == TaskAnimationCue.RECONNECTING ||
                transition.toCue == TaskAnimationCue.WAITING_FOR_INPUT ->
                renderState(PetAnimationState.WAITING, force = true)
            persistentState == PetAnimationState.FAILED || persistentState == PetAnimationState.WAITING ->
                renderState(persistentState, force = true)
            (transition.toCue == TaskAnimationCue.COMPLETED || transition.to == TaskStatus.COMPLETED) &&
                settings.completionBubblesEnabled ->
                playOneShot(PetAnimationState.JUMPING)
            transition.liveNotification && transition.kind == TaskKind.CHAT_MESSAGE &&
                settings.chatMessageBubblesEnabled ->
                playOneShot(PetAnimationState.WAVING)
            else -> renderTaskAnimation()
        }
    }

    fun onConfigurationChanged() {
        removePanel()
        removeMenu()
        val params = petParams ?: return
        val size = dp(settings.petSizeDp)
        applySavedPosition(params, size)
        updatePetLayout()
        if (settings.panelPinned && pet != null) petRoot?.post(::showPanel)
    }

    private fun onPetTouch(view: View, event: MotionEvent): Boolean {
        val params = petParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downWindowX = params.x
                downWindowY = params.y
                moved = false
                longPressTriggered = false
                handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!moved && hypot(dx.toDouble(), dy.toDouble()) > touchSlop.toDouble()) {
                    moved = true
                    handler.removeCallbacks(longPressRunnable)
                    if (!settings.panelPinned) removePanel()
                    removeMenu()
                }
                if (moved) {
                    params.x = downWindowX + dx.roundToInt()
                    params.y = downWindowY + dy.roundToInt()
                    clampPosition(params, dp(settings.petSizeDp))
                    updatePetLayout()
                    if (settings.panelPinned) repositionPanel()
                    renderState(
                        if (dx >= 0f) PetAnimationState.RUNNING_RIGHT else PetAnimationState.RUNNING_LEFT,
                    )
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (moved) {
                    if (settings.snapEnabled) snapToNearestEdge() else savePosition()
                    renderTaskAnimation(force = true)
                } else if (!longPressTriggered) {
                    view.performClick()
                    togglePanel()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (moved) savePosition()
                renderTaskAnimation(force = true)
                return true
            }
        }
        return false
    }

    private fun togglePanel() {
        if (panelView != null) closePanel(unpin = settings.panelPinned) else showPanel()
    }

    /** Adds the WindowManager view once. Notification updates mutate its children in place. */
    private fun showPanel() {
        if (panelView != null || petRoot == null || !settings.petVisible) return
        removeMenu()

        val bubble = SpeechBubbleDrawable(
            color = PANEL_COLOR,
            strokeColor = PANEL_STROKE_COLOR,
            cornerRadiusPx = dp(24).toFloat(),
            tailSizePx = dp(PANEL_TAIL_DP).toFloat(),
            strokeWidthPx = dp(1).toFloat(),
        )
        val root = FrameLayout(context).apply {
            background = bubble
            clipChildren = false
            clipToPadding = false
            elevation = dp(6).toFloat()
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val activeLabel = label("", 11f, ACTIVE_COLOR, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = roundedBackground(0x2410A37F, 12f)
            isSingleLine = true
        }
        val pinButton = iconButton(R.drawable.ic_pin, context.getString(R.string.pin_panel)) {
            setPanelPinned(!settings.panelPinned)
        }
        val closeButton = iconButton(R.drawable.ic_close, context.getString(R.string.close_panel)) {
            closePanel(unpin = settings.panelPinned)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label(context.getString(R.string.panel_title), 18f, Color.WHITE, bold = true))
            addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(activeLabel)
            addView(pinButton, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(6) })
            addView(closeButton, LinearLayout.LayoutParams(dp(36), dp(36)))
        }
        val taskContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        val safe = safeBounds()
        val maxTaskHeight = min(dp(420), ((safe.bottom - safe.top) * 0.55f).roundToInt())
        val taskScroll = ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                taskContainer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val openAll = label(context.getString(R.string.all_tasks), 13f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = roundedBackground(0xFF30353B.toInt(), 14f, PANEL_STROKE_COLOR)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                TaskOpener.openBestAvailable(context, tasks, settings.sourcePackage)
                if (!settings.panelPinned) removePanel()
            }
        }
        content.addView(header)
        content.addView(
            label(context.getString(R.string.current_status), 11f, MUTED_COLOR, bold = true).apply {
                letterSpacing = 0.08f
                isAllCaps = true
            },
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        content.addView(taskScroll, LinearLayout.LayoutParams.MATCH_PARENT, maxTaskHeight)
        content.addView(openAll, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val width = min(dp(PANEL_WIDTH_DP), safe.right - safe.left - dp(16)).coerceAtLeast(dp(248))
        val params = baseParams(width, WindowManager.LayoutParams.WRAP_CONTENT)
        val ui = PanelUi(root, activeLabel, taskContainer, pinButton, bubble)
        val placement = panelPlacement(width, dp(320))
        applyPanelPlacement(ui, placement)
        params.x = placement.x
        params.y = placement.y

        runCatching { windowManager.addView(root, params) }
            .onFailure { AppGraph.diagnostics.error("add task panel: ${it.javaClass.simpleName}") }
            .onSuccess {
                panelView = root
                panelParams = params
                panelUi = ui
                renderPanel()
                root.post(::repositionPanel)
                playGreeting()
            }
    }

    private fun renderPanel() {
        val ui = panelUi ?: return
        val visibleTasks = visibleTasks()
        val runningCount = visibleTasks.count { it.isCodexTask() && it.status == TaskStatus.RUNNING }
        ui.activeLabel.text = context.resources.getQuantityString(
            R.plurals.active_tasks_format,
            runningCount,
            runningCount,
        )
        updatePinState()
        if (visibleTasks.isEmpty()) {
            ui.taskRows.values.forEach { ui.taskContainer.removeView(it.root) }
            ui.taskRows.clear()
            if (ui.emptyView == null) {
                val empty = label(context.getString(R.string.waiting_for_status), 13f, SECONDARY_TEXT_COLOR).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(14), dp(12), dp(14))
                    background = roundedBackground(TASK_CARD_COLOR, 16f)
                    maxLines = 2
                }
                ui.emptyView = empty
                ui.taskContainer.addView(
                    empty,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
        } else {
            ui.emptyView?.let(ui.taskContainer::removeView)
            ui.emptyView = null
            val shownTasks = visibleTasks.take(MAX_PANEL_TASKS)
            val desiredKeys = shownTasks.map(CodexTask::sourceNotificationKey).toSet()
            ui.taskRows.entries
                .filter { it.key !in desiredKeys }
                .forEach { (key, row) ->
                    ui.taskContainer.removeView(row.root)
                    ui.taskRows.remove(key)
                }
            shownTasks.forEachIndexed { index, task ->
                val row = ui.taskRows.getOrPut(task.sourceNotificationKey, ::createTaskRow)
                bindTaskRow(row, task)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (index < shownTasks.lastIndex) bottomMargin = dp(8)
                }
                val currentIndex = ui.taskContainer.indexOfChild(row.root)
                if (currentIndex != index) {
                    if (currentIndex >= 0) ui.taskContainer.removeView(row.root)
                    ui.taskContainer.addView(row.root, index, params)
                } else {
                    row.root.layoutParams = params
                }
            }
        }
    }

    private fun createTaskRow(): TaskRowUi {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = roundedBackground(TASK_CARD_COLOR, 16f, TASK_CARD_STROKE_COLOR)
            isClickable = true
            isFocusable = true
        }
        val titleLine = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val statusDot = View(context)
        titleLine.addView(
            statusDot,
            LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(9) },
        )
        val title = label("", 14f, Color.WHITE, bold = true).apply { maxLines = 1 }
        titleLine.addView(
            title,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        val status = label("", 10f, MUTED_COLOR, bold = true).apply {
            isSingleLine = true
            setPadding(dp(7), dp(3), dp(7), dp(3))
        }
        titleLine.addView(
            status,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        row.addView(titleLine)

        val detail = label("", 11f, MUTED_COLOR).apply {
            maxLines = 1
            setPadding(0, dp(5), 0, 0)
            visibility = View.GONE
        }
        row.addView(detail)
        val summary = label("", 13f, SECONDARY_TEXT_COLOR).apply {
            ellipsize = null
            setLineSpacing(0f, 1.06f)
            setPadding(0, dp(6), 0, 0)
            visibility = View.GONE
        }
        row.addView(summary)
        val age = label("", 11f, MUTED_COLOR).apply { setPadding(0, dp(7), 0, 0) }
        row.addView(age)

        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
        val progressHolder = FrameLayout(context).apply {
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
            addView(
                progress,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(4),
                    Gravity.BOTTOM,
                ),
            )
        }
        row.addView(progressHolder, LinearLayout.LayoutParams.MATCH_PARENT, dp(12))
        return TaskRowUi(row, statusDot, title, status, detail, summary, age, progressHolder, progress)
    }

    private fun bindTaskRow(ui: TaskRowUi, task: CodexTask) {
        val color = taskDisplayColor(task)
        ui.statusDot.background = roundedBackground(color, 6f)
        ui.title.text = task.title
        ui.status.text = taskDisplayStatusText(task)
        ui.status.setTextColor(color)
        ui.status.background = roundedBackground(withAlpha(color, 0x24), 10f)
        ui.detail.text = task.detail.orEmpty()
        ui.detail.visibility = if (task.detail.isNullOrBlank()) View.GONE else View.VISIBLE
        ui.summary.text = task.summary.orEmpty()
        ui.summary.visibility = if (task.summary.isNullOrBlank()) View.GONE else View.VISIBLE
        ui.age.text = DateUtils.getRelativeTimeSpanString(
            task.updatedAt.toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.SECOND_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
        val taskProgress = task.progress
        ui.progressHolder.visibility = if (taskProgress == null) View.GONE else View.VISIBLE
        if (taskProgress != null) {
            ui.progress.isIndeterminate = taskProgress.indeterminate
            ui.progress.max = taskProgress.max.coerceAtLeast(1)
            ui.progress.progress = taskProgress.value.coerceIn(0, ui.progress.max)
            ui.progress.progressTintList = ColorStateList.valueOf(color)
            ui.progress.indeterminateTintList = ColorStateList.valueOf(color)
        }
        ui.root.setOnClickListener {
            TaskOpener.openTask(context, task, settings.sourcePackage)
            if (!settings.panelPinned) removePanel()
        }
    }

    private fun showMenu() {
        removePanel()
        removeMenu()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(PANEL_COLOR, 18f, PANEL_STROKE_COLOR)
            elevation = dp(6).toFloat()
        }
        fun addAction(title: String, action: () -> Unit) {
            container.addView(
                label(title, 14f, Color.WHITE).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        action()
                        removeMenu()
                        if (settings.panelPinned && pet != null) showPanel()
                    }
                },
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        addAction(context.getString(R.string.open_chatgpt)) {
            TaskOpener.openBestAvailable(context, tasks, settings.sourcePackage)
        }
        addAction(context.getString(R.string.settings)) {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        addAction(context.getString(R.string.diagnostics)) {
            context.startActivity(Intent(context, DiagnosticsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        addAction(context.getString(R.string.hide_pet), ::hidePet)
        val width = dp(230)
        val params = baseParams(width, WindowManager.LayoutParams.WRAP_CONTENT)
        val placement = panelPlacement(width, dp(220))
        params.x = placement.x
        params.y = placement.y
        runCatching { windowManager.addView(container, params) }
            .onFailure { AppGraph.diagnostics.error("add long-press menu: ${it.javaClass.simpleName}") }
            .onSuccess {
                menuView = container
                menuParams = params
                container.post(::repositionMenu)
            }
    }

    private fun hidePet() {
        context.startService(Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_HIDE))
    }

    private fun setPanelPinned(pinned: Boolean) {
        if (settings.panelPinned == pinned) return
        settings = settings.copy(panelPinned = pinned)
        updatePinState()
        scope.launch { AppGraph.settings.setPanelPinned(pinned) }
    }

    private fun updatePinState() {
        val button = panelUi?.pinButton ?: return
        button.contentDescription = context.getString(
            if (settings.panelPinned) R.string.unpin_panel else R.string.pin_panel,
        )
        button.imageTintList = ColorStateList.valueOf(
            if (settings.panelPinned) ACTIVE_COLOR else MUTED_COLOR,
        )
        button.background = if (settings.panelPinned) {
            roundedBackground(0x2410A37F, 18f)
        } else {
            roundedBackground(Color.TRANSPARENT, 18f)
        }
    }

    private fun closePanel(unpin: Boolean) {
        if (unpin) setPanelPinned(false)
        removePanel()
        renderTaskAnimation(force = true)
    }

    private fun removePanel() {
        panelView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        panelView = null
        panelParams = null
        panelUi = null
    }

    private fun removeMenu() {
        menuView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        menuView = null
        menuParams = null
    }

    private fun displayTasks(): List<CodexTask> = tasks.filter { task ->
        task.isDisplayTask() &&
            (task.kind != TaskKind.CHAT_MESSAGE || settings.chatMessageBubblesEnabled)
    }

    private fun shouldAutoShowPanel(previous: List<CodexTask>, current: List<CodexTask>): Boolean {
        if (pet == null || !settings.petVisible) return false
        val old = previous.associateBy(CodexTask::sourceNotificationKey)
        return current.any { task ->
            val prior = old[task.sourceNotificationKey]
            when {
                task.kind == TaskKind.CHAT_MESSAGE ->
                    settings.chatMessageBubblesEnabled &&
                        (prior == null || prior.summary != task.summary || prior.title != task.title)
                !task.isCodexTask() -> false
                task.animationCue == TaskAnimationCue.DISCONNECTED ||
                    task.animationCue == TaskAnimationCue.FAILED ||
                    task.animationCue == TaskAnimationCue.RECONNECTING ||
                    task.animationCue == TaskAnimationCue.WAITING_FOR_INPUT ->
                    settings.attentionBubblesEnabled && prior?.animationCue != task.animationCue
                task.animationCue == TaskAnimationCue.COMPLETED || task.status == TaskStatus.COMPLETED ->
                    settings.completionBubblesEnabled &&
                        (prior?.animationCue != task.animationCue || prior.status != task.status)
                else -> settings.autoTaskBubblesEnabled && prior == null && task.status == TaskStatus.RUNNING
            }
        }
    }

    private fun maybeShowCurrentPanel() {
        if (panelView != null) return
        if (shouldAutoShowPanel(emptyList(), tasks)) showPanel()
    }

    private fun visibleTasks(): List<CodexTask> {
        val now = Instant.now()
        return displayTasks().filter { task ->
            task.status != TaskStatus.COMPLETED ||
                (settings.completedVisibleSeconds > 0 &&
                    Duration.between(task.updatedAt, now).seconds <= settings.completedVisibleSeconds)
        }
    }

    private fun renderTaskAnimation(force: Boolean = false) {
        if (transientRunnable != null && !force) return
        if (force) cancelTransient()
        renderState(taskAnimationState(), force)
    }

    private fun taskAnimationState(): PetAnimationState {
        val current = displayTasks().filter(CodexTask::isCodexTask)
        return when {
            current.any {
                it.status == TaskStatus.ERROR ||
                    it.animationCue == TaskAnimationCue.FAILED ||
                    it.animationCue == TaskAnimationCue.DISCONNECTED
            } ->
                PetAnimationState.FAILED
            current.any {
                it.animationCue == TaskAnimationCue.WAITING_FOR_INPUT ||
                    it.animationCue == TaskAnimationCue.RECONNECTING
            } -> PetAnimationState.WAITING
            current.any { it.animationCue == TaskAnimationCue.REVIEWING } -> PetAnimationState.REVIEW
            current.any { it.status == TaskStatus.RUNNING || it.animationCue == TaskAnimationCue.ACTIVE } ->
                PetAnimationState.RUNNING
            current.any { it.status == TaskStatus.COMPLETED || it.animationCue == TaskAnimationCue.COMPLETED } ->
                PetAnimationState.REVIEW
            else -> PetAnimationState.IDLE
        }
    }

    private fun renderState(state: PetAnimationState, force: Boolean = false) {
        if (transientRunnable != null && !force) return
        if (force) cancelTransient()
        val visual = pet ?: return
        val image = petImage ?: return
        val sequence = visual.frameSequences[state]
        if (!force && displayedState == state && displayedSequence === sequence) return
        stopNativeAnimation()
        displayedState = state
        displayedSequence = sequence
        if (sequence == null) {
            image.setImageDrawable(visual.drawable)
            val native = image.drawable as? Animatable
            if (settings.animationsEnabled && ValueAnimator.areAnimatorsEnabled()) native?.start() else native?.stop()
            return
        }
        if (!settings.animationsEnabled || !ValueAnimator.areAnimatorsEnabled()) {
            image.setImageDrawable(filteredDrawable(sequence.frames.first()))
            return
        }
        val animation = animationDrawable(sequence, oneShot = false)
        image.setImageDrawable(animation)
        image.post(animation::start)
    }

    private fun playOneShot(state: PetAnimationState, onFinished: () -> Unit = { renderTaskAnimation(force = true) }) {
        val sequence = pet?.frameSequences?.get(state) ?: run {
            onFinished()
            return
        }
        if (!settings.animationsEnabled || !ValueAnimator.areAnimatorsEnabled()) {
            onFinished()
            return
        }
        cancelTransient()
        val image = petImage ?: return
        stopNativeAnimation()
        displayedState = state
        displayedSequence = sequence
        val animation = animationDrawable(sequence, oneShot = true)
        image.setImageDrawable(animation)
        image.post(animation::start)
        val speed = settings.animationSpeed.coerceIn(0.5f, 2f)
        val duration = (sequence.frameDurationsMs.sum() / speed).toLong().coerceAtLeast(80L)
        val restore = Runnable {
            transientRunnable = null
            onFinished()
        }
        transientRunnable = restore
        handler.postDelayed(restore, duration)
    }

    private fun playGreeting() {
        when (taskAnimationState()) {
            PetAnimationState.FAILED, PetAnimationState.WAITING -> renderTaskAnimation(force = true)
            else -> playOneShot(PetAnimationState.WAVING) { showLookTowardPanel() }
        }
    }

    private fun showLookTowardPanel() {
        val directions = pet?.lookDirections.orEmpty()
        val petLayout = petParams
        val panelLayout = panelParams
        val panel = panelView
        val image = petImage
        if (directions.size != 16 || petLayout == null || panelLayout == null || panel == null || image == null) {
            renderTaskAnimation(force = true)
            return
        }
        cancelTransient()
        stopNativeAnimation()
        val petCenterX = petLayout.x + dp(settings.petSizeDp) / 2f
        val petCenterY = petLayout.y + dp(settings.petSizeDp) / 2f
        val panelCenterX = panelLayout.x + panelLayout.width / 2f
        val panelCenterY = panelLayout.y + panel.measuredHeight / 2f
        val degrees = Math.toDegrees(
            atan2((panelCenterX - petCenterX).toDouble(), (petCenterY - panelCenterY).toDouble()),
        ).let { if (it < 0) it + 360.0 else it }
        val index = ((degrees / 22.5).roundToInt() % 16).coerceIn(0, 15)
        displayedState = null
        displayedSequence = null
        image.setImageDrawable(filteredDrawable(directions[index]))
        val restore = Runnable {
            transientRunnable = null
            renderTaskAnimation(force = true)
        }
        transientRunnable = restore
        handler.postDelayed(restore, LOOK_HOLD_MS)
    }

    private fun animationDrawable(sequence: PetFrameSequence, oneShot: Boolean): AnimationDrawable =
        AnimationDrawable().apply {
            isOneShot = oneShot
            val speed = settings.animationSpeed.coerceIn(0.5f, 2f)
            sequence.frames.forEachIndexed { index, frame ->
                val duration = ((sequence.frameDurationsMs.getOrElse(index) { 150 }) / speed)
                    .roundToInt()
                    .coerceAtLeast(40)
                addFrame(filteredDrawable(frame), duration)
            }
        }

    private fun filteredDrawable(bitmap: android.graphics.Bitmap): BitmapDrawable =
        bitmap.toDrawable(context.resources).apply {
            setTargetDensity(context.resources.displayMetrics)
            isFilterBitmap = true
            paint.isDither = true
        }

    private fun cancelTransient() {
        transientRunnable?.let(handler::removeCallbacks)
        transientRunnable = null
    }

    private fun stopNativeAnimation() {
        (petImage?.drawable as? Animatable)?.stop()
    }

    private fun snapToNearestEdge() {
        val params = petParams ?: return
        val root = petRoot ?: return
        val safe = safeBounds()
        val size = dp(settings.petSizeDp)
        val left = safe.left
        val right = (safe.right - size).coerceAtLeast(left)
        val target = if (abs(params.x - left) <= abs(params.x - right)) left else right
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 180L
            addUpdateListener {
                params.x = it.animatedValue as Int
                runCatching { windowManager.updateViewLayout(root, params) }
                if (settings.panelPinned) repositionPanel()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = savePosition()
            })
            start()
        }
    }

    private fun savePosition() {
        val params = petParams ?: return
        scope.launch { AppGraph.settings.savePosition(orientation(), params.x, params.y) }
    }

    private fun applySavedPosition(params: WindowManager.LayoutParams, size: Int) {
        val safe = safeBounds()
        val landscape = orientation() == Configuration.ORIENTATION_LANDSCAPE
        val savedX = if (landscape) settings.landscapeX else settings.portraitX
        val savedY = if (landscape) settings.landscapeY else settings.portraitY
        params.x = if (savedX >= 0) savedX else safe.right - size - dp(12)
        params.y = if (savedY >= 0) savedY else safe.top + (safe.bottom - safe.top - size) / 2
        clampPosition(params, size)
    }

    private fun clampPosition(params: WindowManager.LayoutParams, size: Int) {
        val safe = safeBounds()
        params.x = params.x.coerceIn(safe.left, (safe.right - size).coerceAtLeast(safe.left))
        params.y = params.y.coerceIn(safe.top, (safe.bottom - size).coerceAtLeast(safe.top))
    }

    private fun repositionPanel() {
        val view = panelView ?: return
        val params = panelParams ?: return
        val ui = panelUi ?: return
        val height = view.measuredHeight.takeIf { it > 0 } ?: dp(320)
        val placement = panelPlacement(params.width, height)
        applyPanelPlacement(ui, placement)
        params.x = placement.x
        params.y = placement.y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun repositionMenu() {
        val view = menuView ?: return
        val params = menuParams ?: return
        val placement = panelPlacement(params.width, view.measuredHeight.takeIf { it > 0 } ?: dp(220))
        params.x = placement.x
        params.y = placement.y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun panelPlacement(width: Int, estimatedHeight: Int): PanelPlacement {
        val petLayout = petParams ?: return PanelPlacement(0, 0, TailEdge.RIGHT, 0f)
        val safe = safeBounds()
        val size = dp(settings.petSizeDp)
        val margin = dp(6)
        val petCenterX = petLayout.x + size / 2
        val petCenterY = petLayout.y + size / 2
        val rightX = petLayout.x + size + margin
        val leftX = petLayout.x - width - margin
        val fitsRight = rightX + width <= safe.right
        val fitsLeft = leftX >= safe.left

        if (fitsRight || fitsLeft) {
            val useRight = fitsRight && (!fitsLeft || petCenterX < (safe.left + safe.right) / 2)
            val x = if (useRight) rightX else leftX
            val y = (petCenterY - estimatedHeight / 2)
                .coerceIn(safe.top, (safe.bottom - estimatedHeight).coerceAtLeast(safe.top))
            return PanelPlacement(
                x = x,
                y = y,
                tailEdge = if (useRight) TailEdge.LEFT else TailEdge.RIGHT,
                tailOffsetPx = (petCenterY - y).toFloat(),
            )
        }

        val belowY = petLayout.y + size + margin
        val aboveY = petLayout.y - estimatedHeight - margin
        val spaceBelow = safe.bottom - belowY
        val spaceAbove = petLayout.y - margin - safe.top
        val useBelow = spaceBelow >= estimatedHeight || spaceBelow >= spaceAbove
        val x = (petCenterX - width / 2)
            .coerceIn(safe.left, (safe.right - width).coerceAtLeast(safe.left))
        val y = if (useBelow) belowY else aboveY
        return PanelPlacement(
            x = x,
            y = y.coerceIn(safe.top, (safe.bottom - estimatedHeight).coerceAtLeast(safe.top)),
            tailEdge = if (useBelow) TailEdge.TOP else TailEdge.BOTTOM,
            tailOffsetPx = (petCenterX - x).toFloat(),
        )
    }

    private fun applyPanelPlacement(ui: PanelUi, placement: PanelPlacement) {
        ui.bubble.pointTo(placement.tailEdge, placement.tailOffsetPx)
        val tail = dp(PANEL_TAIL_DP)
        ui.root.setPadding(
            if (placement.tailEdge == TailEdge.LEFT) tail else 0,
            if (placement.tailEdge == TailEdge.TOP) tail else 0,
            if (placement.tailEdge == TailEdge.RIGHT) tail else 0,
            if (placement.tailEdge == TailEdge.BOTTOM) tail else 0,
        )
    }

    private fun updatePetLayout() {
        val root = petRoot ?: return
        val params = petParams ?: return
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun baseParams(width: Int, height: Int): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
    }

    private data class PanelUi(
        val root: FrameLayout,
        val activeLabel: TextView,
        val taskContainer: LinearLayout,
        val pinButton: ImageButton,
        val bubble: SpeechBubbleDrawable,
        val taskRows: MutableMap<String, TaskRowUi> = linkedMapOf(),
        var emptyView: View? = null,
    )

    private data class TaskRowUi(
        val root: LinearLayout,
        val statusDot: View,
        val title: TextView,
        val status: TextView,
        val detail: TextView,
        val summary: TextView,
        val age: TextView,
        val progressHolder: FrameLayout,
        val progress: ProgressBar,
    )

    private data class PanelPlacement(
        val x: Int,
        val y: Int,
        val tailEdge: TailEdge,
        val tailOffsetPx: Float,
    )

    private data class SafeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun safeBounds(): SafeBounds {
        val metrics = windowManager.currentWindowMetrics
        val bars = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        return SafeBounds(
            left = metrics.bounds.left + bars.left,
            top = metrics.bounds.top + bars.top,
            right = metrics.bounds.right - bars.right,
            bottom = metrics.bounds.bottom - bars.bottom,
        )
    }

    private fun orientation(): Int = context.resources.configuration.orientation

    private fun iconButton(drawableRes: Int, description: String, action: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(drawableRes)
            contentDescription = description
            imageTintList = ColorStateList.valueOf(MUTED_COLOR)
            background = roundedBackground(Color.TRANSPARENT, 18f)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            elevation = 0f
            setOnClickListener { action() }
        }

    private fun roundedBackground(
        color: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.roundToInt()).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

    private fun label(text: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun statusColor(status: TaskStatus): Int = when (status) {
        TaskStatus.RUNNING -> ACTIVE_COLOR
        TaskStatus.COMPLETED -> 0xFF7DD3B0.toInt()
        TaskStatus.ERROR -> 0xFFFF7A7A.toInt()
        TaskStatus.UNKNOWN -> 0xFF9AA3AA.toInt()
    }

    private fun statusText(status: TaskStatus): String = context.getString(
        when (status) {
            TaskStatus.RUNNING -> R.string.task_status_running
            TaskStatus.COMPLETED -> R.string.task_status_completed
            TaskStatus.ERROR -> R.string.task_status_error
            TaskStatus.UNKNOWN -> R.string.task_status_unknown
        },
    )

    private fun taskDisplayStatusText(task: CodexTask): String {
        val textResource = when {
            task.kind == TaskKind.CHAT_MESSAGE -> R.string.task_status_message
            task.animationCue == TaskAnimationCue.DISCONNECTED -> R.string.task_status_disconnected
            task.animationCue == TaskAnimationCue.RECONNECTING -> R.string.task_status_reconnecting
            task.animationCue == TaskAnimationCue.WAITING_FOR_INPUT -> R.string.task_status_waiting
            task.animationCue == TaskAnimationCue.REVIEWING -> R.string.task_status_reviewing
            else -> return statusText(task.status)
        }
        return context.getString(textResource)
    }

    private fun taskDisplayColor(task: CodexTask): Int = when {
        task.kind == TaskKind.CHAT_MESSAGE -> MESSAGE_COLOR
        task.animationCue == TaskAnimationCue.DISCONNECTED ||
            task.animationCue == TaskAnimationCue.FAILED -> 0xFFFF7A7A.toInt()
        task.animationCue == TaskAnimationCue.RECONNECTING ||
            task.animationCue == TaskAnimationCue.WAITING_FOR_INPUT -> 0xFFFFC857.toInt()
        task.animationCue == TaskAnimationCue.REVIEWING -> 0xFF77BDFB.toInt()
        else -> statusColor(task.status)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val MAX_PANEL_TASKS = 6
        const val PANEL_WIDTH_DP = 360
        const val PANEL_TAIL_DP = 12
        const val LOOK_HOLD_MS = 900L
        const val PANEL_STROKE_COLOR = 0x32FFFFFF
        const val TASK_CARD_STROKE_COLOR = 0x1FFFFFFF
        val SECONDARY_TEXT_COLOR = 0xFFD2D6DA.toInt()
        val MUTED_COLOR = 0xFF9099A1.toInt()
        val PANEL_COLOR = 0xF51B1D22.toInt()
        val TASK_CARD_COLOR = 0xE826292F.toInt()
        val ACTIVE_COLOR = 0xFF20C997.toInt()
        val MESSAGE_COLOR = 0xFFB69CFF.toInt()
    }
}
