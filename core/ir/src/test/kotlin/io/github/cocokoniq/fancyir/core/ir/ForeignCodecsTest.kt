package io.github.cocokoniq.fancyir.core.ir

import io.github.cocokoniq.fancyir.core.ir.ReferenceDecoder.assertNear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ForeignCodecsTest {

    @Test
    fun `pronto learned code decodes carrier and an NEC leader`() {
        // 38 kHz (0x006D), one burst pair 0x157/0xAC (≈9 ms/4.5 ms) plus a stop pair.
        val raw = ProntoCodec.decode("0000 006D 0002 0000 0157 00AC 0015 05F7")

        assertEquals(38_029, raw.carrierHz, "carrier from frequency word")
        assertNear(9019, raw.timingsUs[0], toleranceUs = 2, what = "leader mark")
        assertNear(4523, raw.timingsUs[1], toleranceUs = 2, what = "leader space")
        assertEquals(4, raw.timingsUs.size)
    }

    @Test
    fun `pronto with only a repeat sequence uses it`() {
        val raw = ProntoCodec.decode("0000 006D 0000 0001 0157 0056")
        assertEquals(2, raw.timingsUs.size)
    }

    @Test
    fun `broadlink packet from SmartIR decodes the 9 ms leader`() {
        // Prefix of a real SmartIR climate code (Toyotomi, codes/climate/1000.json "off").
        val raw = BroadlinkCodec.decodeBase64("JgAMAAABKZEXNBgQFxEXEA==")

        assertNear(9045, raw.timingsUs[0], toleranceUs = 2, what = "leader mark (0x0129 ticks)")
        assertNear(4416, raw.timingsUs[1], toleranceUs = 2, what = "leader space (0x91 ticks)")
        assertNear(700, raw.timingsUs[2], toleranceUs = 2, what = "bit mark")
        assertEquals(38_000, raw.carrierHz)
    }

    @Test
    fun `broadlink RF packets are rejected`() {
        assertFailsWith<IllegalArgumentException> { BroadlinkCodec.decode(byteArrayOf(0xB2.toByte(), 0, 2, 0, 1, 2)) }
    }
}
