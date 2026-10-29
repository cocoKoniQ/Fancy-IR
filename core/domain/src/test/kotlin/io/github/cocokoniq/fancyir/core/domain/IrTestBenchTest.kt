package io.github.cocokoniq.fancyir.core.domain

import io.github.cocokoniq.fancyir.core.domain.bench.IrTestBench
import io.github.cocokoniq.fancyir.core.ir.IrProtocol
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The documented expectations must match what the encoders actually emit. */
class IrTestBenchTest {

    @Test
    fun `leaders match the encoded frames`() {
        for (vector in IrTestBench.vectors) {
            val frame = vector.code.toSignal().frame
            val mark = if (vector.code.protocol == IrProtocol.RC5) 889 else frame[0]
            assertTrue(abs(mark - vector.leaderMarkUs) <= 1, "${vector.label} leader mark ${frame[0]}")
            if (vector.code.protocol != IrProtocol.RC5) assertTrue(abs(frame[1] - vector.leaderSpaceUs) <= 1, "${vector.label} leader space")
        }
    }

    @Test
    fun `decoder words match the encoded bits`() {
        for (vector in IrTestBench.vectors.filter { it.word != null }) {
            val frame = vector.code.toSignal().frame
            val bits = when (vector.code.protocol) {
                // Pulse distance: a long space after each mark is a 1.
                IrProtocol.NEC, IrProtocol.NEC_EXT, IrProtocol.SAMSUNG32 -> (0 until vector.bits).map { if (frame[3 + 2 * it] > 1_000) 1L else 0L }
                // Pulse width: a long mark is a 1.
                else -> (0 until vector.bits).map { if (frame[2 + 2 * it] > 900) 1L else 0L }
            }
            assertEquals(vector.word, bits.fold(0L) { acc, bit -> (acc shl 1) or bit }, vector.label)
        }
    }
}
