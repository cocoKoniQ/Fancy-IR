package io.github.cocokoniq.fancyir.core.domain

import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import io.github.cocokoniq.fancyir.core.domain.spatial.AimDirection
import io.github.cocokoniq.fancyir.core.domain.spatial.AimMath
import io.github.cocokoniq.fancyir.core.domain.spatial.SpatialPrior
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpatialPriorTest {

    private fun near(expected: Float, actual: Float, tolerance: Float = 0.5f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected ≈$expected, was $actual")

    @Test
    fun `upright phone aiming north reads azimuth 0, elevation 0, roll 0`() {
        // Columns are device x/y/z in world (east, north, up): x = east, y = up, z = south.
        val r = floatArrayOf(
            1f, 0f, 0f,
            0f, 0f, -1f,
            0f, 1f, 0f,
        )
        val aim = AimMath.fromRotationMatrix(r)
        near(0f, aim.azimuthDeg)
        near(0f, aim.elevationDeg)
        near(0f, aim.rollDeg)
    }

    @Test
    fun `angles and relative azimuths wrap correctly`() {
        val a = AimDirection(350f, 0f)
        val b = AimDirection(10f, 0f)
        near(20f, a.angleTo(b))
        near(20f, a.relativeAzimuthTo(b))
        near(-20f, b.relativeAzimuthTo(a))
    }

    @Test
    fun `devices score by proximity to their anchors`() {
        val prior = SpatialPrior()
        val tv = DeviceId("tv")
        val ac = DeviceId("ac")
        prior.record(tv, AimDirection(10f, -5f))
        prior.record(ac, AimDirection(190f, 20f))

        val scores = prior.scores(AimDirection(12f, -4f))

        assertTrue(scores.getValue(tv) > 0.9f)
        assertTrue(scores.getValue(ac) < 0.01f)
    }

    @Test
    fun `nearby samples merge into one anchor and the radar shows its bearing`() {
        val prior = SpatialPrior()
        val tv = DeviceId("tv")
        repeat(5) { prior.record(tv, AimDirection(30f + it, 0f)) }

        val bearing = prior.bearings(AimDirection(0f, 0f)).single()
        assertEquals(5, bearing.weight)
        near(32f, bearing.relativeAzimuthDeg, tolerance = 1f)
    }
}
