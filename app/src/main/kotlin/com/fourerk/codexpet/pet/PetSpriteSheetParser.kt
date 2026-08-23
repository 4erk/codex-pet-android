package com.fourerk.codexpet.pet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.core.graphics.createBitmap

data class ParsedPetSpriteSheet(
    val version: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val sequences: Map<PetAnimationState, PetFrameSequence>,
    val lookDirections: List<Bitmap>,
) {
    val preview: Bitmap get() = requireNotNull(sequences[PetAnimationState.IDLE]).frames.first()
}

/** Parser for the documented ChatGPT v1/v2 pet atlas, including lossless 2x packs. */
object PetSpriteSheetParser {
    fun hasSupportedGeometry(bitmap: Bitmap): Boolean = geometry(bitmap) != null

    fun parse(bitmap: Bitmap): Result<ParsedPetSpriteSheet> = runCatching {
        val geometry = requireNotNull(geometry(bitmap)) {
            "Ожидается pet pack 8×9 или 8×11 с ячейкой 192×208 (также поддерживается 2×)"
        }
        require(bitmap.hasAlpha()) { "Sprite sheet не содержит alpha-канал" }

        val sequences = linkedMapOf<PetAnimationState, PetFrameSequence>()
        ROWS.forEachIndexed { rowIndex, row ->
            val frames = (0 until row.frameCount).map { column ->
                copyCell(bitmap, column, rowIndex, geometry.cellWidth, geometry.cellHeight).also { frame ->
                    require(hasVisiblePixel(frame)) { "Пустой обязательный кадр: строка $rowIndex, кадр $column" }
                }
            }
            for (column in row.frameCount until COLUMN_COUNT) {
                require(isCellTransparent(bitmap, column, rowIndex, geometry.cellWidth, geometry.cellHeight)) {
                    "Лишнее изображение в неиспользуемой ячейке: строка $rowIndex, кадр $column"
                }
            }
            sequences[row.state] = PetFrameSequence(frames, row.durationsMs)
        }

        val lookDirections = if (geometry.version == 2) {
            (V1_ROWS until V2_ROWS).flatMap { row ->
                (0 until COLUMN_COUNT).map { column ->
                    copyCell(bitmap, column, row, geometry.cellWidth, geometry.cellHeight).also { frame ->
                        require(hasVisiblePixel(frame)) { "Пустой look-кадр: строка $row, кадр $column" }
                    }
                }
            }
        } else {
            emptyList()
        }

        ParsedPetSpriteSheet(
            version = geometry.version,
            cellWidth = geometry.cellWidth,
            cellHeight = geometry.cellHeight,
            sequences = sequences,
            lookDirections = lookDirections,
        )
    }

    private fun geometry(bitmap: Bitmap): Geometry? {
        if (bitmap.width % COLUMN_COUNT != 0) return null
        val cellWidth = bitmap.width / COLUMN_COUNT
        val scale = cellWidth / BASE_CELL_WIDTH
        if (scale !in 1..MAX_SCALE || cellWidth != BASE_CELL_WIDTH * scale) return null
        val cellHeight = BASE_CELL_HEIGHT * scale
        val version = when (bitmap.height) {
            cellHeight * V1_ROWS -> 1
            cellHeight * V2_ROWS -> 2
            else -> return null
        }
        return Geometry(version, cellWidth, cellHeight)
    }

    private fun copyCell(source: Bitmap, column: Int, row: Int, width: Int, height: Int): Bitmap {
        val output = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.TRANSPARENT)
        Canvas(output).drawBitmap(
            source,
            Rect(column * width, row * height, (column + 1) * width, (row + 1) * height),
            Rect(0, 0, width, height),
            null,
        )
        return output
    }

    private fun hasVisiblePixel(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { Color.alpha(it) > MIN_VISIBLE_ALPHA }
    }

    private fun isCellTransparent(source: Bitmap, column: Int, row: Int, width: Int, height: Int): Boolean {
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, column * width, row * height, width, height)
        return pixels.all { Color.alpha(it) <= MIN_UNUSED_ALPHA }
    }

    private data class Geometry(
        val version: Int,
        val cellWidth: Int,
        val cellHeight: Int,
    )

    private data class RowDefinition(
        val state: PetAnimationState,
        val frameCount: Int,
        val durationsMs: List<Int>,
    )

    private val ROWS = listOf(
        RowDefinition(PetAnimationState.IDLE, 6, listOf(280, 110, 110, 140, 140, 320)),
        RowDefinition(PetAnimationState.RUNNING_RIGHT, 8, sevenThen(120, 220)),
        RowDefinition(PetAnimationState.RUNNING_LEFT, 8, sevenThen(120, 220)),
        RowDefinition(PetAnimationState.WAVING, 4, listOf(140, 140, 140, 280)),
        RowDefinition(PetAnimationState.JUMPING, 5, listOf(140, 140, 140, 140, 280)),
        RowDefinition(PetAnimationState.FAILED, 8, sevenThen(140, 240)),
        RowDefinition(PetAnimationState.WAITING, 6, fiveThen(150, 260)),
        RowDefinition(PetAnimationState.RUNNING, 6, fiveThen(120, 220)),
        RowDefinition(PetAnimationState.REVIEW, 6, fiveThen(150, 280)),
    )

    private fun sevenThen(value: Int, last: Int): List<Int> = List(7) { value } + last
    private fun fiveThen(value: Int, last: Int): List<Int> = List(5) { value } + last

    private const val COLUMN_COUNT = 8
    private const val V1_ROWS = 9
    private const val V2_ROWS = 11
    private const val BASE_CELL_WIDTH = 192
    private const val BASE_CELL_HEIGHT = 208
    private const val MAX_SCALE = 2
    private const val MIN_VISIBLE_ALPHA = 8
    private const val MIN_UNUSED_ALPHA = 0
}
