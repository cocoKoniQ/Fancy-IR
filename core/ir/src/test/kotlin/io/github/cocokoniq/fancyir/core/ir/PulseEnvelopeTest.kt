package io.github.cocokoniq.fancyir.core.ir

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class PulseEnvelopeTest {

    @Test
    fun `envelope is the marked fraction of each bin`() {
        assertContentEquals(floatArrayOf(1f, 0f, 1f), PulseEnvelope.of(intArrayOf(1000, 1000, 1000), binUs = 1000))
        assertContentEquals(floatArrayOf(0.5f), PulseEnvelope.of(intArrayOf(500, 500), binUs = 1000))
    }

    @Test
    fun `marks spanning bin boundaries are split across bins`() {
        assertContentEquals(floatArrayOf(1f, 0.5f), PulseEnvelope.of(intArrayOf(1500, 500), binUs = 1000))
    }

    @Test
    fun `NEC and SIRC have distinguishable envelopes`() {
        val nec = PulseEnvelope.of(ProtocolEncoders.encode(IrProtocol.NEC, 4, 8).frame, binUs = 5_000)
        val sirc = PulseEnvelope.of(ProtocolEncoders.encode(IrProtocol.SIRC, 1, 0x15).frame, binUs = 5_000)
        assertTrue(nec[0] > 0.99f, "NEC opens with a 9 ms burst")
        assertTrue(sirc[0] < 0.9f, "SIRC leader is only 2.4 ms")
    }
}
