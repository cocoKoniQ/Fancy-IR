package io.github.cocokoniq.fancyir.core.irdb

import io.github.cocokoniq.fancyir.core.ir.IrCode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the hand-written starter sets to the canonical 32-bit words that IR decoders and LIRC
 * configs print for these TVs (transmission order, MSB first), e.g. Samsung power 0xE0E040BF.
 */
class StarterLibraryTest {

    private fun reversed(byte: Long): Long = (0 until 8).fold(0L) { acc, i -> acc or (((byte shr i) and 1L) shl (7 - i)) }

    /** NEC-family word as decoders print it: each byte bit-reversed, in send order. */
    private fun necWord(first: Long, second: Long, command: Long): Long =
        (reversed(first) shl 24) or (reversed(second) shl 16) or (reversed(command) shl 8) or reversed(command.inv() and 0xFF)

    private fun codes(id: String) = Codeset.from(StarterLibrary.load(StarterLibrary.entries.first { it.id == id }))

    @Test
    fun `samsung starter matches the 0xE0E0 command words`() {
        val expected = mapOf(
            Command.POWER to 0xE0E040BFL, Command.POWER_OFF to 0xE0E019E6L, Command.POWER_ON to 0xE0E09966L,
            Command.VOLUME_UP to 0xE0E0E01FL, Command.VOLUME_DOWN to 0xE0E0D02FL, Command.MUTE to 0xE0E0F00FL,
            Command.CHANNEL_UP to 0xE0E048B7L, Command.CHANNEL_DOWN to 0xE0E008F7L, Command.INPUT to 0xE0E0807FL,
            Command.MENU to 0xE0E058A7L, Command.UP to 0xE0E006F9L, Command.DOWN to 0xE0E08679L,
            Command.LEFT to 0xE0E0A659L, Command.RIGHT to 0xE0E046B9L, Command.OK to 0xE0E016E9L,
            Command.BACK to 0xE0E01AE5L, Command.HOME to 0xE0E09E61L, Command.INFO to 0xE0E0F807L,
            Command.EXIT to 0xE0E0B44BL, Command.DIGIT_1 to 0xE0E020DFL, Command.DIGIT_0 to 0xE0E08877L,
        )
        val codeset = codes("starter-samsung-tv")
        for ((command, word) in expected) {
            val code = codeset[command] as IrCode.Parsed
            assertEquals(word, necWord(code.address, code.address, code.command), "$command")
        }
    }

    @Test
    fun `lg starter matches the 0x20DF command words`() {
        val expected = mapOf(
            Command.POWER to 0x20DF10EFL, Command.VOLUME_UP to 0x20DF40BFL,
            Command.VOLUME_DOWN to 0x20DFC03FL, Command.MUTE to 0x20DF906FL,
        )
        val codeset = codes("starter-lg-tv")
        for ((command, word) in expected) {
            val code = codeset[command] as IrCode.Parsed
            assertEquals(word, necWord(code.address, code.address.inv() and 0xFF, code.command), "$command")
        }
    }

    @Test
    fun `sony starter power is the 12-bit 0xA90 word`() {
        val code = codes("starter-sony-tv")[Command.POWER] as IrCode.Parsed
        // 7 command bits then 5 address bits, sent LSB first; decoders print them MSB first.
        val sent = (0 until 7).map { (code.command shr it) and 1L } + (0 until 5).map { (code.address shr it) and 1L }
        assertEquals(0xA90L, sent.fold(0L) { acc, bit -> (acc shl 1) or bit })
    }
}
