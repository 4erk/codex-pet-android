package com.fourerk.codexpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PetPackArchiveReaderTest {
    @Test
    fun `returns only png and webp entries without extracting`() {
        val archive = zip(
            "docs/readme.txt" to byteArrayOf(1),
            "pet/spritesheet.png" to byteArrayOf(2, 3, 4),
            "pet/preview.webp" to byteArrayOf(5, 6),
        )

        val entries = PetPackArchiveReader.read(ByteArrayInputStream(archive)).getOrThrow()

        assertEquals(listOf("pet/spritesheet.png", "pet/preview.webp"), entries.map { it.name })
    }

    @Test
    fun `rejects path traversal`() {
        val result = PetPackArchiveReader.read(
            ByteArrayInputStream(zip("../pet.png" to byteArrayOf(1, 2, 3))),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects archive without supported image`() {
        val result = PetPackArchiveReader.read(
            ByteArrayInputStream(zip("pet/pet.json" to "{}".encodeToByteArray())),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects windows style absolute path`() {
        val result = PetPackArchiveReader.read(
            ByteArrayInputStream(zip("C:/pet.webp" to byteArrayOf(1, 2, 3))),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `recognizes standard zip signatures`() {
        assertTrue(PetPackArchiveReader.looksLikeZip(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
}
