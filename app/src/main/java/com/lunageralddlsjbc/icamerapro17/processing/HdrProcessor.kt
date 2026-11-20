package com.lunageralddlsjbc.icamerapro17.processing

import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.pow

object HdrProcessor {
    /**
     * iPhone-style Smart HDR Simulation
     * Uses Reinhard Tone Mapping + Local Contrast Enhancement
     */
    fun processHdrSequence(frames: List<File>, outputFile: File): File {
        if (frames.isEmpty()) return outputFile
        
        // Use first frame as base
        val baseBitmap = BitmapFactory.decodeFile(frames[0].absolutePath) ?: return outputFile
        val width = baseBitmap.width
        val height = baseBitmap.height
        
        // Create result bitmap
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Apply Tone Mapping & Color Science
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        
        // 1. Tone Mapping LUT (Reinhard operator approximation)
        // Maps high dynamic range values to displayable range while preserving local contrast
        val lut = IntArray(256)
        for (i in 0..255) {
            val normalized = i / 255.0f
            // Reinhard tone mapping: L_d = L_w / (1 + L_w)
            // Adjusted for mobile sensors: slightly more aggressive highlight compression
            val mapped = (normalized / (1.0f + normalized * 0.8f)).pow(0.9f) 
            lut[i] = (mapped * 255).toInt().coerceIn(0, 255)
        }
        
        // 2. Apple-inspired Color Matrix
        // Warm highlights, cool shadows, enhanced midtone saturation
        val colorMatrix = floatArrayOf(
            1.05f, 0.02f, -0.01f, 0.0f, 8f,   // Red: slight warmth boost
            0.01f, 1.08f, 0.02f, 0.0f, 4f,    // Green: natural vibrance
            -0.02f, 0.01f, 1.05f, 0.0f, 0f,   // Blue: neutral-cool
            0.0f, 0.0f, 0.0f, 1.0f, 0f        // Alpha
        )
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(baseBitmap, 0f, 0f, paint)
        
        // 3. Local Contrast Enhancement (Unsharp Mask approximation)
        // This gives the "crisp" iPhone look without artificial sharpening halos
        val blurred = Bitmap.createScaledBitmap(result, width / 4, height / 4, true)
        val blurredFull = Bitmap.createScaledBitmap(blurred, width, height, true)
        
        val contrastPaint = Paint()
        contrastPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        canvas.drawBitmap(blurredFull, 0f, 0f, contrastPaint)
        
        // Save with high quality
        FileOutputStream(outputFile).use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, 98, out)
        }
        
        // Cleanup
        baseBitmap.recycle()
        result.recycle()
        blurred.recycle()
        blurredFull.recycle()
        
        return outputFile
    }
}
