package com.fourerk.codexpet.pet

data class PetAnimationDescriptor(
    val state: PetAnimationState,
    val title: String,
    val whenShort: String,
    val fullConditions: String,
)

object PetAnimationCatalog {
    val all: List<PetAnimationDescriptor> = listOf(
        PetAnimationDescriptor(
            PetAnimationState.IDLE,
            "Спокойно стоит",
            "Нет активной работы, проверки или проблемы.",
            "Базовое состояние. Завершённое уведомление само по себе больше не удерживает REVIEW: после короткой реакции на успех пет возвращается сюда, если других активных задач нет.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.RUNNING,
            "Работает",
            "Codex выполняет реальную работу.",
            "TaskStatus.RUNNING или уверенный ACTIVE-сигнал: код, сборка, загрузка, настройка, генерация и другие действия. Слабое слово вроде building не может перебить явную ошибку или ожидание пользователя.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.REVIEW,
            "Проверяет",
            "Тесты, lint, ревью, аудит, чтение логов.",
            "Включается только для текущего REVIEWING-состояния. Если одновременно есть обычная работа — проверка важнее; если ошибка или запрос ответа — показывается более важное FAILED/WAITING.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.WAITING,
            "Ждёт тебя / связь",
            "Нужен ответ или идёт восстановление соединения.",
            "WAITING_FOR_INPUT имеет максимальный пользовательский приоритет: подтверждение, выбор, разрешение, ответ. RECONNECTING использует ту же спокойную анимацию ожидания, но при успешном восстановлении дополнительно проигрывается короткое WAVING.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.FAILED,
            "Проблема",
            "Ошибка, блокировка или связь потеряна.",
            "TaskStatus.ERROR, FAILED или DISCONNECTED. Явная ошибка не заменяется обычным рабочим глаголом из того же текста. Если затем появляется явное retry/resume/reconnect, состояние может корректно измениться.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.JUMPING,
            "Успех",
            "Короткая реакция на завершение задачи.",
            "Одноразово при переходе в COMPLETED. Не закрепляется из-за старого completed notification и не перебивает более важную текущую ошибку/ожидание. После цикла выбирается актуальное steady-состояние всех задач.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.WAVING,
            "Контакт восстановлен",
            "Приветствие, новое сообщение или возврат связи.",
            "Одноразово при ручном открытии реплик, новом обычном сообщении ChatGPT и успешном переходе DISCONNECTED/RECONNECTING → рабочее состояние. После этого пет может посмотреть в сторону реплик через 16-direction look pack.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.RUNNING_LEFT,
            "Бежит влево",
            "Drag или snap влево.",
            "Жестовое состояние. Используется не только при ручном перетаскивании, но и на короткой анимации snap к левому краю. Новое касание немедленно отменяет snap.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.RUNNING_RIGHT,
            "Бежит вправо",
            "Drag или snap вправо.",
            "Жестовое состояние. Используется при движении вправо и snap к правому краю. Направление определяется по фактической последней дельте, а не по первоначальной точке касания.",
        ),
    )

    fun descriptor(state: PetAnimationState): PetAnimationDescriptor? = all.firstOrNull { it.state == state }
}
