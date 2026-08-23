package com.fourerk.codexpet.app

import com.fourerk.codexpet.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class PackageIdentityTest {
    @Test
    fun `debug variant keeps mr4erk production application id base`() {
        assertEquals("com.mr4erk.codexpet.debug", BuildConfig.APPLICATION_ID)
        assertEquals("com.mr4erk.codexpet", BuildConfig.APPLICATION_ID.removeSuffix(".debug"))
    }
}
