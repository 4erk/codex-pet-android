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
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.StyleSpan
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
import com.fourerk.codexpet.app.HomeActivity
import com.fourerk.codexpet.pet.PetAnimationState
import com.fourerk.codexpet.pet.PetFrameSequence
import com.fourerk.codexpet.pet.PetVisual
import com.fourerk.codexpet.settings.AppSettings
import com.fourerk.codexpet.settings.LongPressAction
import com.fourerk.codexpet.system.TaskOpener
import com.fourerk.codexpet.task.CodexTask
import com.fourerk.codexpet.task.PetAnimationStateResolver
import com.fourerk.codexpet.task.PetSpeechFormatter
import com.fourerk.codexpet.task.PetSpeechItem
import com.fourerk.codexpet.task.PetSpeechPolicy
import com.fourerk.codexpet.task.PetSpeechPriority
import com.fourerk.codexpet.task.PetSpeechSelector
import com.fourerk.codexpet.task.TaskAnimationCue
import com.fourerk.codexpet.task.TaskKind
import com.fourerk.codexpet.task.TaskStatus
import com.fourerk.codexpet.task.TaskTransition
import com.fourerk.codexpet.task.hasExactOpenTarget
import com.fourerk.codexpet.task.isCodexTask
import com.fourerk.codexpet.task.isDisplayTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Product UI controller used by the stable overlay. The old controller is retained for rollback. */
class OverlayControllerV2(
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

    private val speechWindows = mutableListOf<SpeechWindow>()
    private var speechItems: List<PetSpeechItem> = emptyList()
    private var speechShown = false
    private var speechManualMode = false
    private var autoSpeechSuppressed = false
    private var pageStart = 0
    private var renderedPageSize = 0
    private var speechRenderPending = false

    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null

    private var displayedState: PetAnimationState? = null
    private var displayedSequence: PetFrameSequence? = null
    private var transientRunnable: Runnable? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var downWindowX = 0
    private var downWindowY = 0
    private var moved = false
    private var dragging = false
    private var longPressTriggered = false
    private var snapAnimator: ValueAnimator? = null

    private val speechRotationRunnable = Runnable {
        if (!speechShown || speechItems.size <= renderedPageSize.coerceAtLeast(1)) return@Runnable
        advanceSpeechPage()
        renderSpeechWindows()
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
            LongPressAction.OPEN_CHATGPT -> openBestCurrent()
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
        cancelSnapAnimation()
        dragging = false
        handler.removeCallbacksAndMessages(null)
        cancelTransient()
        stopNativeAnimation()
        removeSpeechWindows()
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
            if (speechShown) renderSpeechWindows()
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
            old.completedVisibleSeconds != newSettings.completedVisibleSeconds ||
            old.speechStyle != newSettings.speechStyle ||
            old.maxVisibleBubbles != newSettings.maxVisibleBubbles ||
            old.bubbleScale != newSettings.bubbleScale
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
        val prospective = selectSpeechItems(manual = speechManualMode && speechShown)
        val contentChanged = prospective.any { speechToken(it) !in previousTokens }
        refreshSpeechItems(allowAutoShow = true, contentChanged = contentChanged)
        val dominantState = taskAnimationState()
        if (dominantState == PetAnimationState.WAITING || dominantState == PetAnimationState.FAILED) {
            renderState(dominantState, force = true)
        } else {
            renderTaskAnimation()
        }
    }

    fun onTaskTransition(transition: TaskTransition) {
        val dominantState = taskAnimationState()
        val recoveredConnection = transition.fromCue == TaskAnimationCue.DISCONNECTED ||
            transition.fromCue == TaskAnimationCue.RECONNECTING
        when {
            transition.toCue == TaskAnimationCue.WAITING_FOR_INPUT ->
                renderState(PetAnimationState.WAITING, force = true)
            transition.toCue == TaskAnimationCue.DISCONNECTED ||
                transition.toCue == TaskAnimationCue.FAILED ||
                transition.to == TaskStatus.ERROR && transition.toCue != TaskAnimationCue.RECONNECTING -> renderState(
                    if (dominantState == PetAnimationState.WAITING) PetAnimationState.WAITING else PetAnimationState.FAILED,
                    force = true,
                )
            transition.toCue == TaskAnimationCue.RECONNECTING ->
                renderState(PetAnimationState.WAITING, force = true)
            dominantState == PetAnimationState.WAITING || dominantState == PetAnimationState.FAILED ->
                renderState(dominantState, force = true)
            transition.toCue == TaskAnimationCue.COMPLETED || transition.to == TaskStatus.COMPLETED ->
                playOneShot(PetAnimationState.JUMPING)
            recoveredConnection && transition.toCue in setOf(
                TaskAnimationCue.ACTIVE,
                TaskAnimationCue.REVIEWING,
                TaskAnimationCue.UNKNOWN,
            ) -> playOneShot(PetAnimationState.WAVING)
            transition.liveNotification && transition.kind == TaskKind.CHAT_MESSAGE ->
                playOneShot(PetAnimationState.WAVING)
            else -> renderTaskAnimation()
        }
    }

    fun onConfigurationChanged() {
        cancelSnapAnimation()
        dragging = false
        removeMenu()
        val params = petParams ?: return
        val size = dp(settings.petSizeDp)
        applySavedPosition(params, size)
        updatePetLayout()
        if (speechShown) renderSpeechWindows()
        renderTaskAnimation(force = true)
    }

    fun showMoreSpeech() {
        if (!speechShown) {
            autoSpeechSuppressed = false
            showSpeech(manual = !settings.autoTaskBubblesEnabled)
            return
        }
        if (speechItems.size > renderedPageSize.coerceAtLeast(1)) {
            advanceSpeechPage()
            renderSpeechWindows()
        }
    }

    fun previewAnimation(state: PetAnimationState? = null): Boolean {
        val visual = pet ?: return false
        if (visual.frameSequences.isEmpty() || !animationsAllowed()) return false
        if (state == null) {
            val first = if (visual.frameSequences.containsKey(PetAnimationState.WAVING)) {
                PetAnimationState.WAVING
            } else {
                visual.frameSequences.keys.first()
            }
            playOneShot(first) {
                if (first != PetAnimationState.JUMPING && visual.frameSequences.containsKey(PetAnimationState.JUMPING)) {
                    playOneShot(PetAnimationState.JUMPING)
                } else {
                    renderTaskAnimation(force = true)
                }
            }
            return true
        }
        val sequence = visual.frameSequences[state] ?: return false
        cancelTransient()
        val image = petImage ?: return false
        stopNativeAnimation()
        displayedState = state
        displayedSequence = sequence
        val animation = animationDrawable(sequence, oneShot = false)
        image.setImageDrawable(animation)
        image.post { if (image.drawable === animation) animation.start() }
        val restore = Runnable {
            transientRunnable = null
            renderTaskAnimation(force = true)
        }
        transientRunnable = restore
        handler.postDelayed(restore, PREVIEW_STATE_MS)
        return true
    }

    private fun onPetTouch(view: View, event: MotionEvent): Boolean {
        val params = petParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelSnapAnimation()
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                downWindowX = params.x
                downWindowY = params.y
                moved = false
                dragging = false
                longPressTriggered = false
                handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val motionDx = event.rawX - lastRawX
                if (!moved && hypot(dx.toDouble(), dy.toDouble()) > touchSlop.toDouble()) {
                    moved = true
                    dragging = true
                    handler.removeCallbacks(longPressRunnable)
                    removeMenu()
                }
                if (moved) {
                    params.x = downWindowX + dx.roundToInt()
                    params.y = downWindowY + dy.roundToInt()
                    clampPosition(params, dp(settings.petSizeDp))
                    updatePetLayout()
                    if (speechShown) repositionSpeechWindows()
                    if (abs(motionDx) >= 0.5f) {
                        renderState(
                            if (motionDx >= 0f) PetAnimationState.RUNNING_RIGHT
                            else PetAnimationState.RUNNING_LEFT,
                        )
                    }
                }
                lastRawX = event.rawX
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                dragging = false
                if (moved) {
                    if (settings.snapEnabled) {
                        snapToNearestEdge()
                    } else {
                        savePosition()
                        finishSpeechMotion()
                        renderTaskAnimation(force = true)
                    }
                } else if (!longPressTriggered) {
                    view.performClick()
                    toggleSpeech()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                dragging = false
                if (moved) savePosition()
                finishSpeechMotion()
                renderTaskAnimation(force = true)
                return true
            }
        }
        return false
    }

    private fun toggleSpeech() {
        if (speechShown) {
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
        pageStart = 0
        speechShown = true
        renderSpeechWindows()
    }

    private fun refreshSpeechItems(allowAutoShow: Boolean, contentChanged: Boolean) {
        handler.removeCallbacks(speechExpirationRunnable)
        val wasVisible = speechShown
        val oldFirstKey = currentPageItems().firstOrNull()?.task?.sourceNotificationKey
        val manual = speechManualMode && wasVisible
        speechItems = selectSpeechItems(manual)
        if (contentChanged) {
            autoSpeechSuppressed = false
            pageStart = 0
        } else if (oldFirstKey != null) {
            val index = speechItems.indexOfFirst { it.task.sourceNotificationKey == oldFirstKey }
            if (index >= 0) pageStart = index
        }
        pageStart = pageStart.coerceIn(0, (speechItems.size - 1).coerceAtLeast(0))

        when {
            speechItems.isEmpty() && !manual -> hideSpeech(suppressAutomatic = false)
            wasVisible -> renderSpeechWindows()
            allowAutoShow && settings.autoTaskBubblesEnabled && !autoSpeechSuppressed && speechItems.isNotEmpty() ->
                showSpeech(manual = false)
        }
        scheduleSpeechExpiration()
    }

    private fun selectSpeechItems(manual: Boolean): List<PetSpeechItem> = PetSpeechSelector.select(
        tasks,
        if (manual) manualSpeechPolicy() else automaticSpeechPolicy(),
    ).map { PetSpeechFormatter.format(it, settings.speechStyle) }

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
        speechShown = false
        pageStart = 0
        speechRenderPending = false
        handler.removeCallbacks(speechRotationRunnable)
        removeSpeechWindows()
        renderTaskAnimation(force = true)
    }

    private fun renderSpeechWindows() {
        handler.removeCallbacks(speechRotationRunnable)
        if (motionInProgress()) {
            speechRenderPending = true
            return
        }
        speechRenderPending = false
        removeSpeechWindows(keepShownState = true)
        if (!speechShown || speechItems.isEmpty() || petParams == null) return

        val page = currentPageItems()
        if (page.isEmpty()) return
        val metrics = speechMetrics(page)
        val safe = safeBounds()
        val petLayout = petParams ?: return
        val petSize = dp(settings.petSizeDp)
        val bubbleScale = settings.bubbleScale.coerceIn(0.75f, 1.5f)
        val margin = dp(((settings.petSizeDp / 28f) * bubbleScale).roundToInt().coerceIn(2, 9))
        val gap = dp((6f * bubbleScale).roundToInt().coerceIn(4, 9))

        val prepared = page.mapIndexed { index, item ->
            buildSpeechView(item, pageStart + index, speechItems.size, metrics.width, metrics)
        }
        prepared.forEach { preparedBubble ->
            preparedBubble.root.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(safe.bottom - safe.top, View.MeasureSpec.AT_MOST),
            )
        }
        val minimumHeight = dp((48f * bubbleScale).roundToInt().coerceIn(40, 72))
        val allHeights = prepared.map { it.root.measuredHeight.coerceAtLeast(minimumHeight) }
        val fitCount = SpeechBubblePageFit.fittingCount(
            heights = allHeights,
            gap = gap,
            availableHeight = safe.bottom - safe.top,
        ).coerceIn(1, prepared.size)
        val visiblePrepared = prepared.take(fitCount)
        val heights = allHeights.take(fitCount)
        renderedPageSize = fitCount
        val placements = SpeechBubblePlacement.calculate(
            safe = safe.toOverlayBounds(),
            petX = petLayout.x,
            petY = petLayout.y,
            petSize = petSize,
            bubbleWidth = metrics.width,
            bubbleHeights = heights,
            margin = margin,
            gap = gap,
        )

        visiblePrepared.zip(placements).forEach { (bubble, placement) ->
            configureTail(bubble, placement.anchor.toTailEdge(), placement.tailOffset)
            addSpeechWindow(bubble, placement.x, placement.y)
        }

        if (speechItems.size > renderedPageSize) {
            handler.postDelayed(speechRotationRunnable, SPEECH_PAGE_ROTATION_MS)
        }
    }

    private fun repositionSpeechWindows() {
        if (!speechShown || speechWindows.isEmpty()) return
        val petLayout = petParams ?: return
        val safe = safeBounds()
        val petSize = dp(settings.petSizeDp)
        val bubbleScale = settings.bubbleScale.coerceIn(0.75f, 1.5f)
        val margin = dp(((settings.petSizeDp / 28f) * bubbleScale).roundToInt().coerceIn(2, 9))
        val gap = dp((6f * bubbleScale).roundToInt().coerceIn(4, 9))
        val minimumHeight = dp((48f * bubbleScale).roundToInt().coerceIn(40, 72))
        val bubbleWidth = speechWindows.first().params.width
        val heights = speechWindows.map { window ->
            window.bubble.root.measuredHeight.coerceAtLeast(minimumHeight)
        }
        val placements = SpeechBubblePlacement.calculate(
            safe = safe.toOverlayBounds(),
            petX = petLayout.x,
            petY = petLayout.y,
            petSize = petSize,
            bubbleWidth = bubbleWidth,
            bubbleHeights = heights,
            margin = margin,
            gap = gap,
        )

        speechWindows.zip(placements).forEach { (window, placement) ->
            configureTail(window.bubble, placement.anchor.toTailEdge(), placement.tailOffset)
            window.params.x = placement.x
            window.params.y = placement.y
            runCatching { windowManager.updateViewLayout(window.bubble.root, window.params) }
                .onFailure { AppGraph.diagnostics.error("move speech bubble: ${it.javaClass.simpleName}") }
        }
    }

    private fun finishSpeechMotion() {
        if (!speechShown) {
            speechRenderPending = false
            return
        }
        if (speechRenderPending) {
            renderSpeechWindows()
        } else {
            repositionSpeechWindows()
        }
    }

    private fun currentPageItems(): List<PetSpeechItem> {
        if (speechItems.isEmpty()) return emptyList()
        val limit = currentPageLimit()
        if (pageStart >= speechItems.size) pageStart = 0
        return speechItems.drop(pageStart).take(limit).ifEmpty {
            pageStart = 0
            speechItems.take(limit)
        }
    }

    private fun currentPageLimit(): Int = settings.maxVisibleBubbles.coerceIn(1, MAX_BUBBLES)

    private fun advanceSpeechPage() {
        if (speechItems.isEmpty()) {
            pageStart = 0
            return
        }
        val step = renderedPageSize.takeIf { it > 0 } ?: currentPageLimit()
        pageStart = (pageStart + step) % speechItems.size
    }

    private fun buildSpeechView(
        item: PetSpeechItem,
        absoluteIndex: Int,
        total: Int,
        width: Int,
        metrics: SpeechMetrics,
    ): PreparedSpeechBubble {
        val bubble = SpeechBubbleDrawable(
            color = SPEECH_COLOR,
            strokeColor = SPEECH_STROKE_COLOR,
            cornerRadiusPx = metrics.radius,
            tailSizePx = metrics.tailSize.toFloat(),
            strokeWidthPx = metrics.strokeWidth.toFloat(),
        )
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(metrics.horizontalPadding, metrics.verticalPadding, metrics.horizontalPadding, metrics.verticalPadding)
        }
        val title = item.title?.trim()?.takeIf(String::isNotBlank)
        if (title != null && title.length <= RUN_IN_TITLE_MAX) {
            val cleanTitle = title.removeSuffix(":")
            val combined = SpannableStringBuilder()
                .append(cleanTitle)
                .append(": ")
                .append(item.text)
            combined.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                cleanTitle.length + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            content.addView(label(combined, metrics.bodySp, SPEECH_TEXT_COLOR).apply {
                maxLines = metrics.maxLines
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.08f)
            })
        } else {
            if (title != null) {
                content.addView(label(title, metrics.titleSp, Color.WHITE, bold = true).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            content.addView(label(item.text, metrics.bodySp, SPEECH_TEXT_COLOR).apply {
                maxLines = metrics.maxLines
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.08f)
            })
        }

        if (total > currentPageLimit() || !item.task.hasExactOpenTarget()) {
            val footer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setPadding(0, metrics.footerTopPadding, 0, 0)
            }
            val dot = View(context).apply {
                background = roundedBackground(priorityColor(item.priority), metrics.dotRadiusDp)
            }
            footer.addView(dot, LinearLayout.LayoutParams(metrics.dotSize, metrics.dotSize).apply {
                marginEnd = metrics.dotMarginEnd
            })
            val meta = buildString {
                if (total > currentPageLimit()) append("${absoluteIndex + 1}/$total")
                if (!item.task.hasExactOpenTarget()) {
                    if (isNotEmpty()) append(" · ")
                    append("открыть ChatGPT")
                }
            }
            footer.addView(label(meta, metrics.metaSp, MUTED_COLOR, bold = true))
            content.addView(footer, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val root = FrameLayout(context).apply {
            background = bubble
            clipChildren = false
            clipToPadding = false
            elevation = dp(4).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(
                R.string.open_speech_for,
                item.title ?: item.text.take(80),
            )
            addView(content, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val opened = TaskOpener.openTask(context, item.task, settings.sourcePackage)
                if (opened) {
                    hideSpeech(suppressAutomatic = true)
                } else {
                    AppGraph.diagnostics.error("Unable to open ChatGPT PendingIntent or launcher")
                }
            }
        }
        return PreparedSpeechBubble(root, bubble, metrics.tailSize, width)
    }

    private fun configureTail(bubble: PreparedSpeechBubble, edge: TailEdge, offset: Float) {
        bubble.drawable.pointTo(edge, offset)
        val tail = bubble.tailSize
        val left = if (edge == TailEdge.LEFT) tail else 0
        val top = if (edge == TailEdge.TOP) tail else 0
        val right = if (edge == TailEdge.RIGHT) tail else 0
        val bottom = if (edge == TailEdge.BOTTOM) tail else 0
        if (bubble.root.paddingLeft != left ||
            bubble.root.paddingTop != top ||
            bubble.root.paddingRight != right ||
            bubble.root.paddingBottom != bottom
        ) {
            bubble.root.setPadding(left, top, right, bottom)
        }
    }

    private fun BubbleAnchor.toTailEdge(): TailEdge = when (this) {
        BubbleAnchor.LEFT -> TailEdge.LEFT
        BubbleAnchor.RIGHT -> TailEdge.RIGHT
        BubbleAnchor.TOP -> TailEdge.TOP
        BubbleAnchor.BOTTOM -> TailEdge.BOTTOM
    }

    private fun SafeBounds.toOverlayBounds(): OverlayBounds = OverlayBounds(left, top, right, bottom)

    private fun addSpeechWindow(bubble: PreparedSpeechBubble, x: Int, y: Int) {
        val params = baseParams(bubble.width, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            this.x = x
            this.y = y
        }
        runCatching { windowManager.addView(bubble.root, params) }
            .onFailure { AppGraph.diagnostics.error("add speech bubble: ${it.javaClass.simpleName}") }
            .onSuccess { speechWindows += SpeechWindow(bubble, params) }
    }

    private fun removeSpeechWindows(keepShownState: Boolean = false) {
        speechWindows.forEach { window ->
            runCatching { windowManager.removeViewImmediate(window.bubble.root) }
        }
        speechWindows.clear()
        renderedPageSize = 0
        if (!keepShownState) {
            speechShown = false
            speechRenderPending = false
        }
    }

    private fun showMenu() {
        removeMenu()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(MENU_COLOR, 18f, SPEECH_STROKE_COLOR)
            elevation = dp(6).toFloat()
        }
        fun addAction(title: String, action: () -> Unit) {
            container.addView(label(title, 14f, Color.WHITE).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    action()
                    removeMenu()
                }
            })
        }
        addAction("Открыть текущий чат") { openBestCurrent() }
        addAction("Настройки реплик") {
            context.startActivity(
                Intent(context, com.fourerk.codexpet.app.SpeechSettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        addAction("Настройки Codex Pet") {
            context.startActivity(Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        addAction(context.getString(R.string.hide_pet), ::hidePet)
        val width = dp(230)
        val params = baseParams(width, WindowManager.LayoutParams.WRAP_CONTENT)
        val placement = menuPlacement(width, dp(215))
        params.x = placement.first
        params.y = placement.second
        runCatching { windowManager.addView(container, params) }
            .onFailure { AppGraph.diagnostics.error("add long-press menu: ${it.javaClass.simpleName}") }
            .onSuccess {
                menuView = container
                menuParams = params
            }
    }

    private fun openBestCurrent() {
        val exact = currentPageItems().firstOrNull { it.task.hasExactOpenTarget() }?.task
        if (exact != null && TaskOpener.openTask(context, exact, settings.sourcePackage)) return
        TaskOpener.openBestAvailable(context, tasks, settings.sourcePackage)
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
        task.isDisplayTask() && (task.kind != TaskKind.CHAT_MESSAGE || settings.chatMessageBubblesEnabled)
    }

    private fun renderTaskAnimation(force: Boolean = false) {
        if (transientRunnable != null && !force) return
        if (force) cancelTransient()
        renderState(taskAnimationState(), force)
    }

    private fun taskAnimationState(): PetAnimationState = PetAnimationStateResolver.resolve(displayTasks())

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
        val speech = speechWindows.firstOrNull()
        val image = petImage
        if (directions.size != 16 || petLayout == null || speech == null || image == null || !speechShown) {
            renderTaskAnimation(force = true)
            return
        }
        cancelTransient()
        stopNativeAnimation()
        val petCenterX = petLayout.x + dp(settings.petSizeDp) / 2f
        val petCenterY = petLayout.y + dp(settings.petSizeDp) / 2f
        val speechCenterX = speech.params.x + speech.params.width / 2f
        val speechCenterY = speech.params.y + speech.bubble.root.measuredHeight / 2f
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

    private fun animationsAllowed(): Boolean = settings.animationsEnabled && ValueAnimator.areAnimatorsEnabled()

    private fun cancelTransient() {
        transientRunnable?.let(handler::removeCallbacks)
        transientRunnable = null
    }

    private fun stopNativeAnimation() {
        (petImage?.drawable as? Animatable)?.stop()
    }

    private fun motionInProgress(): Boolean = dragging || snapAnimator?.isRunning == true

    private fun cancelSnapAnimation() {
        snapAnimator?.let { animator ->
            animator.removeAllListeners()
            animator.cancel()
        }
        snapAnimator = null
    }

    private fun snapToNearestEdge() {
        val params = petParams ?: return
        val root = petRoot ?: return
        val safe = safeBounds()
        val size = dp(settings.petSizeDp)
        val left = safe.left
        val right = (safe.right - size).coerceAtLeast(left)
        val target = if (abs(params.x - left) <= abs(params.x - right)) left else right
        if (params.x == target) {
            savePosition()
            finishSpeechMotion()
            renderTaskAnimation(force = true)
            return
        }

        renderState(
            if (target < params.x) PetAnimationState.RUNNING_LEFT else PetAnimationState.RUNNING_RIGHT,
            force = true,
        )
        val animator = ValueAnimator.ofInt(params.x, target).apply {
            duration = 180L
        }
        snapAnimator = animator
        var cancelled = false
        animator.addUpdateListener {
            params.x = it.animatedValue as Int
            runCatching { windowManager.updateViewLayout(root, params) }
            if (speechShown) repositionSpeechWindows()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
                if (snapAnimator === animation) snapAnimator = null
                renderTaskAnimation(force = true)
            }

            override fun onAnimationEnd(animation: Animator) {
                if (snapAnimator === animation) snapAnimator = null
                if (!cancelled) {
                    savePosition()
                    finishSpeechMotion()
                    renderTaskAnimation(force = true)
                }
            }
        })
        animator.start()
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

    private fun updatePetLayout() {
        val root = petRoot ?: return
        val params = petParams ?: return
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun repositionMenu() {
        val view = menuView ?: return
        val params = menuParams ?: return
        val placement = menuPlacement(params.width, view.measuredHeight.takeIf { it > 0 } ?: dp(215))
        params.x = placement.first
        params.y = placement.second
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun menuPlacement(width: Int, estimatedHeight: Int): Pair<Int, Int> {
        val petLayout = petParams ?: return 0 to 0
        val safe = safeBounds()
        val size = dp(settings.petSizeDp)
        val margin = dp(6)
        val rightX = petLayout.x + size + margin
        val leftX = petLayout.x - width - margin
        val x = when {
            rightX + width <= safe.right -> rightX
            leftX >= safe.left -> leftX
            else -> (petLayout.x + size / 2 - width / 2)
                .coerceIn(safe.left, (safe.right - width).coerceAtLeast(safe.left))
        }
        val y = petLayout.y.coerceIn(safe.top, (safe.bottom - estimatedHeight).coerceAtLeast(safe.top))
        return x to y
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

    private fun speechMetrics(page: List<PetSpeechItem>): SpeechMetrics {
        val petDp = settings.petSizeDp
        val scale = settings.bubbleScale.coerceIn(0.75f, 1.5f)
        val longest = page.maxOfOrNull { (it.title?.length ?: 0) + it.text.length } ?: 80
        val baseWidthDp = when {
            longest < 55 -> (petDp * 2.2f).roundToInt().coerceIn(158, 230)
            longest < 130 -> (petDp * 2.65f).roundToInt().coerceIn(180, 276)
            else -> (petDp * 2.9f).roundToInt().coerceIn(200, 310)
        }
        val safe = safeBounds()
        val maximumWidthPx = (safe.right - safe.left - dp(24)).coerceAtLeast(dp(140))
        val widthPx = minOf(
            dp((baseWidthDp * scale).roundToInt()),
            maximumWidthPx,
        )
        val baseTailDp = (petDp / 8f).coerceIn(8f, 16f)
        val baseRadiusDp = (petDp / 4f).coerceIn(14f, 26f)
        val baseHorizontalPaddingDp = (petDp / 6f).coerceIn(10f, 20f)
        val baseVerticalPaddingDp = (petDp / 10f).coerceIn(7f, 14f)
        val dotDp = (6f * scale).roundToInt().coerceIn(4, 9)
        return SpeechMetrics(
            width = widthPx,
            tailSize = dp((baseTailDp * scale).roundToInt().coerceIn(6, 24)),
            radius = dp((baseRadiusDp * scale).roundToInt().coerceIn(10, 39)).toFloat(),
            horizontalPadding = dp((baseHorizontalPaddingDp * scale).roundToInt().coerceIn(8, 30)),
            verticalPadding = dp((baseVerticalPaddingDp * scale).roundToInt().coerceIn(5, 21)),
            titleSp = ((petDp * 0.15f).coerceIn(12f, 16f) * scale).coerceIn(9f, 24f),
            bodySp = ((petDp * 0.16f).coerceIn(12.5f, 17f) * scale).coerceIn(9.5f, 25.5f),
            metaSp = ((petDp * 0.12f).coerceIn(10f, 12.5f) * scale).coerceIn(8f, 18.5f),
            maxLines = when {
                page.size >= 4 && scale < 0.9f -> 4
                page.size >= 4 -> 3
                petDp < 64 -> 3
                else -> 4
            },
            footerTopPadding = dp((3f * scale).roundToInt().coerceIn(2, 5)),
            dotSize = dp(dotDp),
            dotMarginEnd = dp((6f * scale).roundToInt().coerceIn(4, 9)),
            dotRadiusDp = (3f * scale).coerceIn(2f, 4.5f),
            strokeWidth = dp(if (scale >= 1.35f) 2 else 1),
        )
    }

    private fun priorityColor(priority: PetSpeechPriority): Int = when (priority) {
        PetSpeechPriority.NEEDS_INPUT -> 0xFFFFC857.toInt()
        PetSpeechPriority.BLOCKED -> 0xFFFF7474.toInt()
        PetSpeechPriority.READY -> 0xFF77D7B1.toInt()
        PetSpeechPriority.RUNNING -> 0xFF55B7FF.toInt()
    }

    private fun roundedBackground(color: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.roundToInt()).toFloat()
            strokeColor?.let { setStroke(dp(1), it) }
        }

    private fun label(value: CharSequence, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

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
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private data class SpeechWindow(
        val bubble: PreparedSpeechBubble,
        val params: WindowManager.LayoutParams,
    )
    private data class PreparedSpeechBubble(
        val root: FrameLayout,
        val drawable: SpeechBubbleDrawable,
        val tailSize: Int,
        val width: Int,
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
        val footerTopPadding: Int,
        val dotSize: Int,
        val dotMarginEnd: Int,
        val dotRadiusDp: Float,
        val strokeWidth: Int,
    )
    private data class SafeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private companion object {
        const val MAX_BUBBLES = 5
        const val RUN_IN_TITLE_MAX = 28
        const val SPEECH_PAGE_ROTATION_MS = 7_000L
        const val MANUAL_HISTORY_SECONDS = 60 * 60
        const val LOOK_HOLD_MS = 1_100L
        const val PREVIEW_STATE_MS = 2_200L
        const val SPEECH_COLOR = 0xF226292E.toInt()
        const val SPEECH_STROKE_COLOR = 0xFF535962.toInt()
        const val SPEECH_TEXT_COLOR = 0xFFF1F3F4.toInt()
        const val MUTED_COLOR = 0xFFAEB5BB.toInt()
        const val MENU_COLOR = 0xF22B2E33.toInt()
    }
}
