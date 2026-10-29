package io.github.cocokoniq.fancyir.core.ir

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertTrue

/** Minimal decoders used only to round-trip the encoders in tests. */
object ReferenceDecoder {

    fun assertNear(expected: Int, actual: Int, toleranceUs: Int = 50, what: String = "slice") {
        assertTrue(abs(expected - actual) <= toleranceUs, "$what: expected ≈$expected µs, was $actual µs")
    }

    /** NEC-style pulse distance, LSB first. Returns the payload. */
    fun pulseDistance(timings: IntArray, leaderMark: Int, leaderSpace: Int, unit: Int, bits: Int): Long {
        assertNear(leaderMark, timings[0], what = "leader mark")
        assertNear(leaderSpace, timings[1], what = "leader space")
        var value = 0L
        for (i in 0 until bits) {
            assertNear(unit, timings[2 + 2 * i], what = "bit $i mark")
            if (timings[3 + 2 * i] > 2 * unit) value = value or (1L shl i)
        }
        assertNear(unit, timings[2 + 2 * bits], what = "stop mark")
        return value
    }

    /** Sony pulse width, LSB first. Returns the payload. */
    fun pulseWidth(timings: IntArray, unit: Int, bits: Int): Long {
        assertNear(4 * unit, timings[0], what = "leader mark")
        var value = 0L
        for (i in 0 until bits) {
            if (timings[2 + 2 * i] > (1.5 * unit).roundToInt()) value = value or (1L shl i)
        }
        return value
    }

    /**
     * Expands Manchester timings into half-bit levels (true = mark). [leadingSpace] restores a
     * leading half that the encoder dropped because patterns must start with a mark; trailing
     * spaces merged into the frame gap are restored by padding to [padTo] halves.
     */
    fun halves(timings: IntArray, halfUs: Int, leadingSpace: Boolean = false, padTo: Int = 0): List<Boolean> {
        val levels = ArrayList<Boolean>()
        if (leadingSpace) levels += false
        timings.forEachIndexed { index, slice ->
            val count = (slice.toDouble() / halfUs).roundToInt()
            repeat(count) { levels += index % 2 == 0 }
        }
        while (levels.size < padTo) levels += false
        return levels
    }
}
