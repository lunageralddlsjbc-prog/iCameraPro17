package com.lunageralddlsjbc.icamerapro17.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.sqrt

data class HdrFrameOffset(
    val file: File,
    val offsetX: Int,
    val offsetY: Int,
    val correlation: Double
)

data class HdrAlignmentResult(
    val referenceFile: File,
    val offsets: List<HdrFrameOffset>,
    val reliable: Boolean,
    val reason: String
)

object HdrFrameAligner {
    private const val MAX_THUMBNAIL_SIZE = 512
    private const val SEARCH_RADIUS = 10
    private const val MIN_CORRELATION = 0.45

    fun align(frames: List<HdrBracketFrame>): HdrAlignmentResult {
        require(frames.size == 3) {
            "HDR alignment requires exactly three bracket frames"
        }
        require(frames.all { it.file.exists() && it.file.length() > 0L }) {
            "One or more HDR frame files are missing or empty"
        }

        val reference = frames.minByOrNull { kotlin.math.abs(it.actualEv) }
            ?: throw IllegalArgumentException("No reference frame")

        val referenceThumb = decodeThumbnail(reference.file)
            ?: throw IllegalStateException("Could not decode reference HDR frame")

        val offsets = mutableListOf<HdrFrameOffset>()

        try {
            offsets.add(HdrFrameOffset(reference.file, 0, 0, 1.0))

            for (frame in frames) {
                if (frame.file == reference.file) continue

                val candidate = decodeThumbnail(frame.file)
                    ?: throw IllegalStateException("Could not decode HDR frame")

                try {
                    require(
                        candidate.bitmap.width == referenceThumb.bitmap.width &&
                            candidate.bitmap.height == referenceThumb.bitmap.height
                    ) {
                        "HDR frames have different dimensions"
                    }

                    val match = findBestShift(
                        referenceThumb.pixels,
                        candidate.pixels,
                        referenceThumb.bitmap.width,
                        referenceThumb.bitmap.height
                    )

                    offsets.add(
                        HdrFrameOffset(
                            file = frame.file,
                            offsetX = match.dx * referenceThumb.sampleSize,
                            offsetY = match.dy * referenceThumb.sampleSize,
                            correlation = match.score
                        )
                    )
                } finally {
                    candidate.bitmap.recycle()
                }
            }
        } finally {
            referenceThumb.bitmap.recycle()
        }

        val reliable = offsets.all {
            it.correlation >= MIN_CORRELATION
        }

        return HdrAlignmentResult(
            referenceFile = reference.file,
            offsets = offsets,
            reliable = reliable,
            reason = if (reliable) {
                "Frame alignment estimates passed the correlation threshold"
            } else {
                "Motion or scene changes made alignment uncertain; HDR merge should be skipped"
            }
        )
    }

    private data class Thumbnail(
        val bitmap: Bitmap,
        val pixels: IntArray,
        val sampleSize: Int
    )

    private data class Match(
        val dx: Int,
        val dy: Int,
        val score: Double
    )

    private fun decodeThumbnail(file: File): Thumbnail? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            maxOf(bounds.outWidth, bounds.outHeight) / sample >
            MAX_THUMBNAIL_SIZE
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: return null

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(
            pixels, 0, bitmap.width,
            0, 0, bitmap.width, bitmap.height
        )

        return Thumbnail(bitmap, pixels, sample)
    }

    private fun findBestShift(
        reference: IntArray,
        candidate: IntArray,
        width: Int,
        height: Int
    ): Match {
        var best = Match(0, 0, -1.0)

        for (dy in -SEARCH_RADIUS..SEARCH_RADIUS) {
            for (dx in -SEARCH_RADIUS..SEARCH_RADIUS) {
                val score = correlation(
                    reference, candidate, width, height, dx, dy
                )

                if (score > best.score) {
                    best = Match(dx, dy, score)
                }
            }
        }

        return best
    }

    private fun correlation(
        reference: IntArray,
        candidate: IntArray,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int
    ): Double {
        val margin = SEARCH_RADIUS + 2
        val startX = margin + maxOf(0, -dx)
        val endX = width - margin - maxOf(0, dx)
        val startY = margin + maxOf(0, -dy)
        val endY = height - margin - maxOf(0, dy)

        var count = 0
        var sumA = 0.0
        var sumB = 0.0
        var sumAA = 0.0
        var sumBB = 0.0
        var sumAB = 0.0

        for (y in startY until endY step 2) {
            for (x in startX until endX step 2) {
                val a = luminance(reference[y * width + x])
                val b = luminance(candidate[(y + dy) * width + x + dx])

                sumA += a
                sumB += b
                sumAA += a * a
                sumBB += b * b
                sumAB += a * b
                count++
            }
        }

        if (count < 100) return -1.0

        val covariance = sumAB - sumA * sumB / count
        val varianceA = sumAA - sumA * sumA / count
        val varianceB = sumBB - sumB * sumB / count

        if (varianceA <= 0.0 || varianceB <= 0.0) return -1.0

        return (covariance / sqrt(varianceA * varianceB))
            .coerceIn(-1.0, 1.0)
    }

    private fun luminance(color: Int): Double {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        return 0.299 * red + 0.587 * green + 0.114 * blue
    }
}
