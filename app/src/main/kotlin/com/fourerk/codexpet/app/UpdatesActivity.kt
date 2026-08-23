package com.fourerk.codexpet.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.BuildConfig
import com.fourerk.codexpet.update.UpdatePhase
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class UpdatesActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var primaryButton: Button
    private lateinit var autoSwitch: SwitchMaterial
    private lateinit var downloadSwitch: SwitchMaterial
    private lateinit var wifiSwitch: SwitchMaterial
    private var binding = false

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
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PetUi.dp(this@UpdatesActivity, 18), PetUi.dp(this@UpdatesActivity, 20), PetUi.dp(this@UpdatesActivity, 18), PetUi.dp(this@UpdatesActivity, 36))
            setBackgroundColor(PetUi.BACKGROUND)
        }
        body.addView(PetUi.text(this, "Обновления", 28f, PetUi.TEXT, bold = true))
        body.addView(PetUi.text(this, "Стабильный канал GitHub Releases с проверкой SHA-256 и подписи APK.", 14f, PetUi.MUTED))

        val current = PetUi.card(this, "Текущая версия")
        statusText = PetUi.text(this, "Codex Pet ${BuildConfig.VERSION_NAME}", 13f, PetUi.MUTED)
        current.addView(statusText)
        primaryButton = PetUi.action(this, "Проверить сейчас") { handlePrimaryAction() }
        current.addView(primaryButton)
        current.addView(PetUi.action(this, "Открыть GitHub Release") {
            if (!AppGraph.updates.openReleasePage()) {
                AppGraph.updates.checkNow()
            }
        })
        body.addView(current, PetUi.marginParams(this, 16))

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
                "Codex Pet не обходит защиту Android. После безопасной загрузки APK системный Package Installer попросит подтверждение. Первый раз Android также может попросить разрешить установку обновлений из Codex Pet.",
                13f,
                PetUi.MUTED,
            ))
        }, PetUi.marginParams(this))

        body.addView(PetUi.card(this, "Связанные настройки").apply {
            addView(PetUi.action(this@UpdatesActivity, "Подключение и listener") {
                startActivity(Intent(this@UpdatesActivity, IntegrationActivity::class.java))
            })
            addView(PetUi.action(this@UpdatesActivity, "Помощь и диагностика") {
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
            UpdatePhase.CHECKING -> Unit
            else -> AppGraph.updates.checkNow()
        }
    }

    private fun render() {
        if (!::statusText.isInitialized) return
        val state = AppGraph.updates.state.value
        statusText.text = buildString {
            append("Установлено: ${state.currentVersion}")
            state.latestVersion?.let { append("\nGitHub stable: $it") }
            state.progressPercent?.let { append("\nЗагрузка: $it%") }
            state.message?.let { append("\n$it") }
            state.checkedAt?.let {
                append("\nПроверено: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}")
            }
        }
        primaryButton.text = when (state.phase) {
            UpdatePhase.AVAILABLE -> "Скачать обновление"
            UpdatePhase.DOWNLOADING -> "Скачивается…"
            UpdatePhase.READY_TO_INSTALL -> "Установить"
            UpdatePhase.NEEDS_INSTALL_PERMISSION -> "Разрешить и установить"
            UpdatePhase.INSTALLING -> "Ожидает Android…"
            UpdatePhase.CHECKING -> "Проверяю…"
            UpdatePhase.INCOMPATIBLE_BUILD -> "Открыть stable Release"
            else -> "Проверить сейчас"
        }
        primaryButton.isEnabled = state.phase !in setOf(
            UpdatePhase.DOWNLOADING,
            UpdatePhase.INSTALLING,
            UpdatePhase.CHECKING,
        )
    }
}
