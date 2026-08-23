package com.fourerk.codexpet.pet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetSpriteSheetGeometryTest {
    @Test
    fun `accepts v1 v2 and lossless two-x atlas sizes`() {
        assertTrue(PetSpriteSheetParser.hasSupportedGeometry(1536, 1872))
        assertTrue(PetSpriteSheetParser.hasSupportedGeometry(1536, 2288))
        assertTrue(PetSpriteSheetParser.hasSupportedGeometry(3072, 3744))
        assertTrue(PetSpriteSheetParser.hasSupportedGeometry(3072, 4576))
    }

    @Test
    fun `rejects arbitrary static images and oversized atlases`() {
        assertFalse(PetSpriteSheetParser.hasSupportedGeometry(192, 208))
        assertFalse(PetSpriteSheetParser.hasSupportedGeometry(6144, 7488))
        assertFalse(PetSpriteSheetParser.hasSupportedGeometry(1536, 2048))
    }
}
