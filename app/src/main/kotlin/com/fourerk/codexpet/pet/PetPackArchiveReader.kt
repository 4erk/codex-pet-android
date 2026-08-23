package com.fourerk.codexpet.pet

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class PetPackArchiveEntry(
    val name: String,
    val bytes: ByteArray,
)

/** Reads image candidates from a ZIP without ever extracting attacker-controlled paths. */
object PetPackArchiveReader {
    fun read(input: InputStream): Result<List<PetPackArchiveEntry>> = runCatching {
        val candidates = mutableListOf<PetPackArchiveEntry>()
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= MAX_ENTRIES) { "ZIP содержит слишком много файлов" }
                val name = entry.name.orEmpty()
                require(isSafeEntryName(name)) { "ZIP содержит небезопасный путь: $name" }
                if (!entry.isDirectory) {
                    val keep = isSupportedImageName(name)
                    val output = if (keep) ByteArrayOutputStream() else null
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var entryBytes = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes += count
                        totalBytes += count
                        require(entryBytes <= MAX_ENTRY_BYTES) { "Файл в ZIP слишком большой: $name" }
                        require(totalBytes <= MAX_TOTAL_BYTES) { "Распакованный ZIP слишком большой" }
                        output?.write(buffer, 0, count)
                    }
                    if (output != null && output.size() > 0) {
                        candidates += PetPackArchiveEntry(name, output.toByteArray())
                    }
                }
                zip.closeEntry()
            }
        }
        require(entryCount > 0) { "ZIP пуст" }
        require(candidates.isNotEmpty()) { "В ZIP нет PNG/WebP pet pack" }
        candidates
    }

    fun looksLikeZip(header: ByteArray): Boolean = header.size >= 4 &&
        header[0] == 'P'.code.toByte() &&
        header[1] == 'K'.code.toByte() &&
        ((header[2] == 3.toByte() && header[3] == 4.toByte()) ||
            (header[2] == 5.toByte() && header[3] == 6.toByte()) ||
            (header[2] == 7.toByte() && header[3] == 8.toByte()))

    private fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank() || name.startsWith('/') || name.startsWith('\\')) return false
        if (WINDOWS_DRIVE.matches(name)) return false
        if ('\\' in name) return false
        return name.split('/').none { it == ".." }
    }

    private fun isSupportedImageName(name: String): Boolean {
        if (name.startsWith("__MACOSX/")) return false
        val lower = name.lowercase()
        return lower.endsWith(".png") || lower.endsWith(".webp")
    }

    private val WINDOWS_DRIVE = Regex("^[A-Za-z]:.*")
    private const val MAX_ENTRIES = 64
    private const val MAX_ENTRY_BYTES = 24L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 32L * 1024L * 1024L
}
