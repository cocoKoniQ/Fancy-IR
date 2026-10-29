package io.github.cocokoniq.fancyir.core.irdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandVocabularyTest {

    @Test
    fun `common spellings normalise to the same command`() {
        val cases = listOf(
            Command.POWER to listOf("Power", "POWER", "Pwr", "power_toggle", "On/Off"),
            Command.VOLUME_UP to listOf("Vol_up", "VOL+", "Volume Up", "vol-up", "Volume_Up_2"),
            Command.VOLUME_DOWN to listOf("Vol_dn", "VOL-", "Volume_Down", "vol -"),
            Command.CHANNEL_UP to listOf("Ch_next", "CH+", "Channel_Up", "P+"),
            Command.CHANNEL_DOWN to listOf("Ch_prev", "CH-", "channel down"),
            Command.INPUT to listOf("Input", "SOURCE", "Src"),
            Command.OK to listOf("Ok", "ENTER", "Select"),
            Command.BACK to listOf("Back", "Return"),
            Command.PLAY_PAUSE to listOf("Play/Pause", "PlayPause"),
            Command.TEMP_UP to listOf("Temp_up", "TEMP+", "Temperature Up"),
            Command.DIGIT_7 to listOf("7", "Num_7", "KEY_7"),
            Command.OSCILLATE to listOf("Oscillate", "Rotate", "OSC"),
            Command.CHANNEL_UP to listOf("CHANNELUP", "nextChannel"),
            Command.CHANNEL_DOWN to listOf("CHANNELDOWN", "previousChannel"),
            Command.VOLUME_UP to listOf("volumeUp"),
            Command.LEFT to listOf("LEFT_ARROW"),
        )
        for ((expected, names) in cases) {
            for (name in names) assertEquals(expected, CommandVocabulary.normalize(name), "'$name'")
        }
    }

    @Test
    fun `unknown and ambiguous names stay unknown`() {
        assertNull(CommandVocabulary.normalize("Cool_hi"))
        assertNull(CommandVocabulary.normalize("Netflix"))
        assertNull(CommandVocabulary.normalize("vol_up_down"))
    }

    @Test
    fun `codeset keeps the first occurrence and the custom long tail`() {
        val file = FlipperIrParser.parse(
            """
            name: Power
            type: parsed
            protocol: NEC
            address: 04 00 00 00
            command: 08 00 00 00
            #
            name: POWER
            type: parsed
            protocol: NEC
            address: 04 00 00 00
            command: 99 00 00 00
            #
            name: Netflix
            type: parsed
            protocol: NEC
            address: 04 00 00 00
            command: 56 00 00 00
            """.trimIndent(),
        )

        val codeset = Codeset.from(file)

        assertEquals("NEC(0x4, 0x8)", codeset[Command.POWER].toString())
        assertEquals(setOf("Netflix"), codeset.custom.keys)
    }

    @Test
    fun `irdb paths yield category, brand and model`() {
        assertEquals(
            IrdbEntry(DeviceCategory.TV, "Samsung", "BN59-01175N"),
            IrdbPath.parse("TVs/Samsung/Samsung_BN59-01175N.ir"),
        )
        assertEquals(DeviceCategory.AIR_CONDITIONER, IrdbPath.parse("ACs/Daikin/Daikin_ARC433.ir")?.category)
        assertEquals(DeviceCategory.OTHER, IrdbPath.parse("Bidet/Toto/Toto_Washlet.ir")?.category)
        assertNull(IrdbPath.parse("README.md"))
    }
}
