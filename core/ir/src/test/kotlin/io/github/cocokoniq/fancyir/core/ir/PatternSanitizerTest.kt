package io.github.cocokoniq.fancyir.core.ir

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatternSanitizerTest {

    @Test
    fun `zero-length mark merges the spaces around it and the trailing space is trimmed`() {
        assertContentEquals(intArrayOf(500, 1000, 500), PatternSanitizer.sanitize(intArrayOf(500, 500, 0, 500, 500, 700)))
    }

    @Test
    fun `negative slices are dropped`() {
        assertContentEquals(intArrayOf(300), PatternSanitizer.sanitize(intArrayOf(100, -5, 200)))
    }

    @Test
    fun `patterns over the 2 s limit are split inside spaces`() {
        val press = ProtocolEncoders.encode(IrProtocol.NEC, 0x04, 0x02).press()
        val timings = IntArray(press.size * 30) { press[it % press.size] }
        val pattern = IrPattern(38_000, PatternSanitizer.trimTrailingSpace(timings))
        assertTrue(pattern.durationUs > PatternSanitizer.MAX_TRANSMIT_US)

        val chunks = PatternSanitizer.split(pattern)

        assertTrue(chunks.size >= 2)
        for (chunk in chunks) {
            assertTrue(chunk.durationUs <= PatternSanitizer.MAX_TRANSMIT_US, "chunk too long: $chunk")
            assertEquals(1, chunk.timingsUs.size % 2, "chunk must end with a mark")
        }
        val marksBefore = pattern.timingsUs.filterIndexed { i, _ -> i % 2 == 0 }.sum()
        val marksAfter = chunks.sumOf { c -> c.timingsUs.filterIndexed { i, _ -> i % 2 == 0 }.sum() }
        assertEquals(marksBefore, marksAfter, "no emitted time is lost")
    }

    @Test
    fun `short patterns pass through untouched`() {
        val pattern = IrPattern(38_000, intArrayOf(9000, 4500, 560))
        assertEquals(listOf(pattern), PatternSanitizer.split(pattern))
    }
}
