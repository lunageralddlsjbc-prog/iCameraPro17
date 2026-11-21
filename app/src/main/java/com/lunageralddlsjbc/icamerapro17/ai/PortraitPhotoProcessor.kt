package com.lunageralddlsjbc.icamerapro17.ai

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.renderscript.*
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.android.gms.tasks.Tasks
import kotlin.math.max

@Suppress("DEPRECATION")
object PortraitPhotoProcessor {
    fun apply(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        val source = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return false

        var small: Bitmap? = null
        var blurredSmall: Bitmap? = null
        var blurred: Bitmap? = null
        var sharpLayer: Bitmap? = null
        var mask: Bitmap? = null
        var rs: RenderScript? = null

        try {
            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .build()
            )
            val faces = try {
                Tasks.await(detector.process(InputImage.fromBitmap(source, 0)))
            } finally {
                detector.close()
            }

            if (faces.isEmpty()) {
                Log.i("Portrait", "No face detected; portrait effect skipped")
                return false
            }

            val width = source.width
            val height = source.height
            val scale = 4
            val sw = max(1, width / scale)
            val sh = max(1, height / scale)

            small = Bitmap.createScaledBitmap(source, sw, sh, true)
            blurredSmall = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)

            rs = RenderScript.create(context)
            val input = Allocation.createFromBitmap(rs, small)
            val output = Allocation.createFromBitmap(rs, blurredSmall)
            val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blur.setRadius(18f)
            blur.setInput(input)
            blur.forEach(output)
            output.copyTo(blurredSmall)
            blur.destroy()
            input.destroy()
            output.destroy()

            blurred = Bitmap.createScaledBitmap(blurredSmall, width, height, true)
            sharpLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)

            val maskCanvas = Canvas(mask)
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                maskFilter = BlurMaskFilter(max(12f, width * 0.012f), BlurMaskFilter.Blur.NORMAL)
            }

            faces.forEach { face ->
                val bounds = RectF(face.boundingBox)
                bounds.inset(-bounds.width() * 0.22f, -bounds.height() * 0.18f)
                maskCanvas.drawOval(bounds, maskPaint)
            }

            val sharpCanvas = Canvas(sharpLayer)
            sharpCanvas.drawBitmap(source, 0f, 0f, null)
            val maskPaintLayer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            sharpCanvas.drawBitmap(mask, 0f, 0f, maskPaintLayer)
            maskPaintLayer.xfermode = null

            val result = blurred
            Canvas(result).drawBitmap(sharpLayer, 0f, 0f, null)

            resolver.openOutputStream(uri, "wt")?.use {
                if (!result.compress(Bitmap.CompressFormat.JPEG, 95, it)) {
                    throw IllegalStateException("Portrait JPEG save failed")
                }
            } ?: throw IllegalStateException("Cannot open portrait output")

            Log.i("Portrait", "Portrait effect applied to ${faces.size} detected face(s)")
            return true
        } catch (e: Exception) {
            Log.e("Portrait", "Portrait processing failed", e)
            return false
        } finally {
            rs?.destroy()
            small?.recycle()
            blurredSmall?.recycle()
            blurred?.recycle()
            sharpLayer?.recycle()
            mask?.recycle()
            source.recycle()
        }
    }
}
