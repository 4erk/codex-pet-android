package com.fourerk.codexpet.task

enum class TaskAnimationCue {
    ACTIVE,
    WAITING_FOR_INPUT,
    RECONNECTING,
    REVIEWING,
    COMPLETED,
    FAILED,
    DISCONNECTED,
    MESSAGE_RECEIVED,
    UNKNOWN,
}

enum class CueSignalSource {
    STRUCTURED_STATUS,
    TEXT_HEURISTIC,
    NOTIFICATION_ROLE,
    NONE,
}

data class TaskAnimationDecision(
    val cue: TaskAnimationCue,
    val source: CueSignalSource,
)

/**
 * Conservative bilingual cue resolver. Strong states (input/error/connectivity) cannot be erased by
 * a generic verb later in the sentence, while specific later workflow stages such as "now testing"
 * can refine a coarse RUNNING notification.
 */
object TaskAnimationCueResolver {
    private data class Rule(val cue: TaskAnimationCue, val pattern: Regex, val weight: Int)
    private data class TextSignal(val cue: TaskAnimationCue, val score: Int, val lastIndex: Int)

    private val reconnectingWords = Regex(
        "(reconnect(?:ing|ion)?|trying to reconnect|restor(?:e|ing) (?:the )?connection|" +
            "connecting to (?:the )?remote (?:computer|machine|host)|retrying connection|" +
            "переподключ(?:ается|ение|иться)|повторн(?:ое|ая) подключение|" +
            "восстанавливает (?:соединение|подключение|связь)|попытка подключиться)",
        RegexOption.IGNORE_CASE,
    )
    private val disconnectedWords = Regex(
        "(disconnected from (?:the )?remote (?:computer|machine|host)|" +
            "remote (?:computer|machine|host) (?:is )?(?:disconnected|offline|unreachable|unavailable)|" +
            "(?:connection|link) (?:was )?(?:lost|dropped|closed)|lost connection to (?:the )?remote|" +
            "not connected to (?:the )?remote (?:computer|machine|host)|remote session ended|" +
            "отключ[её]н(?:о|а)? от удал[её]нного компьютера|" +
            "нет (?:подключения|соединения|связи) с удал[её]нным компьютером|" +
            "(?:соединение|подключение|связь) (?:потерян(?:о|а)|разорван(?:о|а)|закрыт(?:о|а))|" +
            "удал[её]нный компьютер (?:не в сети|недоступен)|удал[её]нная сессия завершена)",
        RegexOption.IGNORE_CASE,
    )
    private val waitingWords = Regex(
        "(needs? (?:your )?(?:input|approval|answer|permission|decision|confirmation)|" +
            "requires? (?:your |user )?(?:approval|input|answer|permission|decision|confirmation|action)|" +
            "waiting (?:for|on) (?:you|your response|your feedback|input|approval|permission|confirmation)|" +
            "awaiting (?:your )?(?:response|feedback|input|approval|permission|confirmation)|" +
            "(?:approval|permission|confirmation|input|user action|action) required|needs? attention|" +
            "please (?:approve|confirm|choose|select|respond|provide|sign in|log in)|" +
            "(?:choose|select) (?:one|an option)|paused for (?:input|approval|confirmation)|" +
            "ожидает (?:ваш(?:е|его|и|у)? )?(?:ответ|ввод|подтверждение|разрешение|решение)|" +
            "ожидаю (?:ваш(?:его|е|у)? )?(?:ответ|подтверждение|решение)|" +
            "жд[её]т (?:ваш )?(?:ответ|подтверждение|решение)|" +
            "нуж(?:ен|на|но) (?:ваш(?:е|его|а)? )?(?:ответ|ввод|выбор|помощь|подтверждение|разрешение)|" +
            "нужно (?:подтвердить|разрешить|выбрать|ответить|уточнить|указать)|" +
            "необходим(?:о|ы|а) (?:ваш(?:е|его|и|а)? )?(?:ответ|ввод|подтверждение|разрешение|решение|действие)|" +
            "требуется (?:ваш(?:е|его|и|у)? )?(?:ответ|ввод|подтверждение|разрешение|решение|выбор|действие)|" +
            "требует внимания|подтверд(?:ите|ить|и)|разреш(?:ите|ить|и)|" +
            "выбер(?:ите|и) (?:вариант|один)|ответ(?:ьте|ить)|уточн(?:ите|ить)|укаж(?:ите|и))",
        RegexOption.IGNORE_CASE,
    )
    private val failedWords = Regex(
        "(?:^|\\b)(error|failed|failure|blocked|crash(?:ed)?|timed out|timeout|cancelled|canceled|" +
            "cannot continue|cannot complete|can't continue|could not|unable to|denied|rejected|" +
            "permission denied|merge conflict|tests? failed|build failed|dependency failed|" +
            "ошибка|сбой|провал|заблокирован(?:о|а)?|не удалось|не может продолжить|невозможно|" +
            "отказано|запрещено|отклонен(?:о|а)?|нет доступа|отмен[её]н(?:о|а)?|тайм.?аут|" +
            "превышено время|конфликт|тесты упали|сборка упала)(?:\\b|$)",
        RegexOption.IGNORE_CASE,
    )
    private val reviewWords = Regex(
        "(?:^|\\b)(review(?:ing)?|inspect(?:ing|ion)?|validat(?:e|es|ing|ion)|check(?:s|ing)?|" +
            "verif(?:y|ies|ying|ication)|test(?:s|ing)?|audit(?:ing)?|analy[sz](?:e|es|ing|is)|" +
            "lint(?:ing)?|compar(?:e|es|ing|ison)|examin(?:e|es|ing|ation)|evaluat(?:e|es|ing|ion)|" +
            "diagnos(?:e|es|ing|is)|reading logs|running tests|running lint|checking build|" +
            "провер(?:яет|яют|яю|яем|ка|яется|ить)|ревью|валидир(?:ует|уют|ую|уем|ация)|" +
            "анализир(?:ует|уют|ую|уем|уется)|тестир(?:ует|уют|ую|уем|ование)|свер(?:яет|яют)|" +
            "аудит|линтинг|сравнивает|изучает|оценивает|диагностирует|читает логи|запускает тесты)(?:\\b|$)",
        RegexOption.IGNORE_CASE,
    )
    private val completedWords = Regex(
        "(?:^|\\b)(completed|complete|done|finished|ready|succeeded|successful|success|" +
            "passed|fixed|implemented|deployed|delivered|merged|resolved|all tests pass(?:ed)?|build passed|" +
            "готов(?:о|а|ы)?|заверш[её]н(?:о|а|ы)?|выполнен(?:о|а|ы)?|успешно|" +
            "исправлен(?:о|а|ы)?|реализован(?:о|а|ы)?|разв[её]рнут(?:о|а)?|доставлен(?:о|а)?|" +
            "объединен(?:о|а)?|решен(?:о|а)?|тесты прошли|сборка успешна)(?:\\b|$)",
        RegexOption.IGNORE_CASE,
    )
    private val resumedWords = Regex(
        "(?:^|\\b)(retrying|resuming|resumed|continuing|recovered and continuing|back online and working|" +
            "повторяет попытку|возобнов(?:ляет|ил) работу|продолжает после ошибки|связь восстановлена,? продолжает)(?:\\b|$)",
        RegexOption.IGNORE_CASE,
    )
    private val activeWords = Regex(
        "(?:^|\\b)(working|thinking|processing|implementing|building|editing|writing|executing|" +
            "installing|downloading|searching|researching|fixing|coding|compiling|deploying|running|" +
            "creating|updating|refactoring|configuring|generating|preparing|resolving|migrating|" +
            "committing|pushing|fetching|cloning|packaging|starting|continuing|" +
            "выполняет|работает|думает|обрабатывает|реализует|созда[её]т|редактирует|пишет|" +
            "запускает|устанавливает|скачивает|ищет|исследует|исправляет|компилирует|собирает|" +
            "разворачивает|обновляет|рефакторит|настраивает|генерирует|готовит|решает|переносит|" +
            "коммитит|пушит|получает|клонирует|упаковывает|начинает|продолжает)(?:\\b|$)",
        RegexOption.IGNORE_CASE,
    )

    private val rules = listOf(
        Rule(TaskAnimationCue.WAITING_FOR_INPUT, waitingWords, 105),
        Rule(TaskAnimationCue.RECONNECTING, reconnectingWords, 101),
        Rule(TaskAnimationCue.DISCONNECTED, disconnectedWords, 98),
        Rule(TaskAnimationCue.FAILED, failedWords, 95),
        Rule(TaskAnimationCue.ACTIVE, resumedWords, 94),
        Rule(TaskAnimationCue.COMPLETED, completedWords, 88),
        Rule(TaskAnimationCue.REVIEWING, reviewWords, 72),
        Rule(TaskAnimationCue.ACTIVE, activeWords, 40),
    )

    fun resolve(status: TaskStatus, text: String?): TaskAnimationDecision {
        val signal = strongestTextSignal(text.orEmpty())
        val cue = when (status) {
            TaskStatus.ERROR -> when (signal?.cue) {
                TaskAnimationCue.WAITING_FOR_INPUT,
                TaskAnimationCue.RECONNECTING,
                TaskAnimationCue.DISCONNECTED,
                TaskAnimationCue.FAILED -> signal.cue
                else -> TaskAnimationCue.FAILED
            }
            TaskStatus.COMPLETED -> when (signal?.cue) {
                TaskAnimationCue.FAILED, TaskAnimationCue.DISCONNECTED -> signal.cue
                else -> TaskAnimationCue.COMPLETED
            }
            TaskStatus.RUNNING -> signal?.cue ?: TaskAnimationCue.ACTIVE
            TaskStatus.UNKNOWN -> signal?.cue ?: TaskAnimationCue.UNKNOWN
        }
        val source = if (signal != null && cue == signal.cue) {
            CueSignalSource.TEXT_HEURISTIC
        } else if (status != TaskStatus.UNKNOWN) {
            CueSignalSource.STRUCTURED_STATUS
        } else {
            CueSignalSource.NONE
        }
        return TaskAnimationDecision(cue, source)
    }

    private fun strongestTextSignal(text: String): TextSignal? {
        if (text.isBlank()) return null
        return rules.mapNotNull { rule ->
            val match = rule.pattern.findAll(text).lastOrNull() ?: return@mapNotNull null
            val recency = ((match.range.last + 1).toDouble() / text.length.coerceAtLeast(1) * RECENCY_BONUS)
                .toInt()
            TextSignal(rule.cue, rule.weight + recency, match.range.last)
        }.maxWithOrNull(
            compareBy<TextSignal>(TextSignal::score).thenBy(TextSignal::lastIndex),
        )
    }

    private const val RECENCY_BONUS = 30
}
