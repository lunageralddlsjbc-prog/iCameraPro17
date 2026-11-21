package com.lunageralddlsjbc.icamerapro17.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.core.CameraSelector

data class CameraCapabilities(val supportsManualExposure: Boolean = false)

object CapabilityManager {
    fun detect(context: Context): CameraCapabilities {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val allIds = cameraManager.cameraIdList.toList()
            val backId = allIds.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            if (backId != null) {
                val chars = cameraManager.getCameraCharacteristics(backId)
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                return CameraCapabilities(
                    supportsManualExposure = caps?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
        return CameraCapabilities()
    }
}
