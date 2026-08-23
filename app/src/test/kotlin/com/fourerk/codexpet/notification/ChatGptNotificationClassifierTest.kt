package com.fourerk.codexpet.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatGptNotificationClassifierTest {
    @Test
    fun `separates Codex status avatar and regular ChatGPT channels`() {
        assertEquals(
            ChatGptNotificationRole.CODEX_TASK,
            ChatGptNotificationClassifier.classifyChannelId("codex_remote_session"),
        )
        assertEquals(
            ChatGptNotificationRole.CODEX_AVATAR,
            ChatGptNotificationClassifier.classifyChannelId("codex_remote_session.avatar"),
        )
        assertEquals(
            ChatGptNotificationRole.CHAT_MESSAGE,
            ChatGptNotificationClassifier.classifyChannelId("messages"),
        )
    }
}
