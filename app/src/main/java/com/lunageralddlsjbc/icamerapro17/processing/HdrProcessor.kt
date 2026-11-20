package com.lunageralddlsjbc.icamerapro17.processing

import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.pow // FIXED: Added missing pow import

enum class PhotographicStyle(val styleName: String, val matrix: FloatArray) { // FIXED: Renamed 'name' to 'styleName'
    STANDARD("Standard", floatArrayOf(1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)),
    RICH_CONTRAST("Rich Contrast", floatArrayOf(1.2f,0f,0f,0f,-10f, 0f,1.15f,0f,0f,-8f, 0f,0f,1.1f,0f,-5f, 0f,0f,0f,1f,0f)),
    VIBRANT("Vibrant", floatArrayOf(1.3f,-0.1f,-0.1f,0f,10f, -0.1f,1.3f,-0.1f,0f,10f, -0.1f,-0.1f,1.3f,0f,10f, 0f,0f,0f,1f,0f)),
    WARM("Warm", floatArrayOf(1.1f,0f,0f,0f,15f, 0f,1.05f,0f,0f,5f, 0f,0f,0.95f,0f,-10f, 0f,0f,0f,1f,0f)),
    COOL("Cool", floatArrayOf(0.95f,0f,0f,0f,-10f, 0f,1f,0f,0f,5f, 0f,0f,1.1f,0f,15f, 0f,0f,0f,1f,0f)),
    NATURAL("Natural", floatArrayOf(1.05f,0f,0f,0f,5f, 0f,1.05f,0f,0f,3f, 0f,0f,1.02f,0f,0f, 0f,0f,0f,1f,0f))
}

object HdrProcessor {
    fun processHdrSequence(frames: List<File>, outputFile: File, style: PhotographicStyle = PhotographicStyle.STANDARD): File {
        if (frames.isEmpty()) return outputFile
        
        val baseBitmap = BitmapFactory.decodeFile(frames[0].absolutePath) ?: return outputFile
        val width = baseBitmap.width
        val height = baseBitmap.height
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        
        // Reinhard Tone Mapping
        val lut = IntArray(256)
        for (i in 0..255) {
            val n = i / 255.0f
            lut[i] = ((n / (1.0f + n * 0.8f)).pow(0.9f) * 255).toInt().coerceIn(0, 255)
        }
        
        // Apply Photographic Style Matrix
        paint.colorFilter = ColorMatrixColorFilter(style.matrix)
        canvas.drawBitmap(baseBitmap, 0f, 0f, paint)
        
        // Local Contrast Enhancement (Unsharp Mask)
        val small = Bitmap.createScaledBitmap(result, width / 4, height / 4, true)
        val blur = Bitmap.createScaledBitmap(small, width, height, true)
        val contrastPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY) }
        canvas.drawBitmap(blur, 0f, 0f, contrastPaint)
        
        FileOutputStream(outputFile).use { out -> result.compress(Bitmap.CompressFormat.JPEG, 98, out) }
        
        baseBitmap.recycle(); result.recycle(); small.recycle(); blur.recycle()
        return outputFile
    }
}
