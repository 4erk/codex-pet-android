package com.fourerk.codexpet.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
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
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.fourerk.codexpet.R
import com.fourerk.codexpet.app.AppGraph
import com.fourerk.codexpet.app.DiagnosticsActivity
import com.fourerk.codexpet.app.MainActivity
import com.fourerk.codexpet.pet.PetAnimationState
import com.fourerk.codexpet.pet.PetFrameSequence
import com.fourerk.codexpet.pet.PetVisual
import com.fourerk.codexpet.settings.AppSettings
import com.fourerk.codexpet.settings.LongPressAction
import com.fourerk.codexpet.system.TaskOpener
import com.fourerk.codexpet.task.CodexTask
import com.fourerk.codexpet.task.PetSpeechItem
import com.fourerk.codexpet.task.PetSpeechPolicy
import com.fourerk.codexpet.task.PetSpeechPriority
import com.fourerk.codexpet.task.PetSpeechSelector
import com.fourerk.codexpet.task.TaskAnimationCue
import com.fourerk.codexpet.task.TaskKind
import com.fourerk.codexpet.task.TaskStatus
import com.fourerk.codexpet.task.TaskTransition
import com.fourerk.codexpet.task.isCodexTask
import com.fourerk.codexpet.task.isDisplayTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
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

    private var speechView: FrameLayout? = null
    private var speechParams: WindowManager.LayoutParams? = null
    private var speechUi: SpeechUi? = null
    private var speechItems: List<PetSpeechItem> = emptyList()
    private var speechIndex = 0
    private var speechManualMode = false
    private var autoSpeechSuppressed = false
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

    private val speechRotationRunnable = Runnable {
        if (!isSpeechVisible() || speechItems.size < 2) return@Runnable
        speechIndex = (speechIndex + 1) % speechItems.size
        renderSpeech()
    }

    private val speechExpirationRunnable = Runnable {
        refreshSpeechItems(allowAutoShow = false, contentChanged = false)
    }

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
            contentDescription = context.getString(R.string.app_name)
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
                refreshSpeechItems(allowAutoShow = true, contentChanged = true)
            }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        transientRunnable = null
        displayedState = null
        displayedSequence = null
        stopNativeAnimation()
        removeSpeechWindow()
        removeMenu()
        petRoot?.let { runCatching { windowManager.removeViewImmediate(it) } }
        petRoot = null
        petImage = null
        petParams = null
    }

    fun applySettings(newSettings: AppSettings) {
        val old = settings
        settings = newSettings
        val root = petRoot ?: return
        root.visibility = if (newSettings.overlayEnabled && newSettings.petVisible && pet != null) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        if (!newSettings.petVisible) {
            hideSpeech(suppressAutomatic = false)
            removeMenu()
        } else if (!old.petVisible && newSettings.petVisible && pet != null) {
            autoSpeechSuppressed = false
            refreshSpeechItems(allowAutoShow = true, contentChanged = true)
        }

        if (old.petSizeDp != newSettings.petSizeDp) {
            val size = dp(newSettings.petSizeDp)
            petParams?.let { params ->
                params.width = size
                params.height = size
                clampPosition(params, size)
                updatePetLayout()
            }
            updateSpeechGeometry()
            repositionMenu()
        }
        if (old.animationsEnabled != newSettings.animationsEnabled ||
            old.animationSpeed != newSettings.animationSpeed
        ) {
            renderTaskAnimation(force = true)
        }

        val speechSettingsChanged = old.autoTaskBubblesEnabled != newSettings.autoTaskBubblesEnabled ||
            old.attentionBubblesEnabled != newSettings.attentionBubblesEnabled ||
            old.chatMessageBubblesEnabled != newSettings.chatMessageBubblesEnabled ||
            old.completionBubblesEnabled != newSettings.completionBubblesEnabled ||
            old.completedVisibleSeconds != newSettings.completedVisibleSeconds
        if (speechSettingsChanged) {
            if (!newSettings.autoTaskBubblesEnabled) {
                speechManualMode = false
                autoSpeechSuppressed = true
                hideSpeech(suppressAutomatic = false)
                refreshSpeechItems(allowAutoShow = false, contentChanged = false)
            } else {
                autoSpeechSuppressed = false
                refreshSpeechItems(allowAutoShow = true, contentChanged = true)
            }
        }
    }

    fun setPet(newPet: PetVisual?) {
        if (pet === newPet) return
        val oldHash = pet?.hash
        pet = newPet
        petRoot?.visibility = if (newPet != null && settings.overlayEnabled && settings.petVisible) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        renderTaskAnimation(force = true)
        if (newPet == null) {
            hideSpeech(suppressAutomatic = false)
        } else {
            refreshSpeechItems(allowAutoShow = true, contentChanged = oldHash != newPet.hash)
            if (oldHash != newPet.hash && newPet.frameSequences.isNotEmpty()) {
                petImage?.post { previewAnimation() }
            }
        }
    }

    fun setTasks(newTasks: List<CodexTask>) {
        if (tasks == newTasks) return
        val previousTokens = speechItems.map(::speechToken).toSet()
        tasks = newTasks
        val prospective = selectSpeechItems(manual = speechManualMode && isSpeechVisible())
        val contentChanged = prospective.any { speechToken(it) !in previousTokens }
        refreshSpeechItems(allowAutoShow = true, contentChanged = contentChanged)
        val dominantState = taskAnimationState()
        if (dominantState == PetAnimationState.WAITING || dominantState == PetAnimationState.FAILED) {
            // Attention states always interrupt lower-priority one-shot reactions.
            renderState(dominantState, force = true)
        } else {
            renderTaskAnimation()
        }
    }

    fun onTaskTransition(transition: TaskTransition) {
        val dominantState = taskAnimationState()
        when {
            transition.toCue == TaskAnimationCue.WAITING_FOR_INPUT ->
                renderState(PetAnimationState.WAITING, force = true)
            transition.toCue == TaskAnimationCue.DISCONNECTED ||
                transition.toCue == TaskAnimationCue.FAILED ||
                transition.to == TaskStatus.ERROR -> renderState(
                    if (dominantState == PetAnimationState.WAITING) {
                        PetAnimationState.WAITING
                    } else {
                        PetAnimationState.FAILED
                    },
                    force = true,
                )
            transition.toCue == TaskAnimationCue.RECONNECTING ->
                renderState(PetAnimationState.WAITING, force = true)
            dominantState == PetAnimationState.WAITING || dominantState == PetAnimationState.FAILED ->
                renderState(dominantState, force = true)
            transition.toCue == TaskAnimationCue.COMPLETED || transition.to == TaskStatus.COMPLETED ->
                playOneShot(PetAnimationState.JUMPING)
            transition.liveNotification && transition.kind == TaskKind.CHAT_MESSAGE ->
                playOneShot(PetAnimationState.WAVING)
            else -> renderTaskAnimation()
        }
    }

    fun onConfigurationChanged() {
        removeMenu()
        val params = petParams ?: return
        val size = dp(settings.petSizeDp)
        applySavedPosition(params, size)
        updatePetLayout()
        updateSpeechGeometry()
    }

    fun showNextSpeech() {
        if (!isSpeechVisible()) {
            showSpeech(manual = !settings.autoTaskBubblesEnabled)
            return
        }
        if (speechItems.size > 1) {
            speechIndex = (speechIndex + 1) % speechItems.size
            renderSpeech()
        }
    }

    fun previewAnimation(): Boolean {
        val available = pet?.frameSequences.orEmpty()
        if (available.isEmpty() || !animationsAllowed()) return false
        val first = if (available.containsKey(PetAnimationState.WAVING)) {
            PetAnimationState.WAVING
        } else {
            PetAnimationState.JUMPING
        }
        playOneShot(first) {
            if (first != PetAnimationState.JUMPING && available.containsKey(PetAnimationState.JUMPING)) {
                playOneShot(PetAnimationState.JUMPING)
            } else {
                renderTaskAnimation(force = true)
            }
        }
        return true
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
                    removeMenu()
                }
                if (moved) {
                    params.x = downWindowX + dx.roundToInt()
                    params.y = downWindowY + dy.roundToInt()
                    clampPosition(params, dp(settings.petSizeDp))
                    updatePetLayout()
                    repositionSpeech()
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
                    toggleSpeech()
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

    private fun toggleSpeech() {
        if (isSpeechVisible()) {
            hideSpeech(suppressAutomatic = true)
        } else {
            autoSpeechSuppressed = false
            showSpeech(manual = true)
            playGreeting()
        }
    }

    private fun showSpeech(manual: Boolean) {
        if (petRoot == null || pet == null || !settings.petVisible) return
        removeMenu()
        speechManualMode = manual
        speechItems = selectSpeechItems(manual)
        speechIndex = speechIndex.coerceIn(0, (speechItems.size - 1).coerceAtLeast(0))
        ensureSpeechWindow()
        speechView?.visibility = View.VISIBLE
        renderSpeech()
    }

    private fun ensureSpeechWindow() {
        if (speechView != null) return
        val metrics = speechMetrics()
        val bubble = newSpeechDrawable(metrics)
        val title = label("", metrics.titleSp, Color.WHITE, bold = true).apply {
            maxLines = 1
            visibility = View.GONE
        }
        val body = label("", metrics.bodySp, SPEECH_TEXT_COLOR).apply {
            maxLines = metrics.maxLines
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.08f)
        }
        val dot = View(context)
        val counter = label("", metrics.metaSp, MUTED_COLOR, bold = true).apply {
            isSingleLine = true
        }
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            visibility = View.GONE
            addView(dot, LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(6) })
            addView(counter)
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(metrics.horizontalPadding, metrics.verticalPadding, metrics.horizontalPadding, metrics.verticalPadding)
            addView(title, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(body, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(footer, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val root = FrameLayout(context).apply {
            background = bubble
            clipChildren = false
            clipToPadding = false
            elevation = dp(4).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.open_current_speech)
            addView(content, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val item = speechItems.getOrNull(speechIndex)
                val opened = if (item != null) {
                    TaskOpener.openTask(context, item.task, settings.sourcePackage)
                } else {
                    TaskOpener.openBestAvailable(context, tasks, settings.sourcePackage)
                }
                if (opened) {
                    hideSpeech(suppressAutomatic = true)
                } else {
                    AppGraph.diagnostics.error("Unable to open ChatGPT PendingIntent or launcher")
                }
            }
        }
        val params = baseParams(metrics.width, WindowManager.LayoutParams.WRAP_CONTENT)
        val ui = SpeechUi(root, content, title, body, footer, dot, counter, bubble, metrics.tailSize)
        val placement = attachedPlacement(metrics.width, dp(96))
        applySpeechPlacement(ui, placement)
        params.x = placement.x
        params.y = placement.y
        runCatching { windowManager.addView(root, params) }
            .onFailure { AppGraph.diagnostics.error("add speech bubble: ${it.javaClass.simpleName}") }
            .onSuccess {
                speechView = root
                speechParams = params
                speechUi = ui
                root.post(::repositionSpeech)
            }
    }

    private fun renderSpeech() {
        val ui = speechUi ?: return
        handler.removeCallbacks(speechRotationRunnable)
        val item = speechItems.getOrNull(speechIndex)
        if (item == null) {
            ui.title.visibility = View.GONE
            ui.body.text = context.getString(R.string.waiting_for_status)
            ui.footer.visibility = View.GONE
        } else {
            ui.title.text = item.title.orEmpty()
            ui.title.visibility = if (item.title.isNullOrBlank()) View.GONE else View.VISIBLE
            ui.body.text = item.text
            ui.footer.visibility = if (speechItems.size > 1) View.VISIBLE else View.GONE
            ui.counter.text = context.getString(R.string.speech_counter, speechIndex + 1, speechItems.size)
            ui.dot.background = roundedBackground(priorityColor(item.priority), 6f)
            ui.root.contentDescription = context.getString(
                R.string.open_speech_for,
                item.title ?: item.text.take(80),
            )
        }
        ui.root.post(::repositionSpeech)
        if (speechItems.size > 1 && isSpeechVisible()) {
            handler.postDelayed(speechRotationRunnable, SPEECH_ROTATION_MS)
        }
    }

    private fun refreshSpeechItems(allowAutoShow: Boolean, contentChanged: Boolean) {
        handler.removeCallbacks(speechExpirationRunnable)
        val wasVisible = isSpeechVisible()
        val selectedKey = speechItems.getOrNull(speechIndex)?.task?.sourceNotificationKey
        val manual = speechManualMode && wasVisible
        speechItems = selectSpeechItems(manual)
        speechIndex = selectedKey
            ?.let { key -> speechItems.indexOfFirst { it.task.sourceNotificationKey == key } }
            ?.takeIf { it >= 0 }
            ?: 0
        if (contentChanged) autoSpeechSuppressed = false

        when {
            speechItems.isEmpty() && !manual -> hideSpeech(suppressAutomatic = false)
            wasVisible -> renderSpeech()
            allowAutoShow && settings.autoTaskBubblesEnabled && !autoSpeechSuppressed && speechItems.isNotEmpty() ->
                showSpeech(manual = false)
        }
        scheduleSpeechExpiration()
    }

    private fun selectSpeechItems(manual: Boolean): List<PetSpeechItem> = PetSpeechSelector.select(
        tasks,
        if (manual) manualSpeechPolicy() else automaticSpeechPolicy(),
    )

    private fun automaticSpeechPolicy() = PetSpeechPolicy(
        automaticEnabled = settings.autoTaskBubblesEnabled,
        ongoingEnabled = true,
        attentionEnabled = settings.attentionBubblesEnabled,
        chatMessagesEnabled = settings.chatMessageBubblesEnabled,
        completionsEnabled = settings.completionBubblesEnabled,
        completedVisibleSeconds = settings.completedVisibleSeconds,
    )

    private fun manualSpeechPolicy() = PetSpeechPolicy(
        automaticEnabled = true,
        ongoingEnabled = true,
        attentionEnabled = true,
        chatMessagesEnabled = true,
        completionsEnabled = true,
        completedVisibleSeconds = MANUAL_HISTORY_SECONDS,
        chatMessageVisibleSeconds = MANUAL_HISTORY_SECONDS,
    )

    private fun scheduleSpeechExpiration() {
        handler.removeCallbacks(speechExpirationRunnable)
        val delay = PetSpeechSelector.nextExpirationDelayMillis(speechItems) ?: return
        handler.postDelayed(speechExpirationRunnable, delay + 50L)
    }

    private fun speechToken(item: PetSpeechItem): String =
        "${item.task.sourceNotificationKey}:${item.task.updatedAt.toEpochMilli()}:${item.text}"

    private fun hideSpeech(suppressAutomatic: Boolean) {
        if (suppressAutomatic) autoSpeechSuppressed = true
        speechManualMode = false
        handler.removeCallbacks(speechRotationRunnable)
        speechView?.visibility = View.GONE
        renderTaskAnimation(force = true)
    }

    private fun removeSpeechWindow() {
        handler.removeCallbacks(speechRotationRunnable)
        handler.removeCallbacks(speechExpirationRunnable)
        speechView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        speechView = null
        speechParams = null
        speechUi = null
        speechItems = emptyList()
        speechIndex = 0
        speechManualMode = false
    }

    private fun isSpeechVisible(): Boolean = speechView?.visibility == View.VISIBLE

    private fun showMenu() {
        removeMenu()
        speechView?.visibility = View.GONE
        handler.removeCallbacks(speechRotationRunnable)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(MENU_COLOR, 18f, SPEECH_STROKE_COLOR)
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
        val placement = attachedPlacement(width, dp(220))
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

    private fun removeMenu() {
        menuView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        menuView = null
        menuParams = null
    }

    private fun hidePet() {
        context.startService(Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_HIDE))
    }

    private fun displayTasks(): List<CodexTask> = tasks.filter { task ->
        task.isDisplayTask() &&
            (task.kind != TaskKind.CHAT_MESSAGE || settings.chatMessageBubblesEnabled)
    }

    private fun renderTaskAnimation(force: Boolean = false) {
        if (transientRunnable != null && !force) return
        if (force) cancelTransient()
        renderState(taskAnimationState(), force)
    }

    private fun taskAnimationState(): PetAnimationState {
        val current = displayTasks().filter(CodexTask::isCodexTask)
        return when {
            current.any { it.animationCue == TaskAnimationCue.WAITING_FOR_INPUT } ->
                PetAnimationState.WAITING
            current.any {
                it.status == TaskStatus.ERROR ||
                    it.animationCue == TaskAnimationCue.FAILED ||
                    it.animationCue == TaskAnimationCue.DISCONNECTED
            } -> PetAnimationState.FAILED
            current.any { it.animationCue == TaskAnimationCue.RECONNECTING } ->
                PetAnimationState.WAITING
            current.any { it.animationCue == TaskAnimationCue.REVIEWING } ->
                PetAnimationState.REVIEW
            current.any { it.status == TaskStatus.COMPLETED || it.animationCue == TaskAnimationCue.COMPLETED } ->
                PetAnimationState.REVIEW
            current.any { it.status == TaskStatus.RUNNING || it.animationCue == TaskAnimationCue.ACTIVE } ->
                PetAnimationState.RUNNING
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
            if (animationsAllowed()) native?.start() else native?.stop()
            return
        }
        if (!animationsAllowed()) {
            image.setImageDrawable(filteredDrawable(sequence.frames.first()))
            return
        }
        val animation = animationDrawable(sequence, oneShot = false)
        image.setImageDrawable(animation)
        image.post { if (image.drawable === animation) animation.start() }
    }

    private fun playOneShot(
        state: PetAnimationState,
        onFinished: () -> Unit = { renderTaskAnimation(force = true) },
    ) {
        val sequence = pet?.frameSequences?.get(state) ?: run {
            onFinished()
            return
        }
        if (!animationsAllowed()) {
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
        image.post { if (image.drawable === animation) animation.start() }
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
            else -> playOneShot(PetAnimationState.WAVING, ::showLookTowardSpeech)
        }
    }

    private fun showLookTowardSpeech() {
        val directions = pet?.lookDirections.orEmpty()
        val petLayout = petParams
        val speechLayout = speechParams
        val speech = speechView
        val image = petImage
        if (directions.size != 16 || petLayout == null || speechLayout == null || speech == null || image == null ||
            !isSpeechVisible()
        ) {
            renderTaskAnimation(force = true)
            return
        }
        cancelTransient()
        stopNativeAnimation()
        val petCenterX = petLayout.x + dp(settings.petSizeDp) / 2f
        val petCenterY = petLayout.y + dp(settings.petSizeDp) / 2f
        val speechCenterX = speechLayout.x + speechLayout.width / 2f
        val speechCenterY = speechLayout.y + speech.measuredHeight / 2f
        val degrees = Math.toDegrees(
            atan2((speechCenterX - petCenterX).toDouble(), (petCenterY - speechCenterY).toDouble()),
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

    private fun animationsAllowed(): Boolean =
        settings.animationsEnabled && ValueAnimator.areAnimatorsEnabled()

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
                repositionSpeech()
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

    private fun updateSpeechGeometry() {
        val ui = speechUi ?: return
        val params = speechParams ?: return
        val metrics = speechMetrics()
        params.width = metrics.width
        ui.title.textSize = metrics.titleSp
        ui.body.textSize = metrics.bodySp
        ui.body.maxLines = metrics.maxLines
        ui.counter.textSize = metrics.metaSp
        ui.content.setPadding(
            metrics.horizontalPadding,
            metrics.verticalPadding,
            metrics.horizontalPadding,
            metrics.verticalPadding,
        )
        ui.tailSize = metrics.tailSize
        ui.bubble = newSpeechDrawable(metrics).also { ui.root.background = it }
        repositionSpeech()
    }

    private fun repositionSpeech() {
        if (!isSpeechVisible()) return
        val view = speechView ?: return
        val params = speechParams ?: return
        val ui = speechUi ?: return
        val height = view.measuredHeight.takeIf { it > 0 } ?: dp(96)
        val placement = attachedPlacement(params.width, height)
        applySpeechPlacement(ui, placement)
        params.x = placement.x
        params.y = placement.y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun repositionMenu() {
        val view = menuView ?: return
        val params = menuParams ?: return
        val placement = attachedPlacement(params.width, view.measuredHeight.takeIf { it > 0 } ?: dp(220))
        params.x = placement.x
        params.y = placement.y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun attachedPlacement(width: Int, estimatedHeight: Int): AttachedPlacement {
        val petLayout = petParams ?: return AttachedPlacement(0, 0, TailEdge.RIGHT, 0f)
        val safe = safeBounds()
        val size = dp(settings.petSizeDp)
        val margin = dp((settings.petSizeDp / 14).coerceIn(4, 10))
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
            return AttachedPlacement(
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
        return AttachedPlacement(
            x = x,
            y = y.coerceIn(safe.top, (safe.bottom - estimatedHeight).coerceAtLeast(safe.top)),
            tailEdge = if (useBelow) TailEdge.TOP else TailEdge.BOTTOM,
            tailOffsetPx = (petCenterX - x).toFloat(),
        )
    }

    private fun applySpeechPlacement(ui: SpeechUi, placement: AttachedPlacement) {
        ui.bubble.pointTo(placement.tailEdge, placement.tailOffsetPx)
        val tail = ui.tailSize
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

    private fun speechMetrics(): SpeechMetrics {
        val petDp = settings.petSizeDp
        return SpeechMetrics(
            width = dp((petDp * 2.75f).roundToInt().coerceIn(168, 300)),
            tailSize = dp((petDp / 8).coerceIn(8, 16)),
            radius = dp((petDp / 4).coerceIn(14, 26)).toFloat(),
            horizontalPadding = dp((petDp / 6).coerceIn(10, 20)),
            verticalPadding = dp((petDp / 9).coerceIn(8, 15)),
            titleSp = (petDp * 0.15f).coerceIn(12f, 16f),
            bodySp = (petDp * 0.16f).coerceIn(12.5f, 17f),
            metaSp = (petDp * 0.12f).coerceIn(10f, 12.5f),
            maxLines = if (petDp < 64) 3 else 4,
        )
    }

    private fun newSpeechDrawable(metrics: SpeechMetrics) = SpeechBubbleDrawable(
        color = SPEECH_COLOR,
        strokeColor = SPEECH_STROKE_COLOR,
        cornerRadiusPx = metrics.radius,
        tailSizePx = metrics.tailSize.toFloat(),
        strokeWidthPx = dp(1).toFloat(),
    )

    private fun priorityColor(priority: PetSpeechPriority): Int = when (priority) {
        PetSpeechPriority.NEEDS_INPUT -> 0xFFFFC857.toInt()
        PetSpeechPriority.BLOCKED -> 0xFFFF7474.toInt()
        PetSpeechPriority.READY -> 0xFF77D7B1.toInt()
        PetSpeechPriority.RUNNING -> 0xFF55B7FF.toInt()
    }

    private data class SpeechUi(
        val root: FrameLayout,
        val content: LinearLayout,
        val title: TextView,
        val body: TextView,
        val footer: LinearLayout,
        val dot: View,
        val counter: TextView,
        var bubble: SpeechBubbleDrawable,
        var tailSize: Int,
    )

    private data class SpeechMetrics(
        val width: Int,
        val tailSize: Int,
        val radius: Float,
        val horizontalPadding: Int,
        val verticalPadding: Int,
        val titleSp: Float,
        val bodySp: Float,
        val metaSp: Float,
        val maxLines: Int,
    )

    private data class AttachedPlacement(
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

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val MANUAL_HISTORY_SECONDS = 60
        const val SPEECH_ROTATION_MS = 6_500L
        const val LOOK_HOLD_MS = 900L
        const val SPEECH_STROKE_COLOR = 0x38FFFFFF
        val SPEECH_TEXT_COLOR = 0xFFF2F4F5.toInt()
        val MUTED_COLOR = 0xFFA8B0B6.toInt()
        val SPEECH_COLOR = 0xF21B1D22.toInt()
        val MENU_COLOR = 0xFA1B1D22.toInt()
    }
}
