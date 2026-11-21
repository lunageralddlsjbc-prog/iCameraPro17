package com.lunageralddlsjbc.icamerapro17.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Conservative on-device computational photography.
 * It enhances the captured pixels; it does not generate or replace faces.
 */
object AutoPhotoEnhancer {

    fun enhance(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalStateException("Unable to decode captured image")

        var output: Bitmap? = null
        try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
            val detector = FaceDetection.getClient(options)

            val faces = try {
                Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
            } finally {
                detector.close()
            }

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // Estimate exposure from a luminance histogram.
            val histogram = IntArray(256)
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var sampleCount = 0

            val sampleStep = max(1, pixels.size / 100000)
            for (i in pixels.indices step sampleStep) {
                val c = pixels[i]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val y = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                histogram[y]++
                sumR += r
                sumG += g
                sumB += b
                sampleCount++
            }

            fun percentile(target: Int): Int {
                val wanted = (sampleCount * target / 100).coerceAtLeast(1)
                var total = 0
                for (i in histogram.indices) {
                    total += histogram[i]
                    if (total >= wanted) return i
                }
                return 128
            }

            val median = percentile(50).coerceAtLeast(1)
            val low = percentile(5)
            val high = percentile(95)
            val exposureGain = (118f / median).coerceIn(0.88f, 1.16f)

            // Very restrained gray-world white-balance correction.
            val avgR = sumR.toFloat() / sampleCount
            val avgG = sumG.toFloat() / sampleCount
            val avgB = sumB.toFloat() / sampleCount
            val neutral = (avgR + avgG + avgB) / 3f
            val gainR = (neutral / avgR.coerceAtLeast(1f)).coerceIn(0.94f, 1.06f)
            val gainG = (neutral / avgG.coerceAtLeast(1f)).coerceIn(0.97f, 1.03f)
            val gainB = (neutral / avgB.coerceAtLeast(1f)).coerceIn(0.94f, 1.06f)

            // Scene profile heuristics: faces, low light, and high contrast.
            val isNightOrLowLight = median < 75
            val isHighContrast = high - low > 155
            val faceBoxes = faces.map { face ->
                android.graphics.Rect(face.boundingBox).apply {
                    inset(-width / 30, -height / 30)
                    intersect(0, 0, width, height)
                }
            }

            // Work from the original pixel array to avoid cumulative filtering.
            val result = IntArray(pixels.size)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    val color = pixels[index]

                    var r = Color.red(color).toFloat()
                    var g = Color.green(color).toFloat()
                    var b = Color.blue(color).toFloat()

                    val originalY = 0.299f * r + 0.587f * g + 0.114f * b
                    val normalized = (originalY / 255f).coerceIn(0f, 1f)

                    // Lift dark regions gently; compress the brightest tones.
                    var targetY = originalY * exposureGain
                    val shadowLift = (1f - normalized).pow(2) *
                        if (isNightOrLowLight) 17f else 9f
                    targetY += shadowLift

                    if (normalized > 0.70f) {
                        val highlight = ((normalized - 0.70f) / 0.30f).coerceIn(0f, 1f)
                        targetY -= highlight * highlight * if (isHighContrast) 15f else 7f
                    }

                    // Mild local face illumination; no facial reshaping or replacement.
                    val insideFace = faceBoxes.any { it.contains(x, y) }
                    if (insideFace && originalY < 190f) {
                        targetY += (190f - originalY).coerceIn(0f, 70f) * 0.055f
                    }

                    val scale = (targetY / originalY.coerceAtLeast(1f)).coerceIn(0.78f, 1.28f)
                    r *= scale * gainR
                    g *= scale * gainG
                    b *= scale * gainB

                    // Small saturation increase, reduced in low light.
                    val saturation = if (isNightOrLowLight) 1.015f else 1.035f
                    val gray = 0.299f * r + 0.587f * g + 0.114f * b
                    r = gray + (r - gray) * saturation
                    g = gray + (g - gray) * saturation
                    b = gray + (b - gray) * saturation

                    // Gentle luminance detail enhancement using neighboring pixels.
                    if (x in 1 until width - 1 && y in 1 until height - 1) {
                        val left = pixels[index - 1]
                        val right = pixels[index + 1]
                        val up = pixels[index - width]
                        val down = pixels[index + width]

                        val neighborY = (
                            0.299f * (Color.red(left) + Color.red(right) + Color.red(up) + Color.red(down)) +
                            0.587f * (Color.green(left) + Color.green(right) + Color.green(up) + Color.green(down)) +
                            0.114f * (Color.blue(left) + Color.blue(right) + Color.blue(up) + Color.blue(down))
                        ) / 4f

                        val detail = (originalY - neighborY).coerceIn(-24f, 24f)
                        val sharpen = if (isNightOrLowLight) 0.10f else 0.16f
                        r += detail * sharpen
                        g += detail * sharpen
                        b += detail * sharpen
                    }

                    result[index] = Color.rgb(
                        r.toInt().coerceIn(0, 255),
                        g.toInt().coerceIn(0, 255),
                        b.toInt().coerceIn(0, 255)
                    )
                }
            }

            val enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            output = enhanced
            enhanced.setPixels(result, 0, width, 0, 0, width, height)

            resolver.openOutputStream(uri, "wt")?.use { stream ->
                if (!enhanced.compress(Bitmap.CompressFormat.JPEG, 96, stream)) {
                    throw IllegalStateException("Could not save enhanced image")
                }
            } ?: throw IllegalStateException("Could not open image for enhancement")

            Log.i(
                "AutoPhotoEnhancer",
                "Enhanced ${width}x${height}; faces=${faces.size}; median=$median"
            )
        } finally {
            output?.recycle()
            bitmap.recycle()
        }
    }
}
