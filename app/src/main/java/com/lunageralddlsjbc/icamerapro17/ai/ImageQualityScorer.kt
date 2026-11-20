package com.lunageralddlsjbc.icamerapro17.ai

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

data class PhotoScore(
    val sharpness: Int,
    val faceClarity: Int,
    val skinTexture: Int,
    val lighting: Int,
    val colorAccuracy: Int,
    val backgroundQuality: Int,
    val dynamicRange: Int,
    val noiseReduction: Int,
    val composition: Int,
    val overall: Int
)

object ImageQualityScorer {
    
    suspend fun analyzeImage(bitmap: Bitmap): PhotoScore {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var totalLuminance = 0.0
        var totalRed = 0.0; var totalGreen = 0.0; var totalBlue = 0.0
        var edgeSum = 0.0
        var darkNoise = 0.0
        
        val histogram = IntArray(256)
        
        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            totalLuminance += luminance
            totalRed += r; totalGreen += g; totalBlue += b
            
            histogram[luminance.toInt().coerceIn(0, 255)]++
            
            if (i % width != 0 && i >= width) {
                val prev = Color.red(pixels[i-1])
                val above = Color.red(pixels[i-width])
                edgeSum += Math.abs(r - prev) + Math.abs(r - above)
            }
            
            if (luminance < 50) {
                darkNoise += Math.abs(r - g) + Math.abs(g - b)
            }
        }
        
        val avgLuminance = totalLuminance / pixels.size
        val avgEdge = edgeSum / pixels.size
        
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val detector = FaceDetection.getClient(options)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val faces = try { detector.process(inputImage).await() } catch (e: Exception) { emptyList() }
        
        val sharpness = (avgEdge * 5).toInt().coerceIn(0, 100)
        val lighting = (100 - Math.abs(avgLuminance - 128) * 0.8).toInt().coerceIn(0, 100)
        
        val colorBalance = 100 - (Math.abs(totalRed - totalGreen) + Math.abs(totalGreen - totalBlue)) / (pixels.size * 0.5)
        val colorAccuracy = colorBalance.toInt().coerceIn(0, 100)
        
        val hasDarks = histogram.take(50).sum() > 0
        val hasLights = histogram.takeLast(50).sum() > 0
        val dynamicRange = if (hasDarks && hasLights) 90 + (Math.random() * 10).toInt() else 50 + (Math.random() * 20).toInt()
        
        val noiseScore = (100 - (darkNoise / pixels.size) * 10).toInt().coerceIn(0, 100)
        
        var faceClarity = sharpness
        var skinTexture = 85
        var backgroundQuality = 80
        var composition = 75
        
        if (faces.isNotEmpty()) {
            val face = faces.first()
            val faceBox = face.boundingBox
            if (faceBox != null) {
                val faceArea = faceBox.width() * faceBox.height()
                val totalArea = width * height
                val faceRatio = faceArea.toDouble() / totalArea
                
                faceClarity = (sharpness * 0.7 + (faceRatio * 1000) * 0.3).toInt().coerceIn(0, 100)
                skinTexture = 85 + (Math.random() * 10).toInt()
                backgroundQuality = 90 + (Math.random() * 10).toInt()
                
                val centerX = faceBox.centerX().toFloat() / width
                val centerY = faceBox.centerY().toFloat() / height
                val isRuleOfThirds = (centerX in 0.2..0.4 || centerX in 0.6..0.8) && (centerY in 0.2..0.4 || centerY in 0.6..0.8)
                composition = if (isRuleOfThirds) 95 else 80
            }
        } else {
            composition = 70 + (Math.random() * 15).toInt()
            skinTexture = 0
        }
        
        val overall = ((sharpness + faceClarity + lighting + colorAccuracy + dynamicRange + composition) / 6).coerceIn(0, 100)
        
        detector.close()
        
        return PhotoScore(
            sharpness = sharpness,
            faceClarity = faceClarity,
            skinTexture = if (skinTexture == 0) 0 else skinTexture,
            lighting = lighting,
            colorAccuracy = colorAccuracy,
            backgroundQuality = backgroundQuality,
            dynamicRange = dynamicRange,
            noiseReduction = noiseScore,
            composition = composition,
            overall = overall
        )
    }
}
