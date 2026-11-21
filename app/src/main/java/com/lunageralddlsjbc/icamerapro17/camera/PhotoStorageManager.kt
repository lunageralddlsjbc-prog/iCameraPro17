package com.lunageralddlsjbc.icamerapro17.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import android.util.Log

data class PhotoCopies(
    val originalUri: Uri,
    val enhancedUri: Uri,
    val extraBackupUri: Uri? = null
)

object PhotoStorageManager {
    private const val TAG = "PhotoStorageManager"

    fun createCopies(
        context: Context,
        sourceUri: Uri,
        keepExtraBackup: Boolean
    ): PhotoCopies {
        val original = copyPhoto(context, sourceUri, "Originals", "ORIGINAL")
        try {
            val enhanced = copyPhoto(context, sourceUri, "Enhanced", "ENHANCED")
            val extra = if (keepExtraBackup) {
                try {
                    copyPhoto(context, sourceUri, "Backups", "BACKUP")
                } catch (e: Exception) {
                    Log.e(TAG, "Optional extra backup failed", e)
                    null
                }
            } else null

            return PhotoCopies(original, enhanced, extra)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun copyPhoto(
        context: Context,
        source: Uri,
        folder: String,
        prefix: String
    ): Uri {
        val resolver = context.contentResolver
        val stamp = "${System.currentTimeMillis()}_${System.nanoTime()}"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${prefix}_${stamp}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/iCamera Pro 17/$folder"
                )
            }
        }

        val destination = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw IllegalStateException("Could not create $folder image")

        try {
            val input = resolver.openInputStream(source)
                ?: throw IllegalStateException("Could not read captured photo")
            val output = resolver.openOutputStream(destination, "w")
                ?: throw IllegalStateException("Could not write $folder image")

            input.use { src ->
                output.use { dst -> src.copyTo(dst) }
            }
            return destination
        } catch (e: Exception) {
            resolver.delete(destination, null, null)
            throw e
        }
    }

    fun canEnhanceSafely(context: Context, uri: Uri): Boolean {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return false

        val pixels = options.outWidth.toLong() * options.outHeight.toLong()
        if (pixels <= 0L) return false

        // The current enhancer holds several full-size pixel buffers.
        val estimatedBytes = pixels * 16L
        val safeBudget = Runtime.getRuntime().maxMemory() * 35L / 100L
        val safe = estimatedBytes <= safeBudget

        Log.i(
            TAG,
            "Enhancement memory check: ${options.outWidth}x${options.outHeight}, " +
                "estimated=${estimatedBytes / 1048576}MB, budget=${safeBudget / 1048576}MB, safe=$safe"
        )
        return safe
    }

    fun restoreExifMetadata(context: Context, source: Uri, destination: Uri) {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(source)
            ?: throw IllegalStateException("Could not read original EXIF")

        input.use { sourceStream ->
            val originalExif = ExifInterface(sourceStream)
            val descriptor = resolver.openFileDescriptor(destination, "rw")
                ?: throw IllegalStateException("Could not open enhanced image metadata")

            descriptor.use { fd ->
                val outputExif = ExifInterface(fd.fileDescriptor)
                val tags = listOf(
                    "DateTime",
                    "DateTimeOriginal",
                    "DateTimeDigitized",
                    "Make",
                    "Model",
                    "Orientation",
                    "FocalLength",
                    "FNumber",
                    "ExposureTime",
                    "ISOSpeedRatings",
                    "Flash",
                    "WhiteBalance",
                    "LensMake",
                    "LensModel",
                    "ExposureBiasValue",
                    "MeteringMode",
                    "PhotographicSensitivity"
                )

                for (tag in tags) {
                    originalExif.getAttribute(tag)?.let {
                        outputExif.setAttribute(tag, it)
                    }
                }
                outputExif.saveAttributes()
            }
        }
    }
}
