package com.lunageralddlsjbc.icamerapro17.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.core.CameraSelector

data class CameraCapabilities(
    val hasBackCamera: Boolean = false,
    val hasFrontCamera: Boolean = false,
    val supportsManualExposure: Boolean = false,
    val maxZoom: Float = 1.0f,
    val minExposureComp: Int = -2,
    val maxExposureComp: Int = 2
)

object CapabilityManager {
    fun detect(context: Context): CameraCapabilities {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var caps = CameraCapabilities()
        
        try {
            val allIds = cameraManager.cameraIdList.toList()
            
            val backId = allIds.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            
            if (backId != null) {
                caps = caps.copy(hasBackCamera = true)
                val chars = cameraManager.getCameraCharacteristics(backId)
                val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                
                caps = caps.copy(
                    supportsManualExposure = capabilities?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
                    ) == true,
                    maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                )
                
                val expRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                if (expRange != null) {
                    caps = caps.copy(minExposureComp = expRange.lower, maxExposureComp = expRange.upper)
                }
            }
            
            val frontId = allIds.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }
            
            if (frontId != null) {
                caps = caps.copy(hasFrontCamera = true)
            }
        } catch (e: Exception) { 
            e.printStackTrace() 
        }
        
        return caps
    }
}
