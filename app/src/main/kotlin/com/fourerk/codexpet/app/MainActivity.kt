package com.fourerk.codexpet.app

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.R
import com.fourerk.codexpet.notification.ChatGptNotificationListener
import com.fourerk.codexpet.overlay.OverlayService
import com.fourerk.codexpet.pet.PetAnimationState
import com.fourerk.codexpet.pet.PetVisual
import com.fourerk.codexpet.settings.AppSettings
import com.fourerk.codexpet.settings.LongPressAction
import com.fourerk.codexpet.system.SystemAccess
import com.fourerk.codexpet.task.TaskKind
import com.fourerk.codexpet.task.isCodexTask
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var preview: ImageView
    private lateinit var previewText: TextView
    private lateinit var animationStatusText: TextView
    private lateinit var startButton: Button
    private lateinit var sourcePackage: EditText
    private lateinit var sizeLabel: TextView
    private lateinit var sizeSeek: SeekBar
    private lateinit var snapSwitch: SwitchMaterial
    private lateinit var animationSwitch: SwitchMaterial
    private lateinit var speedLabel: TextView
    private lateinit var speedSeek: SeekBar
    private lateinit var autoStartSwitch: SwitchMaterial
    private lateinit var completedLabel: TextView
    private lateinit var completedSeek: SeekBar
    private lateinit var autoTaskBubblesSwitch: SwitchMaterial
    private lateinit var attentionBubblesSwitch: SwitchMaterial
    private lateinit var chatMessageBubblesSwitch: SwitchMaterial
    private lateinit var completionBubblesSwitch: SwitchMaterial
    private lateinit var longPressSpinner: Spinner
    private var bindingSettings = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { renderStatus() }

    private val importPet = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            AppGraph.pets.importManual(uri)
                .onSuccess { pet ->
                    val warning = if (pet.hasMeaningfulTransparency) "" else " Изображение почти непрозрачное."
                    val mode = if (pet.frameSequences.isNotEmpty()) {
                        "Pet pack импортирован: ${pet.frameSequences.size} анимаций."
                    } else {
                        "Изображение питомца импортировано."
                    }
                    toast("$mode$warning")
                }
                .onFailure { toast("Импорт не удался: ${it.message.orEmpty()}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        bindActions()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
    }

    private fun buildContent(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(36))
            setBackgroundColor(getColor(R.color.codex_background))
        }
        content.addView(text("Codex Pet", 30f, Color.WHITE, bold = true))
        content.addView(text("Прозрачный плавающий питомец и локальная диагностика ChatGPT notifications", 14f, 0xFFB8C0C6.toInt()))

        statusText = text("Проверяю состояние…", 14f, Color.WHITE)
        val statusCard = card("Первый запуск").apply {
            addView(statusText)
            addView(action("1. Доступ к уведомлениям") { SystemAccess.openNotificationListenerSettings(this@MainActivity) })
            if (Build.VERSION.SDK_INT >= 33) {
                addView(text(
                    "Если MagicOS пишет «Есть ограничения»: откройте сведения о Codex Pet, нажмите ⋮ и выберите «Разрешить настройки с ограниченным доступом».",
                    12f,
                    0xFFB8C0C6.toInt(),
                ))
                addView(action("Открыть сведения о Codex Pet") {
                    SystemAccess.openAppDetails(this@MainActivity)
                })
            }
            addView(action("2. Отображение поверх приложений") { SystemAccess.openOverlaySettings(this@MainActivity) })
            if (Build.VERSION.SDK_INT >= 33) {
                addView(action("3. Уведомления Codex Pet") {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            startButton = action("Запустить Codex Pet", ::toggleOverlay)
            addView(startButton)
        }
        content.addView(statusCard, cardParams())

        preview = ImageView(this).apply {
            background = null
            elevation = 0f
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Live pet preview"
        }
        previewText = text("Питомец ChatGPT пока не обнаружен. Откройте Codex Remote или запустите задачу.", 13f, 0xFFB8C0C6.toInt())
        content.addView(card("Live preview").apply {
            addView(preview, LinearLayout.LayoutParams.MATCH_PARENT, dp(150))
            addView(previewText)
            addView(action("Обновить из notifications") {
                ChatGptNotificationListener.refresh(this@MainActivity)
            })
            animationStatusText = text("Проверяю доступные анимации…", 12f, 0xFFB8C0C6.toInt())
            addView(animationStatusText)
            addView(action("Проверить анимацию") { previewPetAnimation() })
            addView(action("Импортировать pet pack / ZIP / изображение") {
                importPet.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "image/png",
                        "image/webp",
                        "image/*",
                    ),
                )
            })
            addView(text(
                "Pet pack: прозрачный PNG/WebP 1536×1872 либо v2 1536×2288. ZIP может содержать один такой spritesheet; пути и размер архива проверяются до импорта.",
                12f,
                0xFF8F989F.toInt(),
            ))
        }, cardParams())

        sourcePackage = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF7D858C.toInt())
            hint = "com.openai.chatgpt"
            isSingleLine = true
        }
        sizeLabel = text("Размер: 72 dp", 13f, Color.WHITE)
        sizeSeek = SeekBar(this).apply { max = 112 }
        snapSwitch = toggle("Snap к ближайшему краю")
        animationSwitch = toggle("Анимации оригинального pet pack")
        speedLabel = text("Скорость анимации: 1.00×", 13f, Color.WHITE)
        speedSeek = SeekBar(this).apply { max = 150 }
        completedLabel = text(completedVisibleText(5), 13f, Color.WHITE)
        completedSeek = SeekBar(this).apply { max = 30 }
        autoTaskBubblesSwitch = toggle("Автоматические реплики — общий выключатель")
        attentionBubblesSwitch = toggle("Показывать ошибки, потерю связи и ожидание ответа")
        chatMessageBubblesSwitch = toggle("Показывать сообщения из других чатов ChatGPT")
        completionBubblesSwitch = toggle("Показывать завершение задачи")
        autoStartSwitch = toggle("Запускать после перезагрузки")
        longPressSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Меню", "Открыть ChatGPT", "Скрыть питомца"),
            )
        }
        content.addView(card("Настройки").apply {
            addView(text("Источник notifications", 12f, 0xFF8F989F.toInt()))
            addView(sourcePackage)
            addView(action("Сохранить package") {
                lifecycleScope.launch {
                    AppGraph.settings.setSourcePackage(sourcePackage.text.toString())
                    ChatGptNotificationListener.refresh(this@MainActivity)
                }
            })
            addView(sizeLabel)
            addView(sizeSeek)
            addView(snapSwitch)
            addView(animationSwitch)
            addView(speedLabel)
            addView(speedSeek)
            addView(completedLabel)
            addView(completedSeek)
            addView(autoTaskBubblesSwitch)
            addView(attentionBubblesSwitch)
            addView(chatMessageBubblesSwitch)
            addView(completionBubblesSwitch)
            addView(text(
                "Выполнение показывается, пока активно. Сообщения и «готово» исчезают по таймеру. Ожидание ответа, ошибка и потеря связи остаются до изменения или удаления уведомления. Быстрый выключатель в уведомлении отключает все автоматические реплики.",
                12f,
                0xFF8F989F.toInt(),
            ))
            addView(autoStartSwitch)
            addView(text("Долгое нажатие", 12f, 0xFF8F989F.toInt()))
            addView(longPressSpinner)
        }, cardParams())

        content.addView(card("Интеграция").apply {
            addView(action("Diagnostics → ChatGPT notifications") {
                startActivity(android.content.Intent(this@MainActivity, DiagnosticsActivity::class.java))
            })
            addView(action("Открыть ChatGPT → Notifications → Bubbles") {
                SystemAccess.openChatGptBubbleSettings(
                    this@MainActivity,
                    AppGraph.settings.settings.value.sourcePackage,
                )
            })
            addView(text("После запуска Codex Pet отключите системные bubbles ChatGPT, чтобы белый круг не отображался одновременно. Обычные notifications оставьте включёнными.", 13f, 0xFFB8C0C6.toInt()))
        }, cardParams())

        if (SystemAccess.isHonorDevice()) {
            content.addView(card("Работа в фоне на HONOR").apply {
                addView(text("MagicOS: Настройки → Приложения → Запуск приложений → Codex Pet → отключить автоматическое управление и включить автозапуск, косвенный запуск и работу в фоне. Затем в «Оптимизация батареи» выбрать «Не разрешать» оптимизацию. При необходимости закрепите приложение в Recent apps.", 13f, 0xFFE0E4E7.toInt()))
                addView(action("Открыть настройки батареи") { SystemAccess.openBatteryOptimizationSettings(this@MainActivity) })
                addView(action("Открыть сведения о приложении") { SystemAccess.openAppDetails(this@MainActivity) })
            }, cardParams())
        }

        content.addView(text("Local-only: INTERNET, analytics, telemetry и чтение чужих файлов не используются.", 12f, 0xFF7F898F.toInt()))
        return ScrollView(this).apply { addView(content) }
    }

    private fun bindActions() {
        sizeSeek.setOnSeekBarChangeListener(seekListener(
            onProgress = { sizeLabel.text = getString(R.string.pet_size_format, it + 48) },
            onStop = { lifecycleScope.launch { AppGraph.settings.setPetSizeDp(it.progress + 48) } },
        ))
        speedSeek.setOnSeekBarChangeListener(seekListener(
            onProgress = { speedLabel.text = getString(R.string.animation_speed_format, (it + 50) / 100f) },
            onStop = { lifecycleScope.launch { AppGraph.settings.setAnimationSpeed((it.progress + 50) / 100f) } },
        ))
        completedSeek.setOnSeekBarChangeListener(seekListener(
            onProgress = { completedLabel.text = completedVisibleText(it) },
            onStop = { lifecycleScope.launch { AppGraph.settings.setCompletedVisibleSeconds(it.progress) } },
        ))
        snapSwitch.setOnCheckedChangeListener { _, checked ->
            if (!bindingSettings) lifecycleScope.launch { AppGraph.settings.setSnapEnabled(checked) }
        }
        animationSwitch.setOnCheckedChangeListener { _, checked ->
            if (!bindingSettings) lifecycleScope.launch { AppGraph.settings.setAnimationsEnabled(checked) }
        }
        autoStartSwitch.setOnCheckedChangeListener { _, checked ->
            if (!bindingSettings) lifecycleScope.launch { AppGraph.settings.setAutoStart(checked) }
        }
        autoTaskBubblesSwitch.setOnCheckedChangeListener { _, checked ->
            if (!bindingSettings) lifecycleScope.launch { AppGraph.settings.setAutoTaskBubblesEnabled(checked) }
        }
        attentionBubblesSwitch.setOnCheckedChangeListener { _, checked ->
            if (!bindingSettings) lifecycleScope.launch { AppGraph.settings.setAttentionBubblesEnabled(checked) }
        }
        chatMessageBubblesSwitch.setOnCheckedChangeListener { _, checked ->
            if (!bindingSettings) lifecycleScope.launch { AppGraph.settings.setChatMessageBubblesEnabled(checked) }
        }
        completionBubblesSwitch.setOnCheckedChangeListener { _, checked ->
            if (!bindingSettings) lifecycleScope.launch { AppGraph.settings.setCompletionBubblesEnabled(checked) }
        }
        longPressSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (bindingSettings) return
                val action = LongPressAction.entries.getOrElse(position) { LongPressAction.MENU }
                lifecycleScope.launch { AppGraph.settings.setLongPressAction(action) }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { AppGraph.settings.settings.collect { renderSettings(it); renderStatus() } }
                launch { AppGraph.pets.visual.collect { renderPet(it); renderStatus() } }
                launch { AppGraph.tasks.tasks.collect { renderStatus() } }
                launch { AppGraph.diagnostics.listener.collect { renderStatus() } }
            }
        }
    }

    private fun renderSettings(settings: AppSettings) {
        bindingSettings = true
        if (!sourcePackage.hasFocus()) sourcePackage.setText(settings.sourcePackage)
        sizeSeek.progress = settings.petSizeDp - 48
        sizeLabel.text = getString(R.string.pet_size_format, settings.petSizeDp)
        snapSwitch.isChecked = settings.snapEnabled
        animationSwitch.isChecked = settings.animationsEnabled
        speedSeek.progress = (settings.animationSpeed * 100).roundToInt() - 50
        speedLabel.text = getString(R.string.animation_speed_format, settings.animationSpeed)
        completedSeek.progress = settings.completedVisibleSeconds
        completedLabel.text = completedVisibleText(settings.completedVisibleSeconds)
        autoTaskBubblesSwitch.isChecked = settings.autoTaskBubblesEnabled
        attentionBubblesSwitch.isChecked = settings.attentionBubblesEnabled
        chatMessageBubblesSwitch.isChecked = settings.chatMessageBubblesEnabled
        completionBubblesSwitch.isChecked = settings.completionBubblesEnabled
        autoStartSwitch.isChecked = settings.autoStart
        longPressSpinner.setSelection(settings.longPressAction.ordinal, false)
        bindingSettings = false
        if (::preview.isInitialized) renderPet(AppGraph.pets.visual.value)
    }

    private fun renderPet(pet: PetVisual?) {
        (preview.drawable as? Animatable)?.stop()
        if (pet != null && pet.frameSequences.isNotEmpty() &&
            AppGraph.settings.settings.value.animationsEnabled && ValueAnimator.areAnimatorsEnabled()
        ) {
            val idle = pet.frameSequences[PetAnimationState.IDLE]
            if (idle != null) {
                val animation = AnimationDrawable().apply {
                    isOneShot = false
                    val speed = AppGraph.settings.settings.value.animationSpeed.coerceIn(0.5f, 2f)
                    idle.frames.forEachIndexed { index, frame ->
                        addFrame(
                            frame.toDrawable(resources).apply { isFilterBitmap = true },
                            (idle.frameDurationsMs.getOrElse(index) { 150 } / speed).roundToInt().coerceAtLeast(40),
                        )
                    }
                }
                preview.setImageDrawable(animation)
                preview.post(animation::start)
            } else {
                preview.setImageBitmap(pet.bitmap)
            }
        } else {
            preview.setImageBitmap(pet?.bitmap)
        }
        val scale = pet?.let {
            AppGraph.settings.settings.value.petSizeDp * resources.displayMetrics.density / it.bitmap.height
        }
        previewText.text = when {
            pet == null -> "Питомец ChatGPT пока не обнаружен. Откройте Codex Remote или запустите задачу — listener продолжает ждать."
            pet.frameSequences.isNotEmpty() -> buildString {
                append("Pet pack: ${pet.frameSequences.size} анимаций")
                if (pet.lookDirections.size == 16) append(" · 16 look-направлений")
                append(" · масштаб ${"%.2f".format(scale ?: 1f)}×")
                if (scale != null && scale <= 1.1f) append(" · нативное качество")
            }
            pet.hasMeaningfulTransparency -> "Источник: ${pet.source.name} · SHA-256 ${pet.hash.take(12)}… · alpha сохранён"
            else -> "Источник: ${pet.source.name} · изображение почти непрозрачное; используйте Diagnostics"
        }
        animationStatusText.text = when {
            pet == null -> "Анимация: питомец ещё не загружен"
            pet.frameSequences.isEmpty() ->
                "Анимация: недоступна — notification предоставляет только статичный кадр. Импортируйте полный pet pack или ZIP."
            !AppGraph.settings.settings.value.animationsEnabled ->
                "Анимация: pack содержит ${pet.frameSequences.size}/9 состояний, но переключатель анимации выключен."
            !ValueAnimator.areAnimatorsEnabled() ->
                "Анимация: pack готов, но системный масштаб анимаций Android отключён."
            else -> "Анимация: ${pet.frameSequences.size}/9 состояний готовы" +
                if (pet.lookDirections.size == 16) " · 16 направлений взгляда" else ""
        }
    }

    private fun previewPetAnimation() {
        val pet = AppGraph.pets.visual.value
        if (pet?.frameSequences.isNullOrEmpty()) {
            toast("Это статичный кадр. Для анимаций импортируйте полный pet pack или ZIP.")
            return
        }
        lifecycleScope.launch {
            if (!AppGraph.settings.settings.value.animationsEnabled) {
                AppGraph.settings.setAnimationsEnabled(true)
            }
            val overlayEnabled = AppGraph.settings.settings.value.overlayEnabled
            if (overlayEnabled) {
                startService(Intent(this@MainActivity, OverlayService::class.java).setAction(
                    OverlayService.ACTION_PREVIEW_ANIMATION,
                ))
            }
            renderPet(pet)
            toast(
                if (overlayEnabled) "Запущены idle в preview и wave/jump у overlay"
                else "Запущена idle-анимация в preview; для overlay сначала включите питомца",
            )
        }
    }

    private fun renderStatus() {
        if (!::statusText.isInitialized) return
        val settings = AppGraph.settings.settings.value
        val listener = AppGraph.diagnostics.listener.value
        val pet = AppGraph.pets.visual.value
        val chatGpt = SystemAccess.isPackageInstalled(this, settings.sourcePackage)
        val notificationAccess = SystemAccess.hasNotificationAccess(this)
        val overlayAccess = SystemAccess.canDrawOverlays(this)
        val ownNotifications = SystemAccess.hasOwnNotificationPermission(this)
        val parsedItems = AppGraph.tasks.tasks.value
        val codexTaskCount = parsedItems.count { it.isCodexTask() }
        val chatMessageCount = parsedItems.count { it.kind == TaskKind.CHAT_MESSAGE }
        fun mark(value: Boolean) = if (value) "✓" else "○"
        statusText.text = buildString {
            appendLine("${mark(chatGpt)} ChatGPT найден")
            appendLine("${mark(notificationAccess)} Notification access")
            appendLine("${mark(listener.connected)} Listener подключён")
            appendLine("${mark(overlayAccess)} Overlay permission")
            appendLine("${mark(ownNotifications)} Foreground notification")
            appendLine("${mark(pet != null)} Pet обнаружен")
            append("ChatGPT notifications: ${listener.activeNotificationCount}; ")
            append("Codex-задач: $codexTaskCount; сообщений: $chatMessageCount")
        }
        startButton.text = when {
            !settings.overlayEnabled -> "Запустить Codex Pet"
            !settings.petVisible -> "Показать Codex Pet"
            else -> "Остановить Codex Pet"
        }
    }

    private fun toggleOverlay() {
        lifecycleScope.launch {
            val enabled = AppGraph.settings.settings.value.overlayEnabled
            if (enabled && !AppGraph.settings.settings.value.petVisible) {
                AppGraph.settings.setPetVisible(true)
                return@launch
            }
            if (enabled) {
                AppGraph.settings.setOverlayEnabled(false)
                SystemAccess.stopOverlay(this@MainActivity)
                return@launch
            }
            if (!SystemAccess.hasNotificationAccess(this@MainActivity)) {
                toast("Сначала включите доступ к уведомлениям")
                SystemAccess.openNotificationListenerSettings(this@MainActivity)
                return@launch
            }
            if (!SystemAccess.canDrawOverlays(this@MainActivity)) {
                toast("Сначала разрешите отображение поверх приложений")
                SystemAccess.openOverlaySettings(this@MainActivity)
                return@launch
            }
            if (Build.VERSION.SDK_INT >= 33 && !SystemAccess.hasOwnNotificationPermission(this@MainActivity)) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                toast("После выбора разрешения нажмите «Запустить» ещё раз")
                return@launch
            }
            AppGraph.settings.setPetVisible(true)
            AppGraph.settings.setOverlayEnabled(true)
            SystemAccess.startOverlay(this@MainActivity)
                .onFailure {
                    AppGraph.settings.setOverlayEnabled(false)
                    toast("Не удалось запустить overlay: ${it.javaClass.simpleName}")
                }
        }
    }

    private fun card(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(getColor(R.color.codex_surface))
            cornerRadius = dp(18).toFloat()
        }
        addView(text(title, 19f, Color.WHITE, bold = true), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun action(title: String, action: () -> Unit): Button = Button(this).apply {
        text = title
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun toggle(title: String): SwitchMaterial = SwitchMaterial(this).apply {
        text = title
        setTextColor(Color.WHITE)
    }

    private fun text(value: CharSequence, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setLineSpacing(0f, 1.12f)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun cardParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(16) }

    private fun seekListener(
        onProgress: (Int) -> Unit,
        onStop: (SeekBar) -> Unit,
    ): SeekBar.OnSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) onProgress(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar) = onStop(seekBar)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun completedVisibleText(seconds: Int): String = resources.getQuantityString(
        R.plurals.completed_visible_format,
        seconds,
        seconds,
    )
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
