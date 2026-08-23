package com.fourerk.codexpet.app

import android.content.Intent
import android.os.Bundle
import android.view.View
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
    }

    private fun buildContent(): View {
        val body = PetUi.page(
            this,
            "Обновления",
            "Автоматический stable-канал и ручное обновление с теми же проверками подписи.",
        )

        val current = PetUi.card(this, "Текущая версия")
        statusText = PetUi.text(this, "Codex Pet ${BuildConfig.VERSION_NAME}", 13f, PetUi.MUTED)
        current.addView(statusText)
        body.addView(current, PetUi.marginParams(this, 16))

        val manualCard = PetUi.card(this, "Вручную")
        manualCard.addView(PetUi.text(
            this,
            "Работает независимо от автоматических настроек. Можно прямо сейчас проверить GitHub stable или выбрать уже скачанный APK с телефона.",
            12f,
            PetUi.MUTED,
        ))
        primaryButton = PetUi.primaryAction(this, "Проверить GitHub сейчас") { handlePrimaryAction() }
        manualCard.addView(primaryButton, PetUi.marginParams(this, 8))
        manualApkButton = PetUi.action(this, "Выбрать APK из файла") {
            manualApkPicker.launch(
                arrayOf(
                    "application/vnd.android.package-archive",
                    "application/octet-stream",
                ),
            )
        }
        manualCard.addView(manualApkButton)
        manualCard.addView(PetUi.action(this, "Открыть GitHub Releases") {
            if (!AppGraph.updates.openReleasePage()) {
                AppGraph.updates.checkNow()
            }
        })
        manualCard.addView(PetUi.text(
            this,
            "APK из файла принимается только если Android распознаёт его как более новую версию этого же Codex Pet и сертификат подписи в точности совпадает. Старую/ту же версию и чужой APK приложение отвергнет до установки.",
            12f,
            PetUi.MUTED,
        ))
        body.addView(manualCard, PetUi.marginParams(this))

        val autoCard = PetUi.card(this, "Автоматически")
        val autoRow = PetUi.toggle(this, "Проверять новые stable-релизы", "На старте приложения и периодически, пока пет работает.")
        autoSwitch = PetUi.switchFrom(autoRow)
        val downloadRow = PetUi.toggle(this, "Скачивать обновление заранее", "APK сначала проверяется по GitHub SHA-256 и текущему сертификату подписи.")
        downloadSwitch = PetUi.switchFrom(downloadRow)
        val wifiRow = PetUi.toggle(this, "Автозагрузка только без тарификации", "На мобильной/тарифицируемой сети будет только уведомление о новой версии.")
        wifiSwitch = PetUi.switchFrom(wifiRow)
        autoCard.addView(autoRow)
        autoCard.addView(downloadRow)
        autoCard.addView(wifiRow)
        body.addView(autoCard, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Как устанавливается").apply {
            addView(PetUi.text(
                this@UpdatesActivity,
                "Оба пути сходятся в один безопасный install flow. Codex Pet проверяет APK, затем передаёт его системному Android Package Installer. Защита Android не обходится: установка требует системного подтверждения, а первый раз Android может попросить разрешить Codex Pet устанавливать обновления из этого источника.",
                13f,
                PetUi.MUTED,
            ))
        }, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Связанные настройки").apply {
            addView(PetUi.navigationRow(this@UpdatesActivity, "↗", "Подключение и listener", "Проверка источника состояний и self-heal") {
                startActivity(Intent(this@UpdatesActivity, IntegrationActivity::class.java))
            })
            addView(PetUi.divider(this@UpdatesActivity), LinearLayout.LayoutParams.MATCH_PARENT, PetUi.dp(this@UpdatesActivity, 1))
            addView(PetUi.navigationRow(this@UpdatesActivity, "?", "Помощь и диагностика", "Восстановление, логи состояния и тестовые сценарии") {
                startActivity(Intent(this@UpdatesActivity, HelpActivity::class.java))
            })
        }, PetUi.marginParams(this))

        return ScrollView(this).apply { addView(body) }
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
            UpdatePhase.INCOMPATIBLE_BUILD -> AppGraph.updates.openReleasePage()
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
            append("Установлено: ${state.currentVersion}")
            state.latestVersion?.let {
                append(
                    when (state.source) {
                        UpdateSource.LOCAL_FILE -> "\nAPK из файла: $it"
                        else -> "\nGitHub stable: $it"
                    },
                )
            }
            state.progressPercent?.let { append("\nГотовность: $it%") }
            state.message?.let { append("\n$it") }
            state.checkedAt?.let {
                append("\nПроверено: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}")
            }
        }
        primaryButton.text = when (state.phase) {
            UpdatePhase.AVAILABLE -> "Скачать обновление"
            UpdatePhase.DOWNLOADING -> "Скачивается…"
            UpdatePhase.VALIDATING_MANUAL -> "Проверяю APK…"
            UpdatePhase.READY_TO_INSTALL -> "Установить"
            UpdatePhase.NEEDS_INSTALL_PERMISSION -> "Разрешить и установить"
            UpdatePhase.INSTALLING -> "Ожидает Android…"
            UpdatePhase.CHECKING -> "Проверяю GitHub…"
            UpdatePhase.INCOMPATIBLE_BUILD -> "Открыть stable Release"
            else -> "Проверить GitHub сейчас"
        }
        val busy = state.phase in setOf(
            UpdatePhase.DOWNLOADING,
            UpdatePhase.INSTALLING,
            UpdatePhase.CHECKING,
            UpdatePhase.VALIDATING_MANUAL,
        )
        primaryButton.isEnabled = !busy
        manualApkButton.isEnabled = !busy
    }
}
