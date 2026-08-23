package com.fourerk.codexpet.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
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

    val visual = mutableVisual.asStateFlow()

    fun loadCached() {
        scope.launch(Dispatchers.IO) {
            cacheDirectory.mkdirs()
            val drawable = when {
                manualSourceFile.isFile -> runCatching {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(manualSourceFile))
                }.getOrNull()
                bitmapFile.isFile -> BitmapFactory.decodeFile(bitmapFile.absolutePath)
                    ?.toDrawable(context.resources)
                else -> null
            } ?: return@launch
            val candidate = provider.inspectDrawable(drawable, PetSource.CACHE, enforceTransparency = false)
                ?: return@launch
            val bitmapInfo = candidate.diagnostics.bitmap ?: return@launch
            mutableVisual.value = PetVisual(
                drawable = candidate.drawable,
                bitmap = candidate.bitmap,
                hash = bitmapInfo.sha256,
                source = PetSource.CACHE,
                updatedAt = bitmapFile.lastModified().coerceAtLeast(manualSourceFile.lastModified()),
                hasMeaningfulTransparency = bitmapInfo.transparentPixelPercent >= 2.0,
            )
        }
    }

    suspend fun acceptAutoCandidate(candidate: PetCandidate, sourcePackage: String): Boolean = mutex.withLock {
        if (!candidate.diagnostics.acceptedForOverlay) return false
        val info = candidate.diagnostics.bitmap ?: return false
        val currentSettings = settings.settings.value
        if (currentSettings.lastPetHash == info.sha256 && mutableVisual.value != null) {
            if (currentSettings.lastPetSourcePackage != sourcePackage ||
                currentSettings.lastPetAssetSource != candidate.source.name
            ) {
                settings.updatePetMetadata(
                    info.sha256,
                    currentSettings.lastPetUpdatedAt ?: System.currentTimeMillis(),
                    candidate.source.name,
                    sourcePackage,
                )
            }
            return false
        }
        withContext(Dispatchers.IO) {
            cacheDirectory.mkdirs()
            val temp = File(cacheDirectory, "pet.png.tmp")
            temp.outputStream().use { candidate.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!temp.renameTo(bitmapFile)) {
                temp.copyTo(bitmapFile, overwrite = true)
                temp.delete()
            }
            manualSourceFile.delete()
        }
        val now = System.currentTimeMillis()
        settings.updatePetMetadata(info.sha256, now, candidate.source.name, sourcePackage)
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
            val drawable = withContext(Dispatchers.IO) {
                cacheDirectory.mkdirs()
                val temp = File(cacheDirectory, "manual-source.tmp")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Cannot open selected image" }
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!temp.renameTo(manualSourceFile)) {
                    temp.copyTo(manualSourceFile, overwrite = true)
                    temp.delete()
                }
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(manualSourceFile))
            }
            val candidate = requireNotNull(
                provider.inspectDrawable(drawable, PetSource.MANUAL_IMPORT, enforceTransparency = false),
            ) { "Selected image cannot be rendered" }
            val info = requireNotNull(candidate.diagnostics.bitmap)
            withContext(Dispatchers.IO) {
                bitmapFile.outputStream().use { candidate.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            val now = System.currentTimeMillis()
            settings.updatePetMetadata(info.sha256, now, PetSource.MANUAL_IMPORT.name, null)
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
