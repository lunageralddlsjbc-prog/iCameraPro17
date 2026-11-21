package com.lunageralddlsjbc.icamerapro17.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Range
import android.util.Size

object CameraHardwareInspector {

    fun createReport(context: Context): String {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val report = StringBuilder()

        report.appendLine("iCamera Pro 17 — DEVICE CAMERA REPORT")
        report.appendLine("Android API: ${Build.VERSION.SDK_INT}")
        report.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        report.appendLine("Device: ${Build.MODEL}")
        report.appendLine()

        for (id in manager.cameraIdList) {
            try {
                val c = manager.getCameraCharacteristics(id)
                val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Unknown"
                }

                val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                    else -> "Unknown"
                }

                report.appendLine("CAMERA ID: $id ($facing)")
                report.appendLine("Hardware level: $level")

                c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let {
                    report.appendLine("Sensor array: ${it.width} x ${it.height}")
                }

                c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let {
                    report.appendLine("Active sensor area: ${it.width()} x ${it.height()}")
                }

                c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.let {
                    report.appendLine("Focal lengths: ${it.joinToString()} mm")
                }

                c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.let {
                    report.appendLine("Apertures: ${it.joinToString()}")
                }

                c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let {
                    report.appendLine("ISO range: ${it.lower}–${it.upper}")
                }

                c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let {
                    report.appendLine("Exposure time: ${it.lower}–${it.upper} ns")
                }

                val af = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                report.appendLine("Autofocus modes: ${af?.joinToString() ?: "Not reported"}")

                val ois = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                report.appendLine("Optical stabilization: ${ois?.joinToString() ?: "Not reported"}")

                val eis = c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                report.appendLine("Video stabilization modes: ${eis?.joinToString() ?: "Not reported"}")

                val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                report.appendLine("RAW support: ${
                    capabilities?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
                    ) == true
                }")
                report.appendLine("Logical multi-camera: ${
                    capabilities?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                    ) == true
                }")

                if (Build.VERSION.SDK_INT >= 28) {
                    report.appendLine("Physical camera IDs: ${c.physicalCameraIds.joinToString().ifEmpty { "None reported" }}")
                }

                val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)
                report.appendLine("JPEG resolutions:")
                jpegSizes?.sortedByDescending { it.width.toLong() * it.height }
                    ?.take(12)
                    ?.forEach { report.appendLine("  ${it.width} x ${it.height}") }

                val videoSizes = map?.getOutputSizes(MediaRecorder::class.java)
                report.appendLine("Video resolutions:")
                videoSizes?.sortedByDescending { it.width.toLong() * it.height }
                    ?.take(12)
                    ?.forEach { report.appendLine("  ${it.width} x ${it.height}") }

                val fps = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                report.appendLine("AE frame-rate ranges: ${fps?.joinToString() ?: "Not reported"}")
                report.appendLine()
            } catch (e: Exception) {
                report.appendLine("Camera $id inspection failed: ${e.message}")
                report.appendLine()
            }
        }

        return report.toString()
    }
}
