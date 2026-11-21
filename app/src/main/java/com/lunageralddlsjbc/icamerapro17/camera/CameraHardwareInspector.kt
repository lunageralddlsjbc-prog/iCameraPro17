package com.lunageralddlsjbc.icamerapro17.camera

import android.content.Context
import android.os.Build

object CameraHardwareInspector {

    fun createReport(context: Context): String {
        val cameras = CameraCapabilityManager.detect(context)
        val report = StringBuilder()

        report.appendLine("iCamera Pro 17 — DEVICE CAMERA REPORT")
        report.appendLine("Android API: ${Build.VERSION.SDK_INT}")
        report.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        report.appendLine("Device: ${Build.MODEL}")
        report.appendLine("Detected cameras: ${cameras.size}")
        report.appendLine("Back: ${cameras.count { it.facing == "Back" }} | Front: ${cameras.count { it.facing == "Front" }}")
        report.appendLine()

        if (cameras.isEmpty()) {
            report.appendLine("No accessible cameras were reported by Camera2.")
        }

        cameras.forEach { c ->
            report.appendLine("CAMERA ${c.id} — ${c.facing}")
            report.appendLine("Hardware level: ${c.hardwareLevel}")
            report.appendLine("Sensor pixel array: ${c.sensorArray ?: "Not reported"}")
            report.appendLine("Active sensor area: ${c.activeArray ?: "Not reported"}")
            report.appendLine("Sensor orientation: ${c.sensorOrientation ?: "Not reported"} degrees")
            report.appendLine("Focal lengths: ${c.focalLengths.joinToString().ifEmpty { "Not reported" }} mm")
            report.appendLine("Apertures: ${c.apertures.joinToString().ifEmpty { "Not reported" }}")
            report.appendLine("Minimum focus distance: ${c.minimumFocusDistance?.toString() ?: "Not reported"} diopters")
            report.appendLine("ISO range: ${c.isoRange ?: "Not reported"}")
            report.appendLine("Exposure time: ${c.exposureTimeRange ?: "Not reported"}")
            report.appendLine("Exposure compensation: ${c.exposureCompensationRange ?: "Not reported"} EV steps")
            report.appendLine("Autofocus modes: ${c.autofocusModes.joinToString().ifEmpty { "Not reported" }}")
            report.appendLine("Optical stabilization modes: ${c.opticalStabilizationModes.joinToString().ifEmpty { "Not reported" }}")
            report.appendLine("Electronic video stabilization modes: ${c.videoStabilizationModes.joinToString().ifEmpty { "Not reported" }}")
            report.appendLine("Flash: ${c.flashAvailable}")
            report.appendLine("RAW support: ${c.rawSupported}")
            report.appendLine("HEIF output: ${c.heifSupported}")
            report.appendLine("Logical multi-camera: ${c.logicalMultiCamera}")
            report.appendLine("Physical camera IDs: ${c.physicalCameraIds.joinToString().ifEmpty { "None reported" }}")
            report.appendLine("Maximum digital zoom: ${c.maxDigitalZoom}x")
            report.appendLine("Hardware zoom ratio range: ${c.zoomRatioRange ?: "Not reported; crop fallback may be needed"}")

            report.appendLine("JPEG resolutions (largest first):")
            c.jpegSizes.take(15).forEach { report.appendLine("  ${it.width} x ${it.height}") }
            report.appendLine("RAW resolutions:")
            c.rawSizes.take(10).forEach { report.appendLine("  ${it.width} x ${it.height}") }
            report.appendLine("HEIF resolutions:")
            c.heifSizes.take(10).forEach { report.appendLine("  ${it.width} x ${it.height}") }
            report.appendLine("Video resolutions:")
            c.videoSizes.take(15).forEach { report.appendLine("  ${it.width} x ${it.height}") }
            report.appendLine("Advertised AE frame-rate ranges: ${c.fpsRanges.joinToString().ifEmpty { "Not reported" }}")
            report.appendLine()
        }

        return report.toString()
    }
}
