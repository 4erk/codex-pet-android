package com.fourerk.codexpet.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateModelsTest {
    @Test
    fun `semantic version comparison ignores v prefix and prerelease suffix`() {
        assertTrue(SemanticVersion.isNewer("v0.5.1", "0.5.0"))
        assertTrue(SemanticVersion.isNewer("1.0.0", "0.9.99"))
        assertFalse(SemanticVersion.isNewer("0.5.0", "0.5.0-beta1"))
    }

    @Test
    fun `selector accepts only deterministic stable asset for the release tag`() {
        val selected = ReleaseAssetSelector.select(
            listOf(
                ReleaseAssetInfo("app-debug.apk", "debug", 9, null),
                ReleaseAssetInfo("app-release.apk", "release", 10, null),
                ReleaseAssetInfo("codex-pet-0.5.0.apk", "stable", 8, "sha256:abc"),
                ReleaseAssetInfo("codex-pet-0.5.1.apk", "other", 8, "sha256:def"),
            ),
            version = "0.5.0",
        )

        assertEquals("codex-pet-0.5.0.apk", selected?.name)
    }

    @Test
    fun `selector refuses generic release apk when stable asset is missing`() {
        val selected = ReleaseAssetSelector.select(
            listOf(
                ReleaseAssetInfo("app-release.apk", "release", 10, "sha256:abc"),
                ReleaseAssetInfo("codex-pet-0.5.0-debug.apk", "debug", 10, "sha256:def"),
            ),
            version = "0.5.0",
        )

        assertNull(selected)
    }
}
