package com.fourerk.codexpet.task

data class TaskSignals(
    val progress: Int?,
    val progressMax: Int?,
    val progressIndeterminate: Boolean,
    val ongoing: Boolean,
    val fallbackText: String?,
)

object TaskStatusResolver {
    private val errorWords = Regex(
        "(?:^|\\b)(error|failed|failure|blocked|ошибка|ошибкой|сбой|не удалось)(?:\\b|$)",
        RegexOption.IGNORE_CASE,
    )
    private val completedWords = Regex(
        "(?:^|\\b)(completed|complete|done|finished|ready|готов(?:о|а|ы)?|завершен(?:о|а|ы)?|выполнен(?:о|а|ы)?)(?:\\b|$)",
        RegexOption.IGNORE_CASE,
    )

    fun resolve(signals: TaskSignals): TaskStatus {
        if (signals.progressIndeterminate) return TaskStatus.RUNNING
        if (signals.progress != null && signals.progressMax != null && signals.progressMax > 0 &&
            signals.progress in 0 until signals.progressMax
        ) {
            return TaskStatus.RUNNING
        }
        if (signals.progress != null && signals.progressMax != null && signals.progressMax > 0 &&
            signals.progress >= signals.progressMax
        ) {
            return TaskStatus.COMPLETED
        }
        if (signals.ongoing) return TaskStatus.RUNNING

        val text = signals.fallbackText.orEmpty()
        if (errorWords.containsMatchIn(text)) return TaskStatus.ERROR
        if (completedWords.containsMatchIn(text)) return TaskStatus.COMPLETED
        return TaskStatus.UNKNOWN
    }
}
