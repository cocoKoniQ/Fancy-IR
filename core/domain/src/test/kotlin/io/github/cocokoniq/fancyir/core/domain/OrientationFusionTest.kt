package io.github.cocokoniq.fancyir.core.domain

import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import io.github.cocokoniq.fancyir.core.domain.spatial.AimDirection
import io.github.cocokoniq.fancyir.core.domain.spatial.MotionState
import io.github.cocokoniq.fancyir.core.domain.spatial.OrientationFusion
import io.github.cocokoniq.fancyir.core.domain.spatial.Posture
import io.github.cocokoniq.fancyir.core.domain.spatial.SpatialPrior
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OrientationFusionTest {

    /** Device→world matrix for a phone held upright, camera pointing at [azimuthDeg] and [elevationDeg]. */
    private fun matrix(azimuthDeg: Float, elevationDeg: Float = 0f): FloatArray {
        val az = Math.toRadians(azimuthDeg.toDouble())
        val el = Math.toRadians(elevationDeg.toDouble())
        // Camera axis c = -z_device; device y ≈ up tilted with elevation; x = y × z.
        val c = doubleArrayOf(cos(el) * sin(az), cos(el) * cos(az), sin(el))
        val y = doubleArrayOf(-sin(el) * sin(az), -sin(el) * cos(az), cos(el))
        val z = doubleArrayOf(-c[0], -c[1], -c[2])
        val x = doubleArrayOf(y[1] * z[2] - y[2] * z[1], y[2] * z[0] - y[0] * z[2], y[0] * z[1] - y[1] * z[0])
        return floatArrayOf(
            x[0].toFloat(), y[0].toFloat(), z[0].toFloat(),
            x[1].toFloat(), y[1].toFloat(), z[1].toFloat(),
            x[2].toFloat(), y[2].toFloat(), z[2].toFloat(),
        )
    }

    private fun earthField(fusion: OrientationFusion) = repeat(10) { fusion.onMagneticField(20f, 30f, -30f) } // ≈47 µT

    private fun near(expected: Float, actual: Float, tolerance: Float) =
        assertTrue(abs(expected - actual) <= tolerance, "expected ≈$expected, was $actual")

    @Test
    fun `heading follows the game vector, corrected toward the magnetometer when trusted`() {
        val fusion = OrientationFusion()
        fusion.onMagnetometerAccuracy(3)
        earthField(fusion)
        var t = 0L
        // Game yaw has drifted 30° from true north; the absolute vector says 100°.
        repeat(200) {
            t += 20_000_000
            fusion.onGameRotation(matrix(70f), t)
            fusion.onAbsoluteRotation(matrix(100f), t)
        }

        val state = fusion.state
        near(100f, assertNotNull(state.direction).azimuthDeg, 2f)
        assertTrue(state.headingConfidence > 0.5f, "confidence ${state.headingConfidence}")
    }

    @Test
    fun `a disturbed magnetic field is ignored and heading holds on the gyro`() {
        val fusion = OrientationFusion()
        fusion.onMagnetometerAccuracy(3)
        earthField(fusion)
        var t = 0L
        repeat(100) {
            t += 20_000_000
            fusion.onGameRotation(matrix(10f), t)
            fusion.onAbsoluteRotation(matrix(10f), t)
        }
        // Next to a soundbar: the field balloons and the absolute heading swings 60°.
        repeat(20) { fusion.onMagneticField(90f, 80f, -60f) }
        repeat(100) {
            t += 20_000_000
            fusion.onGameRotation(matrix(10f), t)
            fusion.onAbsoluteRotation(matrix(70f), t)
        }

        near(10f, assertNotNull(fusion.state.direction).azimuthDeg, 1f)
        assertEquals(false, fusion.magnetometerTrusted())
    }

    @Test
    fun `gyroscope speed classifies motion with a still hold time`() {
        val fusion = OrientationFusion()
        var t = 0L
        fun spin(radPerS: Float, ms: Int) = repeat(ms / 10) {
            t += 10_000_000
            fusion.onGyroscope(radPerS, 0f, 0f, t)
        }

        spin(4f, 300)
        assertEquals(MotionState.FLICK, fusion.state.motion)
        spin(1f, 300)
        assertEquals(MotionState.PANNING, fusion.state.motion)
        spin(0f, 200)
        assertEquals(MotionState.STEADY, fusion.state.motion, "not still until the hold time passes")
        spin(0f, 600)
        assertEquals(MotionState.STILL, fusion.state.motion)
    }

    @Test
    fun `posture comes from camera elevation`() {
        val fusion = OrientationFusion()
        fusion.onGameRotation(matrix(0f, 10f), 1)
        assertEquals(Posture.AIMING, fusion.state.posture)
        fusion.onGameRotation(matrix(0f, -60f), 2)
        assertEquals(Posture.LOWERED, fusion.state.posture)
        fusion.onGameRotation(matrix(0f, -88f), 3)
        assertEquals(Posture.FACE_UP, fusion.state.posture)
    }

    @Test
    fun `anchors in another room are ignored and low confidence widens the kernel`() {
        val prior = SpatialPrior()
        val office = DeviceId("office-monitor")
        val bedroom = DeviceId("bedroom-monitor")
        prior.record(office, AimDirection(90f, 0f), context = "wifi:office")
        prior.record(bedroom, AimDirection(90f, 0f), context = "wifi:bedroom")

        val scores = prior.scores(AimDirection(90f, 0f), context = "wifi:office")
        assertEquals(setOf(office), scores.keys)

        val sharp = prior.scores(AimDirection(110f, 0f), confidence = 1f, context = "wifi:office").getValue(office)
        val blurry = prior.scores(AimDirection(110f, 0f), confidence = 0.3f, context = "wifi:office").getValue(office)
        assertTrue(blurry > sharp, "uncertain heading should tolerate a 20° miss: $sharp vs $blurry")
    }
}
