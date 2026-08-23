package com.fourerk.codexpet.notification

import android.app.Notification


enum class ChatGptNotificationRole {
    CODEX_TASK,
    CODEX_AVATAR,
    CHAT_MESSAGE,
}

data class ChatGptNotificationClassification(
    val role: ChatGptNotificationRole,
    val confidence: Int,
    val reasons: List<String>,
)

internal data class ChatGptNotificationSignals(
    val channelId: String?,
    val shortcutId: String?,
    val title: String?,
    val text: String?,
    val ongoing: Boolean,
    val hasProgress: Boolean,
    val hasBubble: Boolean,
)

/**
 * Classifies public ChatGPT notifications without depending on one immutable channel id.
 * Exact known channels remain strongest; structured notification signals and conservative text
 * hints keep Codex Remote working if ChatGPT renames channels in a future release.
 */
object ChatGptNotificationClassifier {
    fun classify(notification: Notification): ChatGptNotificationRole = inspect(notification).role

    fun inspect(notification: Notification): ChatGptNotificationClassification {
        val extras = notification.extras
        return classifySignals(
            ChatGptNotificationSignals(
                channelId = notification.channelId,
                shortcutId = notification.shortcutId,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                text = listOfNotNull(
                    extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                    extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                ).joinToString(" ").ifBlank { null },
                ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
                hasProgress = extras.containsKey(Notification.EXTRA_PROGRESS) ||
                    extras.containsKey(Notification.EXTRA_PROGRESS_MAX) ||
                    extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
                hasBubble = notification.bubbleMetadata != null,
            ),
        )
    }

    internal fun classifyChannelId(channelId: String?): ChatGptNotificationRole =
        classifySignals(ChatGptNotificationSignals(channelId, null, null, null, false, false, false)).role

    internal fun classifySignals(signals: ChatGptNotificationSignals): ChatGptNotificationClassification {
        val channel = signals.channelId.orEmpty().lowercase()
        val shortcut = signals.shortcutId.orEmpty().lowercase()
        val combinedText = "${signals.title.orEmpty()} ${signals.text.orEmpty()}".trim()
        val reasons = mutableListOf<String>()

        val avatarHint = channel.endsWith(AVATAR_CHANNEL_SUFFIX) ||
            shortcut.endsWith(".avatar") || shortcut.contains("codex_avatar")
        if (avatarHint && (channel.contains("codex") || shortcut.contains("codex") || signals.hasBubble)) {
            return ChatGptNotificationClassification(
                ChatGptNotificationRole.CODEX_AVATAR,
                100,
                listOf("avatar marker"),
            )
        }

        var score = 0
        when {
            channel.startsWith(CODEX_REMOTE_CHANNEL_PREFIX) -> {
                score += 100
                reasons += "known Codex channel"
            }
            channel.contains("codex") && (channel.contains("remote") || channel.contains("session")) -> {
                score += 85
                reasons += "Codex-like channel"
            }
            channel.contains("codex") -> {
                score += 70
                reasons += "Codex channel hint"
            }
        }
        if (shortcut.contains("codex") || shortcut.contains("remote_session")) {
            score += 70
            reasons += "Codex shortcut"
        }
        if (signals.hasProgress) {
            score += 45
            reasons += "structured progress"
        }
        if (signals.ongoing) {
            score += 18
            reasons += "ongoing notification"
        }
        if (CODEX_TEXT_HINT.containsMatchIn(combinedText)) {
            score += 38
            reasons += "Codex/remote text hint"
        }
        if (signals.hasBubble && score >= 45) {
            score += 10
            reasons += "bubble metadata"
        }

        return if (score >= TASK_THRESHOLD) {
            ChatGptNotificationClassification(ChatGptNotificationRole.CODEX_TASK, score.coerceAtMost(100), reasons)
        } else {
            ChatGptNotificationClassification(
                ChatGptNotificationRole.CHAT_MESSAGE,
                (100 - score).coerceIn(50, 100),
                if (reasons.isEmpty()) listOf("no Codex execution signals") else reasons,
            )
        }
    }

    private val CODEX_TEXT_HINT = Regex(
        "(?:\\bcodex\\b|remote (?:computer|session|task)|удал[её]нн(?:ый|ая) (?:компьютер|сеанс|сессия)|" +
            "задач[аи] codex|codex-задач[аи])",
        RegexOption.IGNORE_CASE,
    )
    private const val TASK_THRESHOLD = 65
    private const val CODEX_REMOTE_CHANNEL_PREFIX = "codex_remote_session"
    private const val AVATAR_CHANNEL_SUFFIX = ".avatar"
}
