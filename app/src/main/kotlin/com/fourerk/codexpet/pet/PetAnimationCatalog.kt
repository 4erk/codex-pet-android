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
            "Когда ничего срочного не происходит.",
            "Проигрывается, когда нет активной Codex-задачи, ожидания ответа, ошибки, проверки или временной реакции. После тестовой/одноразовой анимации питомец возвращается сюда, если другое состояние не стало важнее.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.RUNNING,
            "Работает",
            "Пока Codex реально выполняет задачу.",
            "Основное рабочее состояние: TaskStatus.RUNNING или cue ACTIVE. Не включается, если одновременно есть более важное ожидание ответа, потеря связи/ошибка или состояние проверки.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.REVIEW,
            "Проверяет",
            "Во время тестов, ревью, валидации и проверки результата.",
            "Включается при cue REVIEWING. Также может удерживаться после COMPLETED, пока соответствующее уведомление ещё присутствует, чтобы не изображать обычную работу после фактического завершения.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.WAITING,
            "Ждёт тебя",
            "Когда нужен ответ/выбор или идёт переподключение.",
            "Имеет высокий приоритет. Включается при WAITING_FOR_INPUT и RECONNECTING. Удерживается, пока такое notification-состояние реально существует; завершение или обычная работа его не перебивают.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.FAILED,
            "Что-то пошло не так",
            "При ошибке или потерянной связи.",
            "Включается при TaskStatus.ERROR, cue FAILED или DISCONNECTED. Если одновременно есть WAITING_FOR_INPUT, ожидание пользователя важнее и показывается WAITING.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.JUMPING,
            "Радуется",
            "Коротко, когда задача завершилась.",
            "Одноразовая реакция на переход в COMPLETED. После полного цикла автоматически возвращается к самому важному текущему состоянию. Не перебивает уже активное WAITING/FAILED.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.WAVING,
            "Машет",
            "При приветствии и новом сообщении.",
            "Одноразовая реакция при ручном открытии реплик и при новом обычном ChatGPT-сообщении. После неё питомец может на короткое время посмотреть в сторону баблов, если pack содержит 16 направлений взгляда.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.RUNNING_LEFT,
            "Бежит влево",
            "Пока ты перетаскиваешь питомца влево.",
            "Это жестовое состояние, а не состояние Codex. Включается во время drag, если текущая горизонтальная дельта отрицательная. После отпускания возвращается к состоянию задач.",
        ),
        PetAnimationDescriptor(
            PetAnimationState.RUNNING_RIGHT,
            "Бежит вправо",
            "Пока ты перетаскиваешь питомца вправо.",
            "Это жестовое состояние, а не состояние Codex. Включается во время drag, если текущая горизонтальная дельта положительная. После отпускания возвращается к состоянию задач.",
        ),
    )

    fun descriptor(state: PetAnimationState): PetAnimationDescriptor? = all.firstOrNull { it.state == state }
}
