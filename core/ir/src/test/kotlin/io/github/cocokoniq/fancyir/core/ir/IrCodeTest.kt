package io.github.cocokoniq.fancyir.core.ir

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IrCodeTest {

    @Test
    fun `raw capture is sanitised and given an inter-frame gap`() {
        val raw = IrCode.Raw(carrierHz = 38_000, dutyCycle = 0.33f, timingsUs = intArrayOf(9000, 4500, 560, 560, 560, 9999))

        val signal = raw.toSignal()

        assertContentEquals(intArrayOf(9000, 4500, 560, 560, 560, 40_000), signal.frame)
        assertEquals(38_000, signal.carrierHz)
    }

    @Test
    fun `raw codes compare by content`() {
        val a = IrCode.Raw(38_000, 0.33f, intArrayOf(1, 2, 3))
        assertEquals(a, IrCode.Raw(38_000, 0.33f, intArrayOf(1, 2, 3)))
        assertNotEquals(a, IrCode.Raw(38_000, 0.33f, intArrayOf(1, 2, 4)))
    }

    @Test
    fun `hold uses the repeat frame after the first frame`() {
        val signal = ProtocolEncoders.encode(IrProtocol.NEC, 0x04, 0x02)
        val repeat = checkNotNull(signal.repeatFrame)

        val held = signal.hold(3)

        assertEquals(signal.frame.size + 2 * repeat.size, held.size)
        assertContentEquals(repeat, held.copyOfRange(held.size - repeat.size, held.size))
    }
}
