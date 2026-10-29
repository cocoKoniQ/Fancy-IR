package io.github.cocokoniq.fancyir.core.irdb

import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.IrProtocol
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FlipperIrParserTest {

    private val sample = """
        Filetype: IR signals file
        Version: 1
        #
        name: Power
        type: parsed
        protocol: NECext
        address: 00 7F 00 00
        command: 15 EA 00 00
        #
        name: Vol_up
        type: raw
        frequency: 38000
        duty_cycle: 0.330000
        data: 9024 4512 564 564
        data: 564 1692 564
    """.trimIndent()

    @Test
    fun `parses header, parsed and raw signals`() {
        val file = FlipperIrParser.parse(sample)

        assertEquals("IR signals file", file.filetype)
        assertEquals(1, file.version)
        assertEquals(2, file.signals.size)
        assertEquals(IrCode.Parsed(IrProtocol.NEC_EXT, address = 0x7F00, command = 0xEA15), file.signals[0].code)
        val raw = assertIs<IrCode.Raw>(file.signals[1].code)
        assertEquals(38_000, raw.carrierHz)
        assertContentEquals(intArrayOf(9024, 4512, 564, 564, 564, 1692, 564), raw.timingsUs, "data lines concatenate")
        assertTrue(file.issues.isEmpty())
    }

    @Test
    fun `bad entries become issues and the rest of the file survives`() {
        val text = """
            name: Broken
            type: parsed
            protocol: NoSuchProtocol
            address: 01 00 00 00
            command: 02 00 00 00
            #
            name: Mute
            type: parsed
            protocol: Samsung32
            address: 07 00 00 00
            command: 0F 00 00 00
        """.trimIndent()

        val file = FlipperIrParser.parse(text)

        assertEquals(listOf("Mute"), file.signals.map { it.name })
        assertEquals(1, file.issues.size)
        assertTrue("NoSuchProtocol" in file.issues.single().message)
    }

    @Test
    fun `protocol names are case-insensitive`() {
        val file = FlipperIrParser.parse("name: x\ntype: parsed\nprotocol: sirc20\naddress: 3F 1A 00 00\ncommand: 7F 00 00 00")

        assertEquals(IrCode.Parsed(IrProtocol.SIRC20, 0x1A3F, 0x7F), file.signals.single().code)
    }

    @Test
    fun `starter library files parse cleanly and encode`() {
        for (entry in StarterLibrary.entries) {
            val file = StarterLibrary.load(entry)
            assertTrue(file.issues.isEmpty(), "${entry.id}: ${file.issues}")
            val codeset = Codeset.from(file)
            for (command in listOf(Command.POWER, Command.VOLUME_UP, Command.VOLUME_DOWN, Command.MUTE, Command.OK)) {
                val code = codeset[command] ?: error("${entry.id} lacks $command")
                code.toSignal()
            }
        }
    }
}
