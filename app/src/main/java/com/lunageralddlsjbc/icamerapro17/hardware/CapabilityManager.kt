package com.lunageralddlsjbc.icamerapro17.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import androidx.camera.core.CameraSelector

data class CameraCapabilities(
    val hasBackCamera: Boolean = false,
    val hasFrontCamera: Boolean = false,
    val supportsManualExposure: Boolean = false,
    val supportsManualFocus: Boolean = false,
    val supportsRawCapture: Boolean = false,
    val maxZoom: Float = 1.0f,
    val minExposureComp: Int = -2,
    val maxExposureComp: Int = 2,
    val sensorSize: String = "Unknown"
)

object CapabilityManager {
    fun detect(context: Context): CameraCapabilities {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var capabilities = CameraCapabilities()
        
        try {
            // Check Back Camera
            if (CameraSelector.DEFAULT_BACK_CAMERA.filter(cameraManager.cameraIdList).isNotEmpty()) {
                capabilities = capabilities.copy(hasBackCamera = true)
                val chars = cameraManager.getCameraCharacteristics(
                    CameraSelector.DEFAULT_BACK_CAMERA.filter(cameraManager.cameraIdList).first()
                )
                
                // Manual Controls
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                capabilities = capabilities.copy(
                    supportsManualExposure = caps?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
                    ) == true,
                    supportsManualFocus = caps?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_FOCUS
                    ) == true,
                    supportsRawCapture = caps?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
                    ) == true
                )
                
                // Zoom
                capabilities = capabilities.copy(
                    maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                )
                
                // Exposure Range
                val expRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                if (expRange != null) {
                    capabilities = capabilities.copy(
                        minExposureComp = expRange.lower,
                        maxExposureComp = expRange.upper
                    )
                }
            }
            
            // Check Front Camera
            if (CameraSelector.DEFAULT_FRONT_CAMERA.filter(cameraManager.cameraIdList).isNotEmpty()) {
                capabilities = capabilities.copy(hasFrontCamera = true)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return capabilities
    }
}
