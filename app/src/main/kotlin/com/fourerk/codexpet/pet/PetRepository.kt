package com.fourerk.codexpet.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.graphics.drawable.toDrawable
import com.fourerk.codexpet.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class PetRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val provider = PetAssetProvider(context)
    private val mutex = Mutex()
    private val mutableVisual = MutableStateFlow<PetVisual?>(null)
    private val cacheDirectory = File(context.filesDir, "pet-cache")
    private val bitmapFile = File(cacheDirectory, "pet.png")
    private val manualSourceFile = File(cacheDirectory, "manual-source.img")
    private var visualFingerprint: PetFingerprint? = null
    private var pendingAutoChange: PendingAutoChange? = null

    val visual = mutableVisual.asStateFlow()

    fun loadCached() {
        scope.launch(Dispatchers.IO) {
            cacheDirectory.mkdirs()
            if (manualSourceFile.isFile) {
                val decoded = decodeBitmap(manualSourceFile)
                if (decoded != null && PetSpriteSheetParser.hasSupportedGeometry(decoded)) {
                    val sheet = PetSpriteSheetParser.parse(decoded).getOrNull()
                    decoded.recycle()
                    if (sheet != null) {
                        mutableVisual.value = sheetVisual(
                            sheet = sheet,
                            hash = sha256(manualSourceFile),
                            updatedAt = manualSourceFile.lastModified(),
                        )
                        visualFingerprint = PetVisualMatcher.fingerprint(sheet.preview)
                        return@launch
                    }
                }
                if (decoded?.isRecycled == false) decoded.recycle()
                val drawable = runCatching {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(manualSourceFile))
                }.getOrNull()
                val candidate = drawable?.let {
                    provider.inspectDrawable(it, PetSource.CACHE, enforceTransparency = false)
                }
                if (candidate != null) {
                    publishCandidate(candidate, PetSource.CACHE, manualSourceFile.lastModified())
                    return@launch
                }
            }

            val bitmap = BitmapFactory.decodeFile(bitmapFile.absolutePath) ?: return@launch
            val candidate = provider.inspectDrawable(
                filteredDrawable(bitmap),
                PetSource.CACHE,
                enforceTransparency = false,
            ) ?: return@launch
            publishCandidate(candidate, PetSource.CACHE, bitmapFile.lastModified())
        }
    }

    suspend fun acceptAutoCandidate(candidate: PetCandidate, sourcePackage: String): Boolean = mutex.withLock {
        if (!candidate.diagnostics.acceptedForOverlay) return false
        if (mutableVisual.value?.source == PetSource.SPRITE_SHEET_IMPORT) return false
        val info = candidate.diagnostics.bitmap ?: return false
        val currentSettings = settings.settings.value
        val currentVisual = mutableVisual.value
        val candidateFingerprint = PetVisualMatcher.fingerprint(candidate.bitmap)
        val currentFingerprint = visualFingerprint ?: currentVisual?.bitmap
            ?.let(PetVisualMatcher::fingerprint)
            ?.also { visualFingerprint = it }
        val samePet = currentVisual != null && (
            currentSettings.lastPetHash == info.sha256 ||
                (currentFingerprint != null &&
                    PetVisualMatcher.looksLikeSamePet(currentFingerprint, candidateFingerprint))
            )
        if (samePet) {
            pendingAutoChange = null
            if (currentSettings.lastPetSourcePackage != sourcePackage ||
                currentSettings.lastPetAssetSource != candidate.source.name
            ) {
                settings.updatePetMetadata(
                    currentVisual.hash,
                    currentVisual.updatedAt,
                    candidate.source.name,
                    sourcePackage,
                )
            }
            return false
        }
        if (currentVisual != null && !confirmDifferentPet(candidateFingerprint)) return false

        withContext(Dispatchers.IO) {
            cacheDirectory.mkdirs()
            val temporary = File(cacheDirectory, "pet.png.tmp")
            temporary.outputStream().use { candidate.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            moveIntoPlace(temporary, bitmapFile)
            manualSourceFile.delete()
        }
        val now = System.currentTimeMillis()
        settings.updatePetMetadata(info.sha256, now, candidate.source.name, sourcePackage)
        pendingAutoChange = null
        visualFingerprint = candidateFingerprint
        mutableVisual.value = PetVisual(
            drawable = candidate.drawable,
            bitmap = candidate.bitmap,
            hash = info.sha256,
            source = candidate.source,
            updatedAt = now,
            hasMeaningfulTransparency = true,
        )
        true
    }

    suspend fun importManual(uri: Uri): Result<PetVisual> = runCatching {
        mutex.withLock {
            val temporary = withContext(Dispatchers.IO) {
                cacheDirectory.mkdirs()
                File(cacheDirectory, "manual-source.tmp").also { target ->
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Не удалось открыть выбранный файл" }
                        target.outputStream().use(input::copyTo)
                    }
                }
            }
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(temporary) }
            if (bitmap != null && PetSpriteSheetParser.hasSupportedGeometry(bitmap)) {
                val sheet = try {
                    PetSpriteSheetParser.parse(bitmap).getOrThrow()
                } finally {
                    bitmap.recycle()
                }
                withContext(Dispatchers.IO) {
                    moveIntoPlace(temporary, manualSourceFile)
                    bitmapFile.delete()
                }
                val now = System.currentTimeMillis()
                val hash = withContext(Dispatchers.IO) { sha256(manualSourceFile) }
                settings.updatePetMetadata(hash, now, PetSource.SPRITE_SHEET_IMPORT.name, null)
                pendingAutoChange = null
                visualFingerprint = PetVisualMatcher.fingerprint(sheet.preview)
                sheetVisual(sheet, hash, now).also { mutableVisual.value = it }
            } else {
                bitmap?.recycle()
                withContext(Dispatchers.IO) { moveIntoPlace(temporary, manualSourceFile) }
                val drawable = withContext(Dispatchers.IO) {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(manualSourceFile))
                }
                val candidate = requireNotNull(
                    provider.inspectDrawable(drawable, PetSource.MANUAL_IMPORT, enforceTransparency = false),
                ) { "Выбранное изображение невозможно отрисовать" }
                val info = requireNotNull(candidate.diagnostics.bitmap)
                withContext(Dispatchers.IO) {
                    bitmapFile.outputStream().use { candidate.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                val now = System.currentTimeMillis()
                settings.updatePetMetadata(info.sha256, now, PetSource.MANUAL_IMPORT.name, null)
                pendingAutoChange = null
                visualFingerprint = PetVisualMatcher.fingerprint(candidate.bitmap)
                PetVisual(
                    drawable = candidate.drawable,
                    bitmap = candidate.bitmap,
                    hash = info.sha256,
                    source = PetSource.MANUAL_IMPORT,
                    updatedAt = now,
                    hasMeaningfulTransparency = info.transparentPixelPercent >= 2.0,
                ).also { mutableVisual.value = it }
            }
        }
    }

    private fun sheetVisual(sheet: ParsedPetSpriteSheet, hash: String, updatedAt: Long): PetVisual = PetVisual(
        drawable = filteredDrawable(sheet.preview),
        bitmap = sheet.preview,
        hash = hash,
        source = PetSource.SPRITE_SHEET_IMPORT,
        updatedAt = updatedAt,
        hasMeaningfulTransparency = true,
        frameSequences = sheet.sequences,
        lookDirections = sheet.lookDirections,
    )

    private fun publishCandidate(candidate: PetCandidate, source: PetSource, updatedAt: Long) {
        val info = candidate.diagnostics.bitmap ?: return
        visualFingerprint = PetVisualMatcher.fingerprint(candidate.bitmap)
        mutableVisual.value = PetVisual(
            drawable = candidate.drawable,
            bitmap = candidate.bitmap,
            hash = info.sha256,
            source = source,
            updatedAt = updatedAt,
            hasMeaningfulTransparency = info.transparentPixelPercent >= 2.0,
        )
    }

    private fun confirmDifferentPet(candidate: PetFingerprint): Boolean {
        val now = System.currentTimeMillis()
        val pending = pendingAutoChange
        if (pending == null || now - pending.firstSeenAt > PENDING_CHANGE_WINDOW_MS ||
            !PetVisualMatcher.looksLikeSamePet(pending.fingerprint, candidate)
        ) {
            pendingAutoChange = PendingAutoChange(candidate, now, 1)
            return false
        }
        val updated = pending.copy(matches = pending.matches + 1)
        pendingAutoChange = updated
        return updated.matches >= REQUIRED_DIFFERENT_FRAMES
    }

    private fun decodeBitmap(file: File): Bitmap? = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        },
    )

    private fun filteredDrawable(bitmap: Bitmap): BitmapDrawable = bitmap.toDrawable(context.resources).apply {
        setTargetDensity(context.resources.displayMetrics)
        isFilterBitmap = true
        paint.isDither = true
    }

    private fun moveIntoPlace(source: File, target: File) {
        if (target.isFile) target.delete()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class PendingAutoChange(
        val fingerprint: PetFingerprint,
        val firstSeenAt: Long,
        val matches: Int,
    )

    private companion object {
        const val REQUIRED_DIFFERENT_FRAMES = 3
        const val PENDING_CHANGE_WINDOW_MS = 5_000L
    }
}
