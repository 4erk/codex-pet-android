package com.fourerk.codexpet.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.fourerk.codexpet.diagnostics.BubbleDiagnostics
import com.fourerk.codexpet.diagnostics.NotificationSnapshot
import com.fourerk.codexpet.diagnostics.SanitizedText
import com.fourerk.codexpet.pet.PetAssetProvider
import com.fourerk.codexpet.pet.PetInspection
import com.fourerk.codexpet.task.CodexTask
import com.fourerk.codexpet.task.TaskProgress
import com.fourerk.codexpet.task.TaskSignals
import com.fourerk.codexpet.task.TaskStatusResolver
import java.time.Instant

data class ParsedNotification(
    val task: CodexTask,
    val snapshot: NotificationSnapshot,
)

class NotificationParser {
    fun parse(
        sbn: StatusBarNotification,
        event: String,
        petInspection: PetInspection,
        includeDebugText: Boolean,
    ): ParsedNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val progress = extras.optionalInt(Notification.EXTRA_PROGRESS)
        val progressMax = extras.optionalInt(Notification.EXTRA_PROGRESS_MAX)
        val progressIndeterminate = extras.optionalBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) ?: false
        val ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        val groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val messagingStyle = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        }.getOrNull()
        val inboxLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map(CharSequence::toString)
            .orEmpty()
        val latestMessage = messagingStyle?.messages?.lastOrNull()?.text?.toString()
        val title = firstText(
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE),
            notification.shortcutId,
            if (groupSummary) "ChatGPT notification group" else "Codex task",
        ).orEmpty().singleLine(120)
        val summary = firstText(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            latestMessage,
            inboxLines.lastOrNull(),
        )?.singleLine(360)
        val fallbackText = listOfNotNull(title, summary, inboxLines.joinToString(" "))
            .joinToString(" ")
            .take(1_000)
        val status = TaskStatusResolver.resolve(
            TaskSignals(
                progress = progress,
                progressMax = progressMax,
                progressIndeterminate = progressIndeterminate,
                ongoing = ongoing,
                fallbackText = fallbackText,
            ),
        )
        val taskProgress = if (progress != null || progressMax != null || progressIndeterminate) {
            TaskProgress(
                value = progress ?: 0,
                max = progressMax ?: 0,
                indeterminate = progressIndeterminate,
            )
        } else {
            null
        }
        val bubble = notification.bubbleMetadata
        val taskId = notification.shortcutId
            ?.takeIf(String::isNotBlank)
            ?.let { "shortcut:$it" }
            ?: "notification:${sbn.key}"
        val notes = buildList {
            if (groupSummary) add("FLAG_GROUP_SUMMARY is set; item is retained as an aggregate, not expanded into invented tasks")
            if (inboxLines.size > 1) add("Inbox-style lines detected: ${inboxLines.size}; lines are not treated as independent tasks without stable IDs")
            addAll(petInspection.notes)
        }
        val snapshot = NotificationSnapshot(
            event = event,
            capturedAt = System.currentTimeMillis(),
            packageName = sbn.packageName,
            id = sbn.id,
            tag = sbn.tag,
            key = sbn.key,
            postTime = sbn.postTime,
            flags = notification.flags,
            category = notification.category,
            group = notification.group,
            groupKey = sbn.groupKey,
            shortcutId = notification.shortcutId,
            channelId = notification.channelId,
            extraKeys = extras.keySet().sorted(),
            extras = DIAGNOSTIC_TEXT_KEYS.associateWith { key ->
                sanitize(extras.getCharSequence(key), includeDebugText)
            },
            progress = progress,
            progressMax = progressMax,
            progressIndeterminate = extras.optionalBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
            bubble = BubbleDiagnostics(
                exists = bubble != null,
                iconExists = bubble?.icon != null,
                iconType = bubble?.icon?.type,
                iconTypeName = bubble?.icon?.type?.let(PetAssetProvider::iconTypeName),
                desiredHeight = bubble?.desiredHeight,
                desiredHeightResId = bubble?.desiredHeightResId,
                suppressNotification = bubble?.isNotificationSuppressed,
                autoExpandBubble = bubble?.autoExpandBubble,
                shortcutId = bubble?.shortcutId,
                bubbleIntentExists = bubble?.intent != null,
                deleteIntentExists = bubble?.deleteIntent != null,
            ),
            contentIntentExists = notification.contentIntent != null,
            bubbleIntentExists = bubble?.intent != null,
            styleClass = when {
                messagingStyle != null -> NotificationCompat.MessagingStyle::class.java.name
                inboxLines.isNotEmpty() -> Notification.InboxStyle::class.java.name
                extras.containsKey(Notification.EXTRA_BIG_TEXT) -> Notification.BigTextStyle::class.java.name
                else -> null
            },
            petCandidates = petInspection.candidates.map { it.diagnostics },
            selectedPetSource = petInspection.selected?.source?.name,
            parserNotes = notes,
        )
        return ParsedNotification(
            task = CodexTask(
                id = taskId,
                title = title.ifBlank { "Codex task" },
                summary = summary,
                status = status,
                updatedAt = Instant.ofEpochMilli(sbn.postTime),
                progress = taskProgress,
                contentIntent = notification.contentIntent,
                bubbleIntent = bubble?.intent,
                sourceNotificationKey = sbn.key,
                groupKey = sbn.groupKey,
            ),
            snapshot = snapshot,
        )
    }

    private fun sanitize(value: Any?, includeDebugText: Boolean): SanitizedText {
        val text = when (value) {
            null -> null
            is CharSequence -> value.toString()
            else -> value.toString()
        }
        return SanitizedText(
            present = text != null,
            length = text?.length ?: 0,
            debugValue = text?.takeIf { includeDebugText }?.take(MAX_DEBUG_TEXT),
        )
    }

    private fun firstText(vararg values: Any?): String? = values.firstNotNullOfOrNull { value ->
        value?.toString()?.trim()?.takeIf(String::isNotBlank)
    }

    private fun String.singleLine(maxLength: Int): String =
        replace(Regex("\\s+"), " ").trim().take(maxLength)

    private fun android.os.Bundle.optionalInt(key: String): Int? =
        if (containsKey(key)) getInt(key) else null

    private fun android.os.Bundle.optionalBoolean(key: String): Boolean? =
        if (containsKey(key)) getBoolean(key) else null

    private companion object {
        const val MAX_DEBUG_TEXT = 2_000
        val DIAGNOSTIC_TEXT_KEYS = listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_CONVERSATION_TITLE,
        )
    }
}
