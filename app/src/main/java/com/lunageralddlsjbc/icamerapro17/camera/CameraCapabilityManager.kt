package com.lunageralddlsjbc.icamerapro17.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Size

data class CameraCapability(
    val id: String,
    val facing: String,
    val hardwareLevel: String,
    val sensorArray: String?,
    val activeArray: String?,
    val sensorOrientation: Int?,
    val jpegSizes: List<Size>,
    val rawSizes: List<Size>,
    val heifSizes: List<Size>,
    val videoSizes: List<Size>,
    val focalLengths: List<Float>,
    val apertures: List<Float>,
    val minimumFocusDistance: Float?,
    val isoRange: String?,
    val exposureTimeRange: String?,
    val exposureCompensationRange: String?,
    val exposureCompensationStep: String?,
    val autofocusModes: List<Int>,
    val opticalStabilizationModes: List<Int>,
    val videoStabilizationModes: List<Int>,
    val flashAvailable: Boolean,
    val rawSupported: Boolean,
    val heifSupported: Boolean,
    val logicalMultiCamera: Boolean,
    val physicalCameraIds: List<String>,
    val fpsRanges: List<String>,
    val maxDigitalZoom: Float,
    val zoomRatioRange: String?
)

object CameraCapabilityManager {

    fun detect(context: Context): List<CameraCapability> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        return manager.cameraIdList.mapNotNull { id ->
            try {
                val c = manager.getCameraCharacteristics(id)
                val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?: intArrayOf()

                val jpeg = map?.getOutputSizes(ImageFormat.JPEG)?.toList()
                    .orEmpty().sortedByDescending { it.width.toLong() * it.height }

                val rawSupported = capabilities.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
                )
                val raw = if (rawSupported) {
                    map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList().orEmpty()
                } else emptyList()

                val heif = if (Build.VERSION.SDK_INT >= 29) {
                    map?.getOutputSizes(ImageFormat.HEIC)?.toList().orEmpty()
                } else emptyList()

                val video = map?.getOutputSizes(MediaRecorder::class.java)?.toList()
                    .orEmpty().sortedByDescending { it.width.toLong() * it.height }

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

                val pixelArray = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val activeArray = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                val iso = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                val exposure = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                val compensation = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                val compensationStep = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                val fps = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                val zoomRange = if (Build.VERSION.SDK_INT >= 30) {
                    c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                } else null

                CameraCapability(
                    id = id,
                    facing = facing,
                    hardwareLevel = level,
                    sensorArray = pixelArray?.let { "${it.width} x ${it.height}" },
                    activeArray = activeArray?.let { "${it.width()} x ${it.height()}" },
                    sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION),
                    jpegSizes = jpeg,
                    rawSizes = raw.sortedByDescending { it.width.toLong() * it.height },
                    heifSizes = heif.sortedByDescending { it.width.toLong() * it.height },
                    videoSizes = video,
                    focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
                    apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty(),
                    minimumFocusDistance = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE),
                    isoRange = iso?.let { "${it.lower}–${it.upper}" },
                    exposureTimeRange = exposure?.let { "${it.lower}–${it.upper} ns" },
                    exposureCompensationRange = compensation?.let { "${it.lower}–${it.upper}" },
                    exposureCompensationStep = compensationStep?.let {
                        "${it.numerator}/${it.denominator} EV per index"
                    },
                    autofocusModes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList().orEmpty(),
                    opticalStabilizationModes = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.toList().orEmpty(),
                    videoStabilizationModes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.toList().orEmpty(),
                    flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                    rawSupported = rawSupported,
                    heifSupported = heif.isNotEmpty(),
                    logicalMultiCamera = capabilities.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                    ),
                    physicalCameraIds = if (Build.VERSION.SDK_INT >= 28) {
                        c.physicalCameraIds.toList()
                    } else emptyList(),
                    fpsRanges = fps?.map { "${it.lower}–${it.upper} FPS" }.orEmpty(),
                    maxDigitalZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f,
                    zoomRatioRange = zoomRange?.let { "${it.lower}x–${it.upper}x" }
                )
            } catch (e: Exception) {
                android.util.Log.e("CameraCapabilityManager", "Cannot inspect camera $id", e)
                null
            }
        }
    }
}
