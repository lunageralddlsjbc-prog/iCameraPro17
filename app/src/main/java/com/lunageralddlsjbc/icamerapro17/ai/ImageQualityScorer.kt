package com.lunageralddlsjbc.icamerapro17.ai

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

data class PhotoScore(val sharpness: Int, val faceClarity: Int, val lighting: Int, val colorAccuracy: Int, val overall: Int)

object ImageQualityScorer {
    suspend fun analyzeImage(bitmap: Bitmap): PhotoScore {
        val width = bitmap.width; val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var totalLuminance = 0.0; var totalRed = 0.0; var totalGreen = 0.0; var totalBlue = 0.0
        var edgeSum = 0.0
        
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]); val g = Color.green(pixels[i]); val b = Color.blue(pixels[i])
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            totalLuminance += luminance; totalRed += r; totalGreen += g; totalBlue += b
            if (i % width != 0 && i >= width) {
                edgeSum += Math.abs(r - Color.red(pixels[i-1])) + Math.abs(r - Color.red(pixels[i-width]))
            }
        }
        
        val avgLuminance = totalLuminance / pixels.size
        val avgEdge = edgeSum / pixels.size
        
        val options = FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build()
        val detector = FaceDetection.getClient(options)
        val faces = try { detector.process(InputImage.fromBitmap(bitmap, 0)).await() } catch (e: Exception) { emptyList() }
        
        val sharpness = (avgEdge * 5).toInt().coerceIn(0, 100)
        val lighting = (100 - Math.abs(avgLuminance - 128) * 0.8).toInt().coerceIn(0, 100)
        val colorBalance = 100 - (Math.abs(totalRed - totalGreen) + Math.abs(totalGreen - totalBlue)) / (pixels.size * 0.5)
        val colorAccuracy = colorBalance.toInt().coerceIn(0, 100)
        
        var faceClarity = sharpness
        if (faces.isNotEmpty()) {
            val box = faces.first().boundingBox
            if (box != null) {
                val faceRatio = (box.width() * box.height()).toDouble() / (width * height)
                faceClarity = (sharpness * 0.7 + (faceRatio * 1000) * 0.3).toInt().coerceIn(0, 100)
            }
        }
        
        val overall = ((sharpness + faceClarity + lighting + colorAccuracy) / 4).coerceIn(0, 100)
        detector.close()
        return PhotoScore(sharpness, faceClarity, lighting, colorAccuracy, overall)
    }
}
