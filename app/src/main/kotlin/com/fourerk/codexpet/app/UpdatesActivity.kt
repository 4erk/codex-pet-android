package com.fourerk.codexpet.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.BuildConfig
import com.fourerk.codexpet.update.UpdatePhase
import com.fourerk.codexpet.update.UpdateSource
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class UpdatesActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var primaryButton: Button
    private lateinit var manualApkButton: Button
    private lateinit var stableButton: Button
    private lateinit var autoSwitch: SwitchMaterial
    private lateinit var downloadSwitch: SwitchMaterial
    private lateinit var wifiSwitch: SwitchMaterial
    private var binding = false

    private val manualApkPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(AppGraph.updates::prepareManualApk)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        bind()
        observe()
    }

    override fun onResume() {
        super.onResume()
        AppGraph.updates.resumeInstallIfAllowed()
        AppGraph.updates.checkIfDue()
        render()
    }

    private fun buildContent(): android.view.View {
        val body = PetUi.page(
            this,
            "Обновления",
            "Проверка GitHub, автоматическая загрузка и ручная установка APK.",
        )

        val current = PetUi.heroCard(this)
        current.addView(PetUi.text(this, "Текущая сборка", 17f, PetUi.TEXT, bold = true))
        statusText = PetUi.text(this, "Codex Pet ${BuildConfig.VERSION_NAME}", 12.5f, PetUi.MUTED).apply {
            setPadding(0, PetUi.dp(this@UpdatesActivity, 8), 0, PetUi.dp(this@UpdatesActivity, 12))
        }
        current.addView(statusText)
        primaryButton = PetUi.primaryAction(this, "Проверить") { handlePrimaryAction() }
        current.addView(primaryButton)
        stableButton = PetUi.action(this, "Открыть") {
            if (!AppGraph.updates.openStableApp()) AppGraph.updates.openReleasePage()
        }
        stableButton.visibility = android.view.View.GONE
        current.addView(stableButton, PetUi.marginParams(this, 8))
        body.addView(current, PetUi.marginParams(this, 16))

        body.addView(PetUi.sectionTitle(this, "Вручную"))
        val manual = PetUi.card(this)
        manualApkButton = PetUi.action(this, "APK-файл") {
            manualApkPicker.launch(
                arrayOf(
                    "application/vnd.android.package-archive",
                    "application/octet-stream",
                ),
            )
        }
        manual.addView(manualApkButton)
        manual.addView(PetUi.action(this, "GitHub") {
            if (!AppGraph.updates.openReleasePage()) AppGraph.updates.checkNow()
        }, PetUi.marginParams(this, 8))
        manual.addView(PetUi.helper(
            this,
            if (BuildConfig.DEBUG) {
                "Тестовая сборка может установить стабильный Codex Pet рядом с собой. APK с GitHub принимается только с правильным именем приложения, контрольной суммой и официальной подписью."
            } else {
                "APK принимается только для этого Codex Pet, с правильной подписью и более новой версией."
            },
        ))
        body.addView(manual, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Автоматически"))
        val automatic = PetUi.card(this)
        val autoRow = PetUi.toggle(this, "Автопроверка", "Проверять новые версии при запуске и пока питомец работает.")
        autoSwitch = PetUi.switchFrom(autoRow)
        val downloadRow = PetUi.toggle(this, "Автозагрузка", "Скачивать новую версию заранее после проверки.")
        downloadSwitch = PetUi.switchFrom(downloadRow)
        val wifiRow = PetUi.toggle(this, "Только Wi‑Fi", "Не загружать APK автоматически через тарифицируемую сеть.")
        wifiSwitch = PetUi.switchFrom(wifiRow)
        automatic.addView(autoRow)
        PetUi.addDivider(automatic, this)
        automatic.addView(downloadRow)
        PetUi.addDivider(automatic, this)
        automatic.addView(wifiRow)
        body.addView(automatic, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Безопасность"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.helper(
                this@UpdatesActivity,
                "Перед установкой проверяются версия, имя приложения, контрольная сумма и подпись. Затем Android показывает обычное системное подтверждение установки.",
            ))
        }, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Связано"))
        body.addView(PetUi.card(this).apply {
            addView(PetUi.navigationRow(this@UpdatesActivity, "↗", "Подключение", "Если приложение перестало получать состояние ChatGPT") {
                startActivity(Intent(this@UpdatesActivity, IntegrationActivity::class.java))
            })
            PetUi.addDivider(this, this@UpdatesActivity)
            addView(PetUi.navigationRow(this@UpdatesActivity, "?", "Помощь", "Проверки и восстановление") {
                startActivity(Intent(this@UpdatesActivity, HelpActivity::class.java))
            })
        }, PetUi.marginParams(this, 4))

        return ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(body)
        }
    }

    private fun bind() {
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            if (!binding) lifecycleScope.launch { AppGraph.settings.setAutoUpdateEnabled(checked) }
        }
        downloadSwitch.setOnCheckedChangeListener { _, checked ->
            if (!binding) lifecycleScope.launch { AppGraph.settings.setAutoDownloadUpdates(checked) }
        }
        wifiSwitch.setOnCheckedChangeListener { _, checked ->
            if (!binding) lifecycleScope.launch { AppGraph.settings.setUpdateWifiOnly(checked) }
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AppGraph.settings.settings.collect { settings ->
                        binding = true
                        autoSwitch.isChecked = settings.autoUpdateEnabled
                        downloadSwitch.isChecked = settings.autoDownloadUpdates
                        wifiSwitch.isChecked = settings.updateWifiOnly
                        binding = false
                    }
                }
                launch { AppGraph.updates.state.collect { render() } }
            }
        }
    }

    private fun handlePrimaryAction() {
        when (AppGraph.updates.state.value.phase) {
            UpdatePhase.AVAILABLE -> AppGraph.updates.downloadAvailable()
            UpdatePhase.READY_TO_INSTALL,
            UpdatePhase.NEEDS_INSTALL_PERMISSION -> AppGraph.updates.installReady()
            UpdatePhase.DOWNLOADING,
            UpdatePhase.INSTALLING,
            UpdatePhase.CHECKING,
            UpdatePhase.VALIDATING_MANUAL -> Unit
            else -> AppGraph.updates.checkNow()
        }
    }

    private fun render() {
        if (!::statusText.isInitialized) return
        val state = AppGraph.updates.state.value
        statusText.text = buildString {
            append(if (BuildConfig.DEBUG) "Тестовая сборка: " else "Установлено: ")
            append(state.currentVersion)
            state.latestVersion?.let {
                append(
                    when (state.source) {
                        UpdateSource.LOCAL_FILE -> "\nВыбранный APK: $it"
                        else -> "\nGitHub: $it"
                    },
                )
            }
            state.progressPercent?.let { append("\nЗагрузка: $it%") }
            state.message?.let { append("\n${friendlyMessage(it)}") }
            state.checkedAt?.let {
                append("\nПроверено: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}")
            }
        }

        primaryButton.text = when (state.phase) {
            UpdatePhase.AVAILABLE -> "Скачать"
            UpdatePhase.DOWNLOADING -> "Загрузка…"
            UpdatePhase.VALIDATING_MANUAL -> "Проверка…"
            UpdatePhase.READY_TO_INSTALL -> "Установить"
            UpdatePhase.NEEDS_INSTALL_PERMISSION -> "Разрешить"
            UpdatePhase.INSTALLING -> "Установка…"
            UpdatePhase.CHECKING -> "Проверка…"
            else -> "Проверить"
        }
        val busy = state.phase in setOf(
            UpdatePhase.DOWNLOADING,
            UpdatePhase.INSTALLING,
            UpdatePhase.CHECKING,
            UpdatePhase.VALIDATING_MANUAL,
        )
        primaryButton.isEnabled = !busy
        manualApkButton.isEnabled = !busy
        stableButton.visibility = if (BuildConfig.DEBUG && isStableInstalled()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    private fun isStableInstalled(): Boolean =
        packageManager.getLaunchIntentForPackage(PRODUCTION_APPLICATION_ID) != null

    private fun friendlyMessage(message: String): String = when {
        message.contains("GitHub HTTP 404", ignoreCase = true) ->
            "На GitHub пока нет опубликованной стабильной версии"
        else -> message
    }

    private companion object {
        const val PRODUCTION_APPLICATION_ID = "com.mr4erk.codexpet"
    }
}
