package com.lunageralddlsjbc.icamerapro17.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HdrCapturePipeline {

    suspend fun captureMergedFile(
        context: Context,
        camera: Camera,
        imageCapture: ImageCapture,
        callbackExecutor: Executor
    ): File = withContext(Dispatchers.IO) {
        var frames: List<HdrBracketFrame> = emptyList()
        val outputFile = File(
            context.cacheDir,
            "HDR_MERGED_${System.currentTimeMillis()}_${System.nanoTime()}.jpg"
        )

        try {
            frames = HdrBracketCapture.captureThreeFrames(
                context = context,
                camera = camera,
                imageCapture = imageCapture,
                callbackExecutor = callbackExecutor
            )

            check(frames.size == 3) {
                "HDR capture did not produce three frames"
            }

            val alignment = HdrFrameAligner.align(frames)

            check(alignment.reliable) {
                "HDR merge skipped: ${alignment.reason}"
            }

            HdrExposureFusion.merge(
                frames = frames,
                alignment = alignment,
                outputFile = outputFile
            )

            check(outputFile.exists() && outputFile.length() > 0L) {
                "HDR output was not created"
            }

            outputFile
        } catch (error: Exception) {
            outputFile.delete()
            throw error
        } finally {
            frames.forEach { frame ->
                try {
                    frame.file.delete()
                } catch (_: Exception) {
                }
            }
        }
    }
}
