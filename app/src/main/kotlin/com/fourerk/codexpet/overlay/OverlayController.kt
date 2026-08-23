package com.fourerk.codexpet.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.fourerk.codexpet.R
import com.fourerk.codexpet.app.AppGraph
import com.fourerk.codexpet.app.DiagnosticsActivity
import com.fourerk.codexpet.app.MainActivity
import com.fourerk.codexpet.pet.PetVisual
import com.fourerk.codexpet.settings.AppSettings
import com.fourerk.codexpet.settings.LongPressAction
import com.fourerk.codexpet.system.TaskOpener
import com.fourerk.codexpet.task.CodexTask
import com.fourerk.codexpet.task.TaskStatus
import com.fourerk.codexpet.task.TaskTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
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
    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var transformAnimator: Animator? = null
    private var positionOrientation: Int = Configuration.ORIENTATION_UNDEFINED

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
            visibility = if (pet == null) View.INVISIBLE else View.VISIBLE
        }
        val params = baseParams(size, size)
        applySavedPosition(params, size)
        runCatching { windowManager.addView(root, params) }
            .onFailure { AppGraph.diagnostics.error("add pet overlay: ${it.javaClass.simpleName}") }
            .onSuccess {
                petRoot = root
                petImage = image
                petParams = params
                positionOrientation = orientation()
                setPet(pet)
            }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        transformAnimator?.cancel()
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
        settings = newSettings
        val root = petRoot ?: return
        root.visibility = if (newSettings.overlayEnabled && pet != null) View.VISIBLE else View.INVISIBLE
        if (oldSize != newSettings.petSizeDp) {
            val size = dp(newSettings.petSizeDp)
            petParams?.let { params ->
                params.width = size
                params.height = size
                clampPosition(params, size)
                updatePetLayout()
            }
            if (panelView != null) rebuildPanel()
            if (menuView != null) showMenu()
        }
        if (oldAnimations != newSettings.animationsEnabled || oldSpeed != newSettings.animationSpeed) {
            updateNativeAnimation()
            startSteadyAnimation()
        }
    }

    fun setPet(newPet: PetVisual?) {
        pet = newPet
        petRoot?.visibility = if (newPet != null && settings.overlayEnabled) View.VISIBLE else View.INVISIBLE
        petImage?.setImageDrawable(newPet?.drawable)
        updateNativeAnimation()
        startSteadyAnimation()
    }

    fun setTasks(newTasks: List<CodexTask>) {
        tasks = newTasks
        if (panelView != null) rebuildPanel()
        startSteadyAnimation()
    }

    fun onTaskTransition(transition: TaskTransition) {
        if (tasks.any { it.status == TaskStatus.RUNNING }) {
            startSteadyAnimation()
            return
        }
        when (transition.to) {
            TaskStatus.COMPLETED -> playTransient(PetState.SUCCESS)
            TaskStatus.ERROR -> playTransient(PetState.ERROR)
            else -> startSteadyAnimation()
        }
    }

    fun onConfigurationChanged() {
        removePanel()
        removeMenu()
        val params = petParams ?: return
        val size = dp(settings.petSizeDp)
        applySavedPosition(params, size)
        positionOrientation = orientation()
        updatePetLayout()
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
                    removePanel()
                    removeMenu()
                }
                if (moved) {
                    params.x = downWindowX + dx.roundToInt()
                    params.y = downWindowY + dy.roundToInt()
                    clampPosition(params, dp(settings.petSizeDp))
                    updatePetLayout()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (moved) {
                    if (settings.snapEnabled) snapToNearestEdge() else savePosition()
                } else if (!longPressTriggered) {
                    view.performClick()
                    togglePanel()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (moved) savePosition()
                return true
            }
        }
        return false
    }

    private fun togglePanel() {
        if (panelView != null) removePanel() else showPanel()
    }

    private fun showPanel() {
        removeMenu()
        removePanel()
        val visibleTasks = visibleTasks()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            background = roundedBackground(0xF21B1E21.toInt(), 18f)
            elevation = dp(8).toFloat()
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("Codex", 18f, Color.WHITE, bold = true))
            addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(label("${visibleTasks.count { it.status == TaskStatus.RUNNING }} active", 12f, 0xFF9AA3AA.toInt()))
        }
        container.addView(header)
        container.addView(Space(context), LinearLayout.LayoutParams(1, dp(8)))
        if (visibleTasks.isEmpty()) {
            container.addView(label(context.getString(R.string.no_tasks), 13f, 0xFFB9C0C5.toInt()))
        } else {
            visibleTasks.take(MAX_PANEL_TASKS).forEach { container.addView(taskRow(it)) }
        }
        val openAll = Button(context).apply {
            text = context.getString(R.string.all_tasks)
            isAllCaps = false
            setOnClickListener {
                TaskOpener.openBestAvailable(context, tasks, settings.sourcePackage)
                removePanel()
            }
        }
        container.addView(openAll, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val safe = safeBounds()
        val width = min(dp(336), safe.right - safe.left - dp(24)).coerceAtLeast(dp(220))
        val params = baseParams(width, WindowManager.LayoutParams.WRAP_CONTENT)
        placeAdjacent(params, width, estimatedHeight = dp(300))
        runCatching { windowManager.addView(container, params) }
            .onFailure { AppGraph.diagnostics.error("add task panel: ${it.javaClass.simpleName}") }
            .onSuccess {
                panelView = container
                panelParams = params
                container.post {
                    placeAdjacent(params, width, container.measuredHeight)
                    runCatching { windowManager.updateViewLayout(container, params) }
                }
            }
    }

    private fun rebuildPanel() {
        removePanel()
        showPanel()
    }

    private fun taskRow(task: CodexTask): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(8), 0, dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                TaskOpener.openTask(context, task, settings.sourcePackage)
                removePanel()
            }
        }
        val dotColor = when (task.status) {
            TaskStatus.RUNNING -> 0xFF10A37F.toInt()
            TaskStatus.COMPLETED -> 0xFF7DD3B0.toInt()
            TaskStatus.ERROR -> 0xFFFF6B6B.toInt()
            TaskStatus.UNKNOWN -> 0xFF7D858C.toInt()
        }
        row.addView(View(context).apply {
            background = roundedBackground(dotColor, 6f)
        }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { topMargin = dp(5); marginEnd = dp(10) })
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(task.title, 14f, Color.WHITE, bold = true).apply { maxLines = 1 })
            task.summary?.takeIf(String::isNotBlank)?.let { summary ->
                addView(label(summary, 12f, 0xFFBEC5CA.toInt()).apply { maxLines = 1 })
            }
            val age = DateUtils.getRelativeTimeSpanString(
                task.updatedAt.toEpochMilli(),
                System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            )
            val progress = task.progress?.let {
                if (it.indeterminate) " · …" else if (it.max > 0) " · ${it.value}/${it.max}" else ""
            }.orEmpty()
            addView(label("$age$progress", 11f, 0xFF838C93.toInt()))
        }
        row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun showMenu() {
        removePanel()
        removeMenu()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBackground(0xF21B1E21.toInt(), 16f)
            elevation = dp(8).toFloat()
        }
        fun addAction(title: String, action: () -> Unit) {
            container.addView(Button(context).apply {
                text = title
                isAllCaps = false
                setOnClickListener { action(); removeMenu() }
            }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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
        val width = dp(220)
        val params = baseParams(width, WindowManager.LayoutParams.WRAP_CONTENT)
        placeAdjacent(params, width, estimatedHeight = dp(220))
        runCatching { windowManager.addView(container, params) }
            .onFailure { AppGraph.diagnostics.error("add long-press menu: ${it.javaClass.simpleName}") }
            .onSuccess {
                menuView = container
                menuParams = params
                container.post {
                    placeAdjacent(params, width, container.measuredHeight)
                    runCatching { windowManager.updateViewLayout(container, params) }
                }
            }
    }

    private fun hidePet() {
        context.startService(Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_HIDE))
    }

    private fun removePanel() {
        panelView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        panelView = null
        panelParams = null
    }

    private fun removeMenu() {
        menuView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        menuView = null
        menuParams = null
    }

    private fun visibleTasks(): List<CodexTask> {
        val now = Instant.now()
        return tasks.filter { task ->
            task.status != TaskStatus.COMPLETED ||
                (settings.completedVisibleSeconds > 0 &&
                    Duration.between(task.updatedAt, now).seconds <= settings.completedVisibleSeconds)
        }
    }

    private fun startSteadyAnimation() {
        val state = if (tasks.any { it.status == TaskStatus.RUNNING }) PetState.RUNNING else PetState.IDLE
        startTransformAnimation(state)
    }

    private fun startTransformAnimation(state: PetState) {
        val image = petImage ?: return
        transformAnimator?.cancel()
        resetTransforms(image)
        if (!settings.animationsEnabled || !ValueAnimator.areAnimatorsEnabled() || pet == null) return
        val speed = settings.animationSpeed.coerceIn(0.5f, 2f)
        val animators = when (state) {
            PetState.IDLE -> listOf(
                repeating(image, "scaleX", floatArrayOf(1f, 1.015f, 1f), (3_200 / speed).toLong()),
                repeating(image, "scaleY", floatArrayOf(1f, 1.015f, 1f), (3_200 / speed).toLong()),
                repeating(image, "translationY", floatArrayOf(0f, -dp(2).toFloat(), 0f), (3_200 / speed).toLong()),
            )
            PetState.RUNNING -> listOf(
                repeating(image, "translationY", floatArrayOf(0f, -dp(4).toFloat(), 0f), (900 / speed).toLong()),
                repeating(image, "rotation", floatArrayOf(-2f, 2f, -2f), (1_200 / speed).toLong()),
            )
            else -> emptyList()
        }
        transformAnimator = AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    private fun playTransient(state: PetState) {
        val image = petImage ?: return
        if (!settings.animationsEnabled || !ValueAnimator.areAnimatorsEnabled() || pet == null) return
        transformAnimator?.cancel()
        resetTransforms(image)
        val animator = when (state) {
            PetState.SUCCESS -> AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(image, "scaleX", 1f, 1.1f, 0.97f, 1f),
                    ObjectAnimator.ofFloat(image, "scaleY", 1f, 1.1f, 0.97f, 1f),
                    ObjectAnimator.ofFloat(image, "translationY", 0f, -dp(8).toFloat(), 0f),
                )
                duration = 1_000L
            }
            PetState.ERROR -> ObjectAnimator.ofFloat(
                image,
                "translationX",
                0f,
                -dp(6).toFloat(),
                dp(6).toFloat(),
                -dp(4).toFloat(),
                dp(4).toFloat(),
                0f,
            ).apply { duration = 520L }
            else -> return
        }
        var cancelled = false
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) startSteadyAnimation()
            }
        })
        transformAnimator = animator
        animator.start()
    }

    private fun repeating(view: View, property: String, values: FloatArray, durationMs: Long): ObjectAnimator =
        ObjectAnimator.ofFloat(view, property, *values).apply {
            duration = durationMs.coerceAtLeast(250L)
            repeatCount = ValueAnimator.INFINITE
        }

    private fun resetTransforms(view: View) {
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.rotation = 0f
    }

    private fun updateNativeAnimation() {
        val animatable = petImage?.drawable as? Animatable ?: return
        if (settings.animationsEnabled && ValueAnimator.areAnimatorsEnabled()) animatable.start() else animatable.stop()
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
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = savePosition()
            })
            start()
        }
    }

    private fun savePosition() {
        val params = petParams ?: return
        val currentOrientation = orientation()
        scope.launch { AppGraph.settings.savePosition(currentOrientation, params.x, params.y) }
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

    private fun placeAdjacent(params: WindowManager.LayoutParams, width: Int, estimatedHeight: Int) {
        val petLayout = petParams ?: return
        val safe = safeBounds()
        val size = dp(settings.petSizeDp)
        val margin = dp(10)
        val rightX = petLayout.x + size + margin
        params.x = if (rightX + width <= safe.right) rightX else petLayout.x - width - margin
        params.x = params.x.coerceIn(safe.left, (safe.right - width).coerceAtLeast(safe.left))
        params.y = petLayout.y.coerceIn(safe.top, (safe.bottom - estimatedHeight).coerceAtLeast(safe.top))
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

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.roundToInt()).toFloat()
    }

    private fun label(text: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val MAX_PANEL_TASKS = 6
    }
}
