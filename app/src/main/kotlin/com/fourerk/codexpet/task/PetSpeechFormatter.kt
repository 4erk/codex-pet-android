package com.fourerk.codexpet.task

import com.fourerk.codexpet.settings.SpeechStyle

/**
 * Turns notification-derived status text into compact pet speech without throwing the exact
 * notification payload away. Friendly mode only adds a human lead; chat messages stay verbatim.
 */
object PetSpeechFormatter {
    fun format(item: PetSpeechItem, style: SpeechStyle): PetSpeechItem {
        if (style == SpeechStyle.EXACT || item.task.kind == TaskKind.CHAT_MESSAGE) return item

        val raw = item.text.trim()
        val lead = friendlyLead(item.task)
        val text = when {
            raw.isBlank() -> lead
            isGenericStatus(raw, item.task.animationCue) -> lead
            startsWithEquivalent(raw, lead) -> raw
            else -> "$lead: $raw"
        }.take(MAX_FRIENDLY_TEXT)

        return item.copy(text = text)
    }

    internal fun friendlyLead(task: CodexTask): String {
        val variants = when (task.animationCue) {
            TaskAnimationCue.WAITING_FOR_INPUT -> listOf(
                "Тут нужен ты",
                "Без тебя дальше не двинусь",
                "Нужен твой ответ",
            )
            TaskAnimationCue.DISCONNECTED -> listOf(
                "Похоже, связь отвалилась",
                "Компьютер пропал со связи",
                "Связь с компьютером потерялась",
            )
            TaskAnimationCue.RECONNECTING -> listOf(
                "Пробую вернуть связь",
                "Переподключаюсь",
                "Пытаюсь снова достучаться до компьютера",
            )
            TaskAnimationCue.FAILED -> listOf(
                "Тут что-то сломалось",
                "Споткнулся об ошибку",
                "Похоже, дальше мешает ошибка",
            )
            TaskAnimationCue.COMPLETED -> listOf(
                "Готово",
                "Есть, сделал",
                "Закончил",
            )
            TaskAnimationCue.REVIEWING -> listOf(
                "Проверяю, что всё сходится",
                "Сверяю результат",
                "Сейчас всё перепроверю",
            )
            TaskAnimationCue.MESSAGE_RECEIVED -> listOf("Новое сообщение")
            TaskAnimationCue.ACTIVE -> listOf(
                "Я в деле",
                "Сейчас разбираюсь",
                "Работаю над этим",
            )
            TaskAnimationCue.UNKNOWN -> when (task.status) {
                TaskStatus.COMPLETED -> listOf("Готово")
                TaskStatus.ERROR -> listOf("Тут что-то сломалось")
                TaskStatus.RUNNING -> listOf("Я в деле")
                TaskStatus.UNKNOWN -> listOf("Есть обновление")
            }
        }
        return variants[Math.floorMod(task.id.hashCode(), variants.size)]
    }

    /**
     * Only truly content-free statuses are replaced. Anything that names the failing component,
     * remote endpoint, command, test, error code, requested choice, etc. is useful payload and must
     * stay visible after the friendly lead.
     */
    private fun isGenericStatus(text: String, cue: TaskAnimationCue): Boolean {
        val normalized = text.lowercase().trim().removeSuffix(".").removeSuffix("!")
        return when (cue) {
            TaskAnimationCue.WAITING_FOR_INPUT -> normalized in setOf(
                "waiting for input", "needs input", "нужен ответ", "нужно подтверждение", "ожидает ответа",
            )
            TaskAnimationCue.DISCONNECTED -> normalized in setOf(
                "disconnected", "connection lost", "соединение потеряно", "нет связи",
            )
            TaskAnimationCue.RECONNECTING -> normalized in setOf(
                "reconnecting", "trying to reconnect", "переподключение", "переподключается",
            )
            TaskAnimationCue.FAILED -> normalized in setOf(
                "failed", "error", "ошибка", "сбой", "не удалось",
            )
            TaskAnimationCue.COMPLETED -> normalized in setOf(
                "done", "completed", "complete", "ready", "готово", "завершено",
            )
            TaskAnimationCue.REVIEWING -> normalized in setOf(
                "reviewing", "checking", "validating", "проверяет", "проверка",
            )
            TaskAnimationCue.ACTIVE -> normalized in setOf(
                "working", "running", "processing", "работает", "выполняет", "в работе",
            )
            TaskAnimationCue.MESSAGE_RECEIVED, TaskAnimationCue.UNKNOWN -> false
        }
    }

    private fun startsWithEquivalent(text: String, lead: String): Boolean {
        val normalizedText = text.lowercase()
        val normalizedLead = lead.lowercase()
        return normalizedText.startsWith(normalizedLead) ||
            (lead == "Готово" && normalizedText.startsWith("готов"))
    }

    private const val MAX_FRIENDLY_TEXT = 600
}
