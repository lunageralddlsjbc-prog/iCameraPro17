package com.lunageralddlsjbc.icamerapro17.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import android.util.Rational
import kotlin.math.abs
import kotlin.math.roundToInt

data class HdrExposure(
    val compensationIndex: Int,
    val requestedEv: Float,
    val actualEv: Float
)

data class HdrBracketPlan(
    val exposures: List<HdrExposure>,
    val supported: Boolean,
    val reason: String
)

object HdrExposurePlanner {

    fun createPlan(
        cameraManager: CameraManager,
        cameraId: String,
        frameCount: Int = 3
    ): HdrBracketPlan {
        if (frameCount != 3 && frameCount != 5) {
            return unsupported("Only 3-frame and 5-frame brackets are supported")
        }

        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val range = characteristics.get(
                CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE
            ) ?: return unsupported("Camera does not advertise exposure compensation")

            val step = characteristics.get(
                CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP
            ) ?: return unsupported("Camera does not advertise an exposure step")

            val stepEv = step.toFloat()
            if (!stepEv.isFinite() || stepEv <= 0f) {
                return unsupported("Camera exposure step is invalid")
            }

            if (range.lower == range.upper) {
                return unsupported("Camera exposure compensation is fixed")
            }

            val requested = if (frameCount == 3) {
                listOf(-1.5f, 0f, 1.5f)
            } else {
                listOf(-2f, -1f, 0f, 1f, 2f)
            }

            val exposures = requested.map { ev ->
                val index = (ev / stepEv).roundToInt()
                    .coerceIn(range.lower, range.upper)
                HdrExposure(
                    compensationIndex = index,
                    requestedEv = ev,
                    actualEv = index * stepEv
                )
            }.distinctBy { it.compensationIndex }
                .sortedBy { it.compensationIndex }

            if (exposures.size < 3 || exposures.none { it.compensationIndex == 0 }) {
                return unsupported(
                    "Camera range is too narrow for a useful multi-exposure bracket"
                )
            }

            HdrBracketPlan(
                exposures = exposures,
                supported = true,
                reason = "Exposure bracket calculated from camera-supported compensation"
            )
        } catch (e: Exception) {
            unsupported("Could not inspect camera exposure controls: ${e.message}")
        }
    }

    private fun unsupported(reason: String) = HdrBracketPlan(
        exposures = emptyList(),
        supported = false,
        reason = reason
    )
}
