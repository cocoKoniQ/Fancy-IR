package io.github.cocokoniq.fancyir.core.ir

import io.github.cocokoniq.fancyir.core.ir.ReferenceDecoder.assertNear
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProtocolEncodersTest {

    @Test
    fun `NEC encodes address, inverted address, command, inverted command LSB first`() {
        // LG TV power: NEC address 0x04, command 0x08.
        val signal = ProtocolEncoders.encode(IrProtocol.NEC, address = 0x04, command = 0x08)

        assertEquals(38_000, signal.carrierHz)
        val payload = ReferenceDecoder.pulseDistance(signal.frame, 9024, 4512, 564, bits = 32)
        assertEquals(0xF708FB04L, payload)
        assertEquals(108_000L, signal.frame.sum().toLong(), "frame period")
    }

    @Test
    fun `NEC repeat code is a short leader and a single mark on the same period`() {
        val repeat = ProtocolEncoders.encode(IrProtocol.NEC, 0x04, 0x02).repeatFrame!!

        assertContentEquals(intArrayOf(9024, 2256, 564, 108_000 - 9024 - 2256 - 564), repeat)
    }

    @Test
    fun `NECext sends 16-bit address and 16-bit command without inversion`() {
        val signal = ProtocolEncoders.encode(IrProtocol.NEC_EXT, address = 0x7F00, command = 0xEA15)

        val payload = ReferenceDecoder.pulseDistance(signal.frame, 9024, 4512, 564, bits = 32)
        assertEquals(0xEA157F00L, payload)
    }

    @Test
    fun `NEC42 sends 13-bit address and its inverse`() {
        val signal = ProtocolEncoders.encode(IrProtocol.NEC42, address = 0x1ABC, command = 0x5A)

        val payload = ReferenceDecoder.pulseDistance(signal.frame, 9024, 4512, 564, bits = 42)
        assertEquals(0x1ABCL, payload and 0x1FFF)
        assertEquals(0x1ABCL.inv() and 0x1FFF, (payload shr 13) and 0x1FFF)
        assertEquals(0x5AL, (payload shr 26) and 0xFF)
        assertEquals(0xA5L, (payload shr 34) and 0xFF)
    }

    @Test
    fun `Samsung32 repeats the address byte and uses a 4_5 ms leader`() {
        // Samsung TV power: address 0x07, command 0x02.
        val signal = ProtocolEncoders.encode(IrProtocol.SAMSUNG32, address = 0x07, command = 0x02)

        val payload = ReferenceDecoder.pulseDistance(signal.frame, 4512, 4512, 564, bits = 32)
        assertEquals(0xFD020707L, payload)
        assertEquals(null, signal.repeatFrame, "Samsung repeats full frames")
    }

    @Test
    fun `RC5 is Manchester with start bits, toggle, 5-bit address and 6-bit command`() {
        val signal = ProtocolEncoders.encode(IrProtocol.RC5, address = 0x00, command = 0x0C, toggle = true)

        assertEquals(36_000, signal.carrierHz)
        val halves = ReferenceDecoder.halves(signal.frame.dropLast(1).toIntArray(), 889, leadingSpace = true, padTo = 28)
        val bits = halves.take(28).chunked(2).map { (first, second) -> !first && second }
        val expected = listOf(true, true, true) + bitsMsb(0x00, 5) + bitsMsb(0x0C, 6)
        assertEquals(expected, bits)
    }

    @Test
    fun `RC5X clears the second start bit for commands of 64 and above`() {
        val signal = ProtocolEncoders.encode(IrProtocol.RC5X, address = 0x05, command = 0x4C)

        val halves = ReferenceDecoder.halves(signal.frame.dropLast(1).toIntArray(), 889, leadingSpace = true, padTo = 28)
        val bits = halves.take(28).chunked(2).map { (first, second) -> !first && second }
        assertEquals(listOf(true, false, false) + bitsMsb(0x05, 5) + bitsMsb(0x0C, 6), bits)
    }

    @Test
    fun `RC6 mode 0 has leader, start bit, mode 000, double-width toggle, address and command`() {
        val signal = ProtocolEncoders.encode(IrProtocol.RC6, address = 0x00, command = 0x0C, toggle = true)

        assertNear(6 * 444, signal.frame[0], what = "leader mark")
        val halves = ReferenceDecoder.halves(signal.frame.dropLast(1).toIntArray(), 444, padTo = 8 + 44)
        val body = halves.drop(8) // 6 mark + 2 space halves of leader
        fun bitAt(halfIndex: Int) = body[halfIndex] && !body[halfIndex + 1]
        assertEquals(true, bitAt(0), "start bit")
        assertEquals(listOf(false, false, false), listOf(bitAt(2), bitAt(4), bitAt(6)), "mode")
        assertEquals(listOf(true, true, false, false), body.subList(8, 12), "double-width toggle = 1")
        val payload = (0 until 16).map { bitAt(12 + 2 * it) }
        assertEquals(bitsMsb(0x00, 8) + bitsMsb(0x0C, 8), payload)
    }

    @Test
    fun `SIRC sends command then address LSB first, three frames per press`() {
        // Sony TV power: SIRC address 0x01, command 0x15.
        val signal = ProtocolEncoders.encode(IrProtocol.SIRC, address = 0x01, command = 0x15)

        assertEquals(40_000, signal.carrierHz)
        assertEquals(3, signal.minFrames)
        val payload = ReferenceDecoder.pulseWidth(signal.frame, 600, bits = 12)
        assertEquals(0x15L or (0x01L shl 7), payload)
        assertEquals(45_000L, signal.frame.sum().toLong())
        assertEquals(3 * 45_000L, signal.pressDurationUs)
    }

    @Test
    fun `SIRC20 carries a 13-bit address`() {
        val signal = ProtocolEncoders.encode(IrProtocol.SIRC20, address = 0x1A3F, command = 0x7F)

        assertEquals(0x7FL or (0x1A3FL shl 7), ReferenceDecoder.pulseWidth(signal.frame, 600, bits = 20))
    }

    @Test
    fun `every frame starts with a mark and ends with a gap`() {
        val encodable = IrProtocol.entries.filter { it.isEncodable }
        for (protocol in encodable) {
            val signal = ProtocolEncoders.encode(protocol, address = 1, command = 1)
            assertEquals(0, signal.frame.size % 2, "${protocol.flipperName} frame must end with a gap")
            assertTrue(signal.frame.all { it > 0 }, "${protocol.flipperName} has non-positive slices")
        }
    }

    @Test
    fun `out-of-range address is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolEncoders.encode(IrProtocol.NEC, address = 0x100, command = 0x01)
        }
    }

    @Test
    fun `unverified protocols refuse to encode`() {
        assertFailsWith<UnsupportedProtocolException> {
            ProtocolEncoders.encode(IrProtocol.KASEIKYO, address = 0x2002, command = 0x3D)
        }
    }

    private fun bitsMsb(value: Int, bits: Int) = (bits - 1 downTo 0).map { (value shr it) and 1 == 1 }
}
