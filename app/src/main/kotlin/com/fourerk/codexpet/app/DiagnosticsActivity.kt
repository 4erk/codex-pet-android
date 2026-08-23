package com.fourerk.codexpet.app

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fourerk.codexpet.BuildConfig
import com.fourerk.codexpet.diagnostics.DiagnosticsExporter
import com.fourerk.codexpet.diagnostics.NotificationSnapshot
import com.fourerk.codexpet.notification.ChatGptNotificationListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var snapshots: LinearLayout
    private var pendingExport: String? = null

    private val createJson = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri == null || json == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri, "wt").use { output ->
                requireNotNull(output)
                output.writer(Charsets.UTF_8).use { it.write(json) }
            }
        }.onSuccess {
            Toast.makeText(this, "Диагностика сохранена", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "Не удалось сохранить: ${it.message.orEmpty()}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { AppGraph.diagnostics.listener.collect { render() } }
                launch { AppGraph.diagnostics.snapshots.collect { render() } }
                launch { AppGraph.pets.visual.collect { render() } }
            }
        }
    }

    private fun buildContent(): android.view.View {
        val body = PetUi.page(
            this,
            "Диагностика",
            "Технические сведения об уведомлениях ChatGPT и работе подключения.",
        )

        val hero = PetUi.heroCard(this)
        hero.addView(PetUi.text(this, "Состояние", 17f, PetUi.TEXT, bold = true))
        hero.addView(PetUi.helper(
            this,
            if (BuildConfig.DEBUG) {
                "Тестовая сборка может показывать текст уведомлений только на этом экране. В экспорт он не попадает."
            } else {
                "В стабильной сборке полный текст уведомлений не сохраняется."
            },
        ))
        status = PetUi.text(this, "Проверяю…", 12f, PetUi.MUTED).apply {
            setPadding(0, PetUi.dp(this@DiagnosticsActivity, 10), 0, PetUi.dp(this@DiagnosticsActivity, 12))
        }
        hero.addView(status)
        hero.addView(PetUi.primaryAction(this, "Обновить") {
            ChatGptNotificationListener.refresh(this)
        })
        hero.addView(PetUi.action(this, "Переподключить") {
            ChatGptNotificationListener.restart(this)
        }, PetUi.marginParams(this, 8))
        body.addView(hero, PetUi.marginParams(this, 16))

        body.addView(PetUi.sectionTitle(this, "Данные"))
        val actions = PetUi.card(this)
        actions.addView(PetUi.navigationRow(this, "⇧", "Экспорт", "Сохранить очищенный JSON") { export() })
        PetUi.addDivider(actions, this)
        actions.addView(PetUi.navigationRow(this, "×", "Очистить", "Удалить текущие диагностические снимки") {
            AppGraph.diagnostics.clear()
        })
        body.addView(actions, PetUi.marginParams(this, 4))

        body.addView(PetUi.sectionTitle(this, "Последние уведомления"))
        snapshots = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(snapshots)

        return ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(body)
        }
    }

    private fun render() {
        if (!::status.isInitialized) return
        val listener = AppGraph.diagnostics.listener.value
        val pet = AppGraph.pets.visual.value
        status.text = buildString {
            append("Подключение: ${if (listener.connected) "работает" else "не отвечает"}")
            append("\nИсточник: ${listener.sourcePackage}")
            append("\nАктивных уведомлений: ${listener.activeNotificationCount}")
            append(" · снимков: ${AppGraph.diagnostics.snapshots.value.size}")
            append("\nПитомец: ${pet?.source?.name ?: "не определён"}")
            listener.lastError?.let { append("\nПоследняя ошибка: $it") }
        }
        snapshots.removeAllViews()
        AppGraph.diagnostics.snapshots.value.take(30).forEach { snapshot ->
            snapshots.addView(snapshotCard(snapshot), PetUi.marginParams(this, 8))
        }
        if (AppGraph.diagnostics.snapshots.value.isEmpty()) {
            snapshots.addView(PetUi.card(this).apply {
                addView(PetUi.helper(this@DiagnosticsActivity, "Снимков пока нет. Нажмите «Обновить» или дождитесь нового уведомления ChatGPT."))
            })
        }
    }

    private fun snapshotCard(item: NotificationSnapshot): LinearLayout = PetUi.card(this).apply {
        addView(PetUi.text(this@DiagnosticsActivity, "${item.event} · id=${item.id}", 14.5f, PetUi.TEXT, bold = true))
        addView(PetUi.text(
            this@DiagnosticsActivity,
            "key=${item.key}\npostTime=${item.postTime}\nflags=${item.flags}\ncategory=${item.category}\ngroup=${item.group}\ngroupKey=${item.groupKey}\nshortcutId=${item.shortcutId}\nchannelId=${item.channelId}\nrole=${item.notificationRole}\nstyle=${item.styleClass}",
            10.5f,
            PetUi.MUTED,
        ).apply { setPadding(0, PetUi.dp(this@DiagnosticsActivity, 7), 0, 0) })
        addView(PetUi.text(
            this@DiagnosticsActivity,
            "Bubble: exists=${item.bubble.exists}, icon=${item.bubble.iconExists}, type=${item.bubble.iconTypeName}, height=${item.bubble.desiredHeight}, suppressed=${item.bubble.suppressNotification}, autoExpand=${item.bubble.autoExpandBubble}, intent=${item.bubble.bubbleIntentExists}",
            10.5f,
            PetUi.SECONDARY,
        ).apply { setPadding(0, PetUi.dp(this@DiagnosticsActivity, 7), 0, 0) })
        item.petCandidates.forEach { candidate ->
            val bitmap = candidate.bitmap
            addView(PetUi.text(
                this@DiagnosticsActivity,
                "Pet ${candidate.source}: ${candidate.iconTypeName}, ${candidate.drawableClass}, ${bitmap?.width}×${bitmap?.height}, transparent=${bitmap?.transparentPixelPercent?.let { "%.2f".format(Locale.US, it) }}%, accepted=${candidate.acceptedForOverlay}${candidate.rejectionReason?.let { ", reason=$it" }.orEmpty()}",
                10.5f,
                if (candidate.acceptedForOverlay) PetUi.GOOD else PetUi.WARN,
            ))
        }
        addView(PetUi.text(this@DiagnosticsActivity, "Extras: ${item.extraKeys.joinToString()}", 10f, PetUi.MUTED))
        item.extras.forEach { (key, value) ->
            val debug = value.debugValue?.replace(Regex("\\s+"), " ")?.take(400)
            addView(PetUi.text(
                this@DiagnosticsActivity,
                "$key: present=${value.present}, length=${value.length}${debug?.let { "\n$it" }.orEmpty()}",
                10f,
                if (debug == null) PetUi.MUTED else PetUi.SECONDARY,
            ))
        }
        item.parserNotes.forEach { addView(PetUi.text(this@DiagnosticsActivity, "Примечание: $it", 10f, PetUi.WARN)) }
    }

    private fun export() {
        pendingExport = DiagnosticsExporter.build(
            context = this,
            listener = AppGraph.diagnostics.listener.value,
            snapshots = AppGraph.diagnostics.snapshots.value,
            settings = AppGraph.settings.settings.value,
            pet = AppGraph.pets.visual.value,
        )
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        createJson.launch("codex-pet-diagnostics-$timestamp.json")
    }
}
