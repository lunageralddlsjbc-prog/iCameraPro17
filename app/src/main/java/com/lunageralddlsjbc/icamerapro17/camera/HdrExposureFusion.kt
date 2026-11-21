package com.lunageralddlsjbc.icamerapro17.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

object HdrExposureFusion {
    private const val TILE_HEIGHT = 32
    private const val JPEG_QUALITY = 96

    /**
     * Creates a tone-mapped JPEG using exposure fusion.
     * The source bracket files are never modified.
     */
    fun merge(
        frames: List<HdrBracketFrame>,
        alignment: HdrAlignmentResult,
        outputFile: File
    ): File {
        require(frames.size == 3) { "Three HDR frames are required" }
        require(alignment.reliable) { "Unreliable alignment; HDR merge cancelled" }
        require(frames.all { it.file.exists() && it.file.length() > 0L }) {
            "An HDR source frame is missing"
        }

        val offsets = alignment.offsets.associateBy { it.file.absolutePath }
        require(frames.all { offsets.containsKey(it.file.absolutePath) }) {
            "Alignment data is missing for an HDR frame"
        }

        val decoders = mutableListOf<BitmapRegionDecoder>()
        var output: Bitmap? = null

        try {
            val dimensions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(
                alignment.referenceFile.absolutePath,
                dimensions
            )

            val width = dimensions.outWidth
            val height = dimensions.outHeight
            require(width > 0 && height > 0) { "Invalid HDR image dimensions" }

            val frameOffsets = frames.map {
                offsets.getValue(it.file.absolutePath)
            }

            val left = frameOffsets.maxOf { maxOf(0, -it.offsetX) }
            val top = frameOffsets.maxOf { maxOf(0, -it.offsetY) }
            val right = frameOffsets.minOf { minOf(width, width - it.offsetX) }
            val bottom = frameOffsets.minOf { minOf(height, height - it.offsetY) }

            val outputWidth = right - left
            val outputHeight = bottom - top
            require(outputWidth > 0 && outputHeight > 0) {
                "Not enough common image area after alignment"
            }

            val pixelCount = outputWidth.toLong() * outputHeight
            val outputBytes = pixelCount * 4L
            val tileBytes = outputWidth.toLong() * TILE_HEIGHT * 16L
            val requiredBytes = outputBytes + tileBytes
            val safeBudget = Runtime.getRuntime().maxMemory() * 35L / 100L

            require(requiredBytes <= safeBudget) {
                "HDR merge needs ${requiredBytes / 1048576}MB; safe memory budget is ${safeBudget / 1048576}MB"
            }

            for (frame in frames) {
                decoders.add(
                    BitmapRegionDecoder.newInstance(
                        frame.file.absolutePath,
                        false
                    ) ?: throw IllegalStateException("Could not open HDR frame")
                )
            }

            output = Bitmap.createBitmap(
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888
            )

            val exposureScales = frames.map {
                2.0.pow(-it.actualEv.toDouble())
            }

            var y = 0
            while (y < outputHeight) {
                val rows = minOf(TILE_HEIGHT, outputHeight - y)
                val tilePixels = ArrayList<IntArray>(3)

                for (i in frames.indices) {
                    val offset = frameOffsets[i]
                    val sourceRect = Rect(
                        left + offset.offsetX,
                        top + y + offset.offsetY,
                        right + offset.offsetX,
                        top + y + offset.offsetY + rows
                    )

                    val tile = decoders[i].decodeRegion(
                        sourceRect,
                        BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                    ) ?: throw IllegalStateException("Could not decode HDR image tile")

                    try {
                        require(
                            tile.width == outputWidth && tile.height == rows
                        ) { "HDR tile dimensions do not match" }

                        val pixels = IntArray(outputWidth * rows)
                        tile.getPixels(
                            pixels, 0, outputWidth,
                            0, 0, outputWidth, rows
                        )
                        tilePixels.add(pixels)
                    } finally {
                        tile.recycle()
                    }
                }

                val merged = IntArray(outputWidth * rows)

                for (p in merged.indices) {
                    var red = 0.0
                    var green = 0.0
                    var blue = 0.0
                    var totalWeight = 0.0

                    for (i in 0..2) {
                        val color = tilePixels[i][p]
                        val r = ((color shr 16) and 0xFF) / 255.0
                        val g = ((color shr 8) and 0xFF) / 255.0
                        val b = (color and 0xFF) / 255.0

                        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
                        val midtoneWeight = (1.0 - abs(luminance - 0.5) * 2.0)
                            .coerceAtLeast(0.01)

                        val scale = exposureScales[i]
                        val weight = midtoneWeight

                        red += (r * scale).coerceIn(0.0, 1.0) * weight
                        green += (g * scale).coerceIn(0.0, 1.0) * weight
                        blue += (b * scale).coerceIn(0.0, 1.0) * weight
                        totalWeight += weight
                    }

                    val r = (red / totalWeight * 255.0).roundToInt().coerceIn(0, 255)
                    val g = (green / totalWeight * 255.0).roundToInt().coerceIn(0, 255)
                    val b = (blue / totalWeight * 255.0).roundToInt().coerceIn(0, 255)

                    merged[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }

                output.setPixels(
                    merged, 0, outputWidth,
                    0, y, outputWidth, rows
                )
                y += rows
            }

            outputFile.parentFile?.let {
                require(it.exists() || it.mkdirs()) {
                    "Could not create HDR output directory"
                }
            }

            FileOutputStream(outputFile).use { stream ->
                check(output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    "Could not encode merged HDR JPEG"
                }
                stream.flush()
            }

            check(outputFile.exists() && outputFile.length() > 0L) {
                "HDR output file is empty"
            }

            return outputFile
        } catch (error: Exception) {
            outputFile.delete()
            throw error
        } finally {
            output?.recycle()
            decoders.forEach {
                try {
                    it.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }
}
