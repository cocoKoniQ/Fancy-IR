package io.github.cocokoniq.fancyir.core.ir

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CompiledCommandTest {

    @Test
    fun `compiled press equals the encoder's press`() {
        val code = IrCode.Parsed(IrProtocol.SIRC, 0x01, 0x15)

        val compiled = CompiledCommand.compile(code)

        assertEquals(40_000, compiled.carrierHz)
        assertContentEquals(code.toSignal().press(), compiled.press)
        assertNull(compiled.togglePress)
    }

    @Test
    fun `toggle protocols compile both phases and render alternates them`() {
        val compiled = CompiledCommand.compile(IrCode.Parsed(IrProtocol.RC6, 0x00, 0x10))
        val toggled = assertNotNull(compiled.togglePress)
        assertFalse(compiled.press.contentEquals(toggled))

        val rendered = compiled.render(2) { it == 1 }

        assertContentEquals(compiled.press + toggled, rendered)
    }

    @Test
    fun `timing codec round-trips through varints`() {
        val timings = intArrayOf(9024, 4512, 564, 1692, 0, 127, 128, 108_000, 2_000_000)

        val bytes = TimingCodec.encode(timings)

        assertContentEquals(timings, TimingCodec.decode(bytes))
        assertEquals(true, bytes.size < timings.size * 4)
    }
}
