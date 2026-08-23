package com.fourerk.codexpet.settings

const val DEFAULT_CHATGPT_PACKAGE = "com.openai.chatgpt"

enum class LongPressAction {
    MENU,
    OPEN_CHATGPT,
    HIDE,
}

enum class SpeechStyle {
    FRIENDLY,
    EXACT,
}

data class AppSettings(
    val sourcePackage: String = DEFAULT_CHATGPT_PACKAGE,
    val overlayEnabled: Boolean = false,
    val petVisible: Boolean = true,
    val autoStart: Boolean = false,
    val snapEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val animationSpeed: Float = 1f,
    val petSizeDp: Int = 72,
    val bubbleScale: Float = 1f,
    val portraitX: Int = -1,
    val portraitY: Int = -1,
    val landscapeX: Int = -1,
    val landscapeY: Int = -1,
    val completedVisibleSeconds: Int = 5,
    val autoTaskBubblesEnabled: Boolean = true,
    val attentionBubblesEnabled: Boolean = true,
    val chatMessageBubblesEnabled: Boolean = true,
    val completionBubblesEnabled: Boolean = true,
    val speechStyle: SpeechStyle = SpeechStyle.FRIENDLY,
    val maxVisibleBubbles: Int = 5,
    val longPressAction: LongPressAction = LongPressAction.MENU,
    val lastPetHash: String? = null,
    val lastPetUpdatedAt: Long? = null,
    val lastPetAssetSource: String? = null,
    val lastPetSourcePackage: String? = null,
)
