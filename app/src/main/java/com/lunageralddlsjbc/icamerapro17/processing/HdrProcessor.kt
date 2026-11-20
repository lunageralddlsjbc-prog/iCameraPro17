package com.lunageralddlsjbc.icamerapro17.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

object HdrProcessor {
    fun processHdrSequence(frames: List<File>, outputFile: File): File {
        if (frames.isEmpty()) return outputFile
        
        val baseBitmap = BitmapFactory.decodeFile(frames[0].absolutePath) ?: return outputFile
        val result = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        
        // iPhone-inspired tone mapping matrix
        val matrix = floatArrayOf(
            1.1f, 0.0f, 0.0f, 0.0f, 5f,
            0.0f, 1.05f, 0.0f, 0.0f, 2f,
            0.0f, 0.0f, 1.0f, 0.0f, 0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0f
        )
        
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(result, 0f, 0f, paint)
        
        FileOutputStream(outputFile).use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        
        baseBitmap.recycle()
        result.recycle()
        return outputFile
    }
    
    fun applyNightModeEnhancement(inputFile: File, outputFile: File): File {
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath) ?: return outputFile
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        FileOutputStream(outputFile).use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        bitmap.recycle()
        result.recycle()
        return outputFile
    }
}
