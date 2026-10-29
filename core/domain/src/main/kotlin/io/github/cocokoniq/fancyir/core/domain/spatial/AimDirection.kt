package io.github.cocokoniq.fancyir.core.domain.spatial

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Degrees per radian. */
private const val DEG = 57.29578f

/** Where the back camera points: azimuth (0° = north, 90° = east), elevation, and roll about the camera axis. */
data class AimDirection(val azimuthDeg: Float, val elevationDeg: Float, val rollDeg: Float = 0f) {

    /** Great-circle angle between two pointing directions, ignoring roll. */
    fun angleTo(other: AimDirection): Float {
        val a = unitVector()
        val b = other.unitVector()
        val dot = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1f, 1f)
        return acos(dot) * DEG
    }

    /** Signed azimuth difference to [other] in (-180, 180]; positive = [other] is to the right. */
    fun relativeAzimuthTo(other: AimDirection): Float {
        var d = (other.azimuthDeg - azimuthDeg) % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /** (east, north, up) */
    fun unitVector(): FloatArray {
        val az = azimuthDeg / DEG
        val el = elevationDeg / DEG
        return floatArrayOf(cos(el) * sin(az), cos(el) * cos(az), sin(el))
    }

    companion object {
        fun fromVector(east: Float, north: Float, up: Float, rollDeg: Float = 0f): AimDirection {
            val norm = sqrt(east * east + north * north + up * up).coerceAtLeast(1e-6f)
            var azimuth = atan2(east, north) * DEG
            if (azimuth < 0) azimuth += 360f
            return AimDirection(azimuth, asin((up / norm).coerceIn(-1f, 1f)) * DEG, rollDeg)
        }
    }
}

object AimMath {

    /**
     * From the row-major 3×3 device→world rotation matrix that
     * `SensorManager.getRotationMatrixFromVector` produces. The back camera looks along the
     * device's −Z axis.
     */
    fun fromRotationMatrix(r: FloatArray): AimDirection {
        require(r.size == 9) { "expected a 3×3 matrix" }
        val roll = atan2(r[6], r[7]) * DEG
        return AimDirection.fromVector(east = -r[2], north = -r[5], up = -r[8], rollDeg = roll)
    }
}
