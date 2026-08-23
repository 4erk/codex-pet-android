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

/** Text chooses the most specific animation first; structured status remains the fallback. */
object TaskAnimationCueResolver {
    private val reconnectingWords = Regex(
        "(reconnect(?:ing|ion)?|trying to reconnect|restor(?:e|ing) (?:the )?connection|" +
            "connecting to (?:the )?remote (?:computer|machine|host)|" +
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
            "unavailable|permission denied|merge conflict|tests? failed|build failed|" +
            "ошибка|сбой|провал|заблокирован(?:о|а)?|не удалось|не может продолжить|невозможно|" +
            "отказано|запрещено|отклонен(?:о|а)?|нет доступа|отмен[её]н(?:о|а)?|тайм.?аут|" +
            "превышено время|конфликт|тесты упали|сборка упала)(?:\\b|$)",
        RegexOption.IGNORE_CASE,
    )
    private val reviewWords = Regex(
        "(?:^|\\b)(review(?:ing)?|inspect(?:ing|ion)?|validat(?:e|es|ing|ion)|check(?:s|ing)?|" +
            "verif(?:y|ies|ying|ication)|test(?:s|ing)?|audit(?:ing)?|analy[sz](?:e|es|ing|is)|" +
            "lint(?:ing)?|compar(?:e|es|ing|ison)|examin(?:e|es|ing|ation)|evaluat(?:e|es|ing|ion)|" +
            "diagnos(?:e|es|ing|is)|monitor(?:s|ing)?|reading logs|running tests|" +
            "провер(?:яет|яют|яю|яем|ка|яется|ить)|ревью|валидир(?:ует|уют|ую|уем|ация)|" +
            "анализир(?:ует|уют|ую|уем|уется)|тестир(?:ует|уют|ую|уем|ование)|свер(?:яет|яют)|" +
            "аудит|линтинг|сравнивает|изучает|оценивает|диагностирует|читает логи|запускает тесты)(?:\\b|$)",
        RegexOption.IGNORE_CASE,
    )
    private val completedWords = Regex(
        "(?:^|\\b)(completed|complete|done|finished|ready|succeeded|successful|success|" +
            "passed|fixed|implemented|deployed|delivered|merged|resolved|all tests pass|" +
            "готов(?:о|а|ы)?|заверш[её]н(?:о|а|ы)?|выполнен(?:о|а|ы)?|успешно|" +
            "исправлен(?:о|а|ы)?|реализован(?:о|а|ы)?|разв[её]рнут(?:о|а)?|доставлен(?:о|а)?|" +
            "объединен(?:о|а)?|решен(?:о|а)?|тесты прошли|сборка успешна)(?:\\b|$)",
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

    private val textSignals = listOf(
        TaskAnimationCue.ACTIVE to activeWords,
        TaskAnimationCue.COMPLETED to completedWords,
        TaskAnimationCue.REVIEWING to reviewWords,
        TaskAnimationCue.FAILED to failedWords,
        TaskAnimationCue.DISCONNECTED to disconnectedWords,
        TaskAnimationCue.WAITING_FOR_INPUT to waitingWords,
        TaskAnimationCue.RECONNECTING to reconnectingWords,
    )

    fun resolve(status: TaskStatus, text: String?): TaskAnimationDecision {
        val value = text.orEmpty()
        val latestSignal = textSignals.mapIndexedNotNull { priority, (cue, pattern) ->
            pattern.findAll(value).lastOrNull()?.let { match ->
                TextSignal(cue, match.range.last, priority)
            }
        }.maxWithOrNull(compareBy<TextSignal>(TextSignal::lastIndex).thenBy(TextSignal::priority))
        if (latestSignal != null) {
            return TaskAnimationDecision(latestSignal.cue, CueSignalSource.TEXT_HEURISTIC)
        }
        return when (status) {
            TaskStatus.RUNNING -> TaskAnimationDecision(TaskAnimationCue.ACTIVE, CueSignalSource.STRUCTURED_STATUS)
            TaskStatus.COMPLETED -> TaskAnimationDecision(TaskAnimationCue.COMPLETED, CueSignalSource.STRUCTURED_STATUS)
            TaskStatus.ERROR -> TaskAnimationDecision(TaskAnimationCue.FAILED, CueSignalSource.STRUCTURED_STATUS)
            TaskStatus.UNKNOWN -> TaskAnimationDecision(TaskAnimationCue.UNKNOWN, CueSignalSource.NONE)
        }
    }

    private data class TextSignal(
        val cue: TaskAnimationCue,
        val lastIndex: Int,
        val priority: Int,
    )
}
