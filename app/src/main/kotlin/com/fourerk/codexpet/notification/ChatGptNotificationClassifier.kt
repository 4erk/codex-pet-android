package com.fourerk.codexpet.notification

import android.app.Notification

enum class ChatGptNotificationRole {
    CODEX_TASK,
    CODEX_AVATAR,
    CHAT_MESSAGE,
}

/** Keeps regular ChatGPT chats interactive without presenting them as Codex execution tasks. */
object ChatGptNotificationClassifier {
    fun classify(notification: Notification): ChatGptNotificationRole {
        return classifyChannelId(notification.channelId)
    }

    internal fun classifyChannelId(channelId: String?): ChatGptNotificationRole {
        val channel = channelId.orEmpty()
        if (!channel.startsWith(CODEX_REMOTE_CHANNEL_PREFIX)) {
            return ChatGptNotificationRole.CHAT_MESSAGE
        }
        return if (channel.endsWith(AVATAR_CHANNEL_SUFFIX)) {
            ChatGptNotificationRole.CODEX_AVATAR
        } else {
            ChatGptNotificationRole.CODEX_TASK
        }
    }

    private const val CODEX_REMOTE_CHANNEL_PREFIX = "codex_remote_session"
    private const val AVATAR_CHANNEL_SUFFIX = ".avatar"
}
