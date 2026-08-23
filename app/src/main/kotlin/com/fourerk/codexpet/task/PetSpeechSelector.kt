package com.fourerk.codexpet.task

import java.time.Duration
import java.time.Instant

enum class PetSpeechPriority(val rank: Int) {
    NEEDS_INPUT(0),
    BLOCKED(1),
    READY(2),
    RUNNING(3),
}

data class PetSpeechPolicy(
    val automaticEnabled: Boolean,
    val ongoingEnabled: Boolean,
    val attentionEnabled: Boolean,
    val chatMessagesEnabled: Boolean,
    val completionsEnabled: Boolean,
    val completedVisibleSeconds: Int,
    val chatMessageVisibleSeconds: Int = 15,
)

data class PetSpeechItem(
    val task: CodexTask,
    val title: String?,
    val text: String,
    val priority: PetSpeechPriority,
    val expiresAt: Instant?,
)

/** Selects only notification-derived context that is useful in a compact pet speech bubble. */
object PetSpeechSelector {
    fun select(
        tasks: List<CodexTask>,
        policy: PetSpeechPolicy,
        now: Instant = Instant.now(),
    ): List<PetSpeechItem> = if (!policy.automaticEnabled) {
        emptyList()
    } else tasks
        .asSequence()
        .filter(CodexTask::isDisplayTask)
        .mapNotNull { task -> task.toSpeechItem(policy, now) }
        .sortedWith(
            compareBy<PetSpeechItem> { it.priority.rank }
                .thenByDescending { it.task.updatedAt },
        )
        .toList()

    private fun CodexTask.toSpeechItem(policy: PetSpeechPolicy, now: Instant): PetSpeechItem? {
        val category = category(policy) ?: return null
        val expiresAt = expiration(policy, category)
        if (expiresAt != null && !now.isBefore(expiresAt)) return null
        val body = meaningfulBody() ?: return null
        return PetSpeechItem(
            task = this,
            title = meaningfulTitle(body),
            text = body,
            priority = category,
            expiresAt = expiresAt,
        )
    }

    private fun CodexTask.category(policy: PetSpeechPolicy): PetSpeechPriority? = when {
        kind == TaskKind.CHAT_MESSAGE ->
            PetSpeechPriority.READY.takeIf { policy.chatMessagesEnabled }
        !isCodexTask() -> null
        animationCue == TaskAnimationCue.WAITING_FOR_INPUT ->
            PetSpeechPriority.NEEDS_INPUT.takeIf { policy.attentionEnabled }
        status == TaskStatus.ERROR ||
            animationCue == TaskAnimationCue.FAILED ||
            animationCue == TaskAnimationCue.DISCONNECTED ||
            animationCue == TaskAnimationCue.RECONNECTING ->
            PetSpeechPriority.BLOCKED.takeIf { policy.attentionEnabled }
        status == TaskStatus.COMPLETED || animationCue == TaskAnimationCue.COMPLETED ->
            PetSpeechPriority.READY.takeIf {
                policy.completionsEnabled && policy.completedVisibleSeconds > 0
            }
        status == TaskStatus.RUNNING ||
            animationCue == TaskAnimationCue.ACTIVE ||
            animationCue == TaskAnimationCue.REVIEWING ->
            PetSpeechPriority.RUNNING.takeIf { policy.ongoingEnabled }
        else -> null
    }

    private fun CodexTask.expiration(
        policy: PetSpeechPolicy,
        priority: PetSpeechPriority,
    ): Instant? = when {
        kind == TaskKind.CHAT_MESSAGE -> updatedAt.plusSeconds(policy.chatMessageVisibleSeconds.toLong())
        priority == PetSpeechPriority.READY -> updatedAt.plusSeconds(policy.completedVisibleSeconds.toLong())
        else -> null
    }

    private fun CodexTask.meaningfulBody(): String? {
        val candidates = listOf(summary, detail, title)
        return candidates.firstNotNullOfOrNull { candidate ->
            candidate
                ?.normalizeSpeechText()
                ?.takeIf { it.isNotBlank() && !GENERIC_TEXT.matches(it) }
        }
    }

    private fun CodexTask.meaningfulTitle(body: String): String? = title
        .normalizeSpeechText()
        .takeIf { it.isNotBlank() && !GENERIC_TEXT.matches(it) }
        ?.takeIf { candidate ->
            !body.equals(candidate, ignoreCase = true) && !body.startsWith(candidate, ignoreCase = true)
        }

    private fun String.normalizeSpeechText(): String =
        replace('\r', '\n')
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .replace(WHITESPACE, " ")
            .trim()
            .take(MAX_SPEECH_CHARACTERS)

    fun contentSignature(items: List<PetSpeechItem>): String = items.joinToString("|") { item ->
        "${item.task.sourceNotificationKey}:${item.task.updatedAt.toEpochMilli()}:${item.text}"
    }

    fun nextExpirationDelayMillis(items: List<PetSpeechItem>, now: Instant = Instant.now()): Long? =
        items.mapNotNull(PetSpeechItem::expiresAt)
            .minOrNull()
            ?.let { expiration ->
                Duration.between(now, expiration).toMillis().coerceAtLeast(1L)
            }

    private val WHITESPACE = Regex("\\s+")
    private val GENERIC_TEXT = Regex(
        "(?:codex(?: task)?|chatgpt notification group|task|задача codex|задача|уведомление chatgpt)",
        RegexOption.IGNORE_CASE,
    )
    private const val MAX_SPEECH_CHARACTERS = 600
}
