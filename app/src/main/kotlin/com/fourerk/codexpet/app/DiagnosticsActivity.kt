package com.fourerk.codexpet.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
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
import com.fourerk.codexpet.R
import com.fourerk.codexpet.diagnostics.DiagnosticsExporter
import com.fourerk.codexpet.diagnostics.NotificationSnapshot
import com.fourerk.codexpet.notification.ChatGptNotificationListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
            Toast.makeText(this, "Санитизированный JSON сохранён", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "Ошибка экспорта: ${it.message.orEmpty()}", Toast.LENGTH_LONG).show()
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

    private fun buildContent(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(36))
            setBackgroundColor(getColor(R.color.codex_background))
        }
        root.addView(label("Diagnostics → ChatGPT notifications", 25f, Color.WHITE, true))
        root.addView(label(
            if (BuildConfig.DEBUG) "Debug build: тексты видны только на этом экране и никогда не входят в export." else "Release build: полный текст notifications не хранится.",
            13f,
            0xFFB8C0C6.toInt(),
        ))
        status = label("", 13f, Color.WHITE)
        root.addView(status)
        root.addView(button("Refresh active notifications") {
            ChatGptNotificationListener.refresh(this)
        })
        root.addView(button("Restart notification listener") {
            ChatGptNotificationListener.restart(this)
        })
        root.addView(button("Export sanitized JSON") { export() })
        root.addView(button("Очистить snapshots") { AppGraph.diagnostics.clear() })
        snapshots = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(snapshots)
        return ScrollView(this).apply { addView(root) }
    }

    private fun render() {
        if (!::status.isInitialized) return
        val listener = AppGraph.diagnostics.listener.value
        val pet = AppGraph.pets.visual.value
        status.text = buildString {
            appendLine("Listener: ${if (listener.connected) "connected" else "disconnected"}")
            appendLine("Source: ${listener.sourcePackage}")
            appendLine("Active notifications: ${listener.activeNotificationCount}")
            appendLine("Snapshots: ${AppGraph.diagnostics.snapshots.value.size}")
            appendLine("Pet: ${pet?.source?.name ?: "not detected"}")
            append("Pet hash: ${pet?.hash ?: "—"}")
            listener.lastError?.let { appendLine(); append("Last error: $it") }
        }
        snapshots.removeAllViews()
        AppGraph.diagnostics.snapshots.value.take(30).forEach { snapshot ->
            snapshots.addView(snapshotCard(snapshot), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) })
        }
    }

    private fun snapshotCard(item: NotificationSnapshot): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor(getColor(R.color.codex_surface))
            cornerRadius = dp(14).toFloat()
        }
        addView(label("${item.event} · id=${item.id}", 16f, Color.WHITE, true))
        addView(label(
            "key=${item.key}\npostTime=${item.postTime}\nflags=${item.flags}\ncategory=${item.category}\ngroup=${item.group}\ngroupKey=${item.groupKey}\nshortcutId=${item.shortcutId}\nchannelId=${item.channelId}\nrole=${item.notificationRole}\nstyle=${item.styleClass}",
            11f,
            0xFFB8C0C6.toInt(),
        ))
        addView(label(
            "Bubble: exists=${item.bubble.exists}, icon=${item.bubble.iconExists}, type=${item.bubble.iconTypeName}, height=${item.bubble.desiredHeight}, suppressed=${item.bubble.suppressNotification}, autoExpand=${item.bubble.autoExpandBubble}, intent=${item.bubble.bubbleIntentExists}",
            12f,
            0xFFD5DADD.toInt(),
        ))
        item.petCandidates.forEach { candidate ->
            val bitmap = candidate.bitmap
            addView(label(
                "Pet candidate ${candidate.source}: ${candidate.iconTypeName}, ${candidate.drawableClass}, ${bitmap?.width}×${bitmap?.height}, transparent=${bitmap?.transparentPixelPercent?.let { "%.2f".format(Locale.US, it) }}%, corners=${bitmap?.transparentCorners}, hash=${bitmap?.sha256?.take(16)}…, accepted=${candidate.acceptedForOverlay}${candidate.rejectionReason?.let { ", reason=$it" }.orEmpty()}",
                12f,
                if (candidate.acceptedForOverlay) 0xFF7DD3B0.toInt() else 0xFFFFB86B.toInt(),
            ))
        }
        addView(label("Extras keys: ${item.extraKeys.joinToString()}", 11f, 0xFF8F989F.toInt()))
        item.extras.forEach { (key, value) ->
            val debug = value.debugValue?.replace(Regex("\\s+"), " ")?.take(400)
            addView(label(
                "$key: present=${value.present}, length=${value.length}${debug?.let { "\n$it" }.orEmpty()}",
                11f,
                if (debug == null) 0xFF8F989F.toInt() else 0xFFE7EBED.toInt(),
            ))
        }
        item.parserNotes.forEach { addView(label("Note: $it", 11f, 0xFFFFD27D.toInt())) }
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

    private fun button(title: String, action: () -> Unit): Button = Button(this).apply {
        text = title
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun label(value: CharSequence, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
