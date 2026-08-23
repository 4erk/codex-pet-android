package com.fourerk.codexpet.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerRecoveryPolicyTest {
    @Test
    fun `rebind delay backs off and caps`() {
        assertEquals(1_500L, ListenerRecoveryPolicy.delayMillis(0))
        assertEquals(3_000L, ListenerRecoveryPolicy.delayMillis(1))
        assertEquals(60_000L, ListenerRecoveryPolicy.delayMillis(99))
    }

    @Test
    fun `three consecutive active notification failures force recovery`() {
        assertFalse(ListenerRecoveryPolicy.shouldForceRebind(1))
        assertFalse(ListenerRecoveryPolicy.shouldForceRebind(2))
        assertTrue(ListenerRecoveryPolicy.shouldForceRebind(3))
    }
}
