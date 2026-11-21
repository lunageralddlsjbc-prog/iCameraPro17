package com.lunageralddlsjbc.icamerapro17.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

data class HdrBracketFrame(
    val file: File,
    val compensationIndex: Int,
    val actualEv: Float
)

object HdrBracketCapture {

    private val directExecutor = Executor { it.run() }

    suspend fun captureThreeFrames(
        context: Context,
        camera: Camera,
        imageCapture: ImageCapture,
        callbackExecutor: Executor
    ): List<HdrBracketFrame> {
        val state = camera.cameraInfo.exposureState

        check(state.isExposureCompensationSupported) {
            "Exposure compensation is not supported"
        }

        val range = state.exposureCompensationRange
        val stepEv: Float = state.exposureCompensationStep.toFloat()
        val baseIndex = state.exposureCompensationIndex

        check(stepEv > 0f && stepEv.isFinite()) {
            "Invalid camera exposure step"
        }

        val requestedEv = listOf(-1.5f, 0f, 1.5f)
        val frames = requestedEv.map { ev ->
            val offset: Int = (ev / stepEv).roundToInt()
            val index = (baseIndex + offset).coerceIn(range.lower, range.upper)

            HdrBracketFrame(
                file = File(context.cacheDir, "unused"),
                compensationIndex = index,
                actualEv = (index - baseIndex) * stepEv
            )
        }

        check(frames.map { it.compensationIndex }.distinct().size == 3) {
            "Camera exposure range is too narrow for three distinct HDR frames"
        }

        val directory = File(context.cacheDir, "hdr_brackets")
        check(directory.exists() || directory.mkdirs()) {
            "Could not create private HDR working directory"
        }

        val captured = mutableListOf<HdrBracketFrame>()

        try {
            for ((position, frame) in frames.withIndex()) {
                awaitExposure(
                    camera.cameraControl.setExposureCompensationIndex(
                        frame.compensationIndex
                    )
                )

                delay(180)

                val outputFile = File(
                    directory,
                    "HDR_${System.nanoTime()}_$position.jpg"
                )

                captureFrame(imageCapture, outputFile, callbackExecutor)
                captured.add(frame.copy(file = outputFile))
            }

            return captured
        } catch (error: Exception) {
            captured.forEach { it.file.delete() }
            throw error
        } finally {
            try {
                awaitExposure(
                    camera.cameraControl.setExposureCompensationIndex(baseIndex)
                )
            } catch (restoreError: Exception) {
                Log.e(
                    "HdrBracketCapture",
                    "Could not restore original exposure compensation",
                    restoreError
                )
            }
        }
    }

    private suspend fun awaitExposure(future: ListenableFuture<Int>) =
        suspendCancellableCoroutine<Unit> { continuation ->
            future.addListener({
                try {
                    future.get()
                    if (continuation.isActive) continuation.resume(Unit)
                } catch (error: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
            }, directExecutor)
        }

    private suspend fun captureFrame(
        imageCapture: ImageCapture,
        file: File,
        executor: Executor
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(
                    output: ImageCapture.OutputFileResults
                ) {
                    if (file.exists() && file.length() > 0L) {
                        if (continuation.isActive) continuation.resume(Unit)
                    } else if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("HDR frame file is empty")
                        )
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
            }
        )
    }
}
