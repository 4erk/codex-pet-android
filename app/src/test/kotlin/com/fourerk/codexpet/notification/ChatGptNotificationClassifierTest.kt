package com.fourerk.codexpet.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptNotificationClassifierTest {
    @Test
    fun `separates known Codex status avatar and regular ChatGPT channels`() {
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

    @Test
    fun `renamed Codex channel is recovered from structured execution signals`() {
        val result = ChatGptNotificationClassifier.classifySignals(
            ChatGptNotificationSignals(
                channelId = "background_activity_v3",
                shortcutId = "codex-session-42",
                title = "Remote task",
                text = "Building project",
                ongoing = true,
                hasProgress = true,
                hasBubble = true,
            ),
        )

        assertEquals(ChatGptNotificationRole.CODEX_TASK, result.role)
        assertTrue(result.confidence >= 80)
    }

    @Test
    fun `ordinary chat stays a message even if it has bubble metadata`() {
        val result = ChatGptNotificationClassifier.classifySignals(
            ChatGptNotificationSignals(
                channelId = "messages",
                shortcutId = "conversation-123",
                title = "Alice",
                text = "New message",
                ongoing = false,
                hasProgress = false,
                hasBubble = true,
            ),
        )

        assertEquals(ChatGptNotificationRole.CHAT_MESSAGE, result.role)
    }

    @Test
    fun `Codex text plus progress survives a generic channel rename`() {
        val result = ChatGptNotificationClassifier.classifySignals(
            ChatGptNotificationSignals(
                channelId = "activity",
                shortcutId = null,
                title = "Codex",
                text = "Remote computer is running tests",
                ongoing = true,
                hasProgress = true,
                hasBubble = false,
            ),
        )

        assertEquals(ChatGptNotificationRole.CODEX_TASK, result.role)
    }
}
