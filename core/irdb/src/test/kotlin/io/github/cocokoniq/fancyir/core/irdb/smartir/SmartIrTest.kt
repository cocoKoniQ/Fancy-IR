package io.github.cocokoniq.fancyir.core.irdb.smartir

import io.github.cocokoniq.fancyir.core.irdb.Command
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.catalog.CodesetKind
import io.github.cocokoniq.fancyir.core.irdb.catalog.FunctionKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartIrTest {

    private val code = "JgAMAAABKZEXNBgQFxEXEA=="

    private val climateJson = """
        {
          "manufacturer": "Toyotomi",
          "supportedModels": ["AKIRA GAN/GAG-A128 VL"],
          "supportedController": "Broadlink",
          "commandsEncoding": "Base64",
          "minTemperature": 16, "maxTemperature": 30, "precision": 1,
          "operationModes": ["heat", "cool"],
          "fanModes": ["low", "auto"],
          "commands": {
            "off": "$code",
            "cool": { "auto": { "24": "$code", "25": "$code" } },
            "heat": { "low": { "20": "$code" } }
          }
        }
    """.trimIndent()

    @Test
    fun `climate files become state-keyed tables`() {
        val set = assertNotNull(SmartIr.parse(climateJson, SmartIrPlatform.CLIMATE, "1000", DeviceCategory.AIR_CONDITIONER, rank = 1))

        assertEquals(CodesetKind.CLIMATE, set.kind)
        assertEquals("Toyotomi", set.brand)
        assertEquals(listOf("heat", "cool"), set.climate?.modes)
        val functions = set.commands.map { it.function }.toSet()
        assertEquals(setOf(FunctionKey.CLIMATE_OFF, "climate:cool:auto:24", "climate:cool:auto:25", "climate:heat:low:20"), functions)
        assertEquals("codes/climate/1000.json", set.sourceRef)
    }

    @Test
    fun `media player names normalise and nested sources stay custom`() {
        val media = """
            { "manufacturer": "Philips", "supportedModels": ["26PFL560H"], "supportedController": "Broadlink",
              "commandsEncoding": "Base64",
              "commands": { "off": "$code", "volumeUp": "$code", "previousChannel": "$code", "sources": { "HDMI 1": "$code" } } }
        """.trimIndent()

        val set = assertNotNull(SmartIr.parse(media, SmartIrPlatform.MEDIA_PLAYER, "1000", DeviceCategory.TV, rank = 1))
        val codeset = set.toCodeset()

        assertNotNull(codeset[Command.POWER_OFF])
        assertNotNull(codeset[Command.VOLUME_UP])
        assertNotNull(codeset[Command.CHANNEL_DOWN])
        assertTrue("custom:sources:HDMI 1" in codeset.custom)
    }

    @Test
    fun `unsupported encodings are skipped`() {
        val xiaomi = """{ "manufacturer": "X", "supportedController": "Xiaomi", "commandsEncoding": "Raw", "commands": { "off": "abc" } }"""
        assertNull(SmartIr.parse(xiaomi, SmartIrPlatform.CLIMATE, "1406", DeviceCategory.AIR_CONDITIONER, rank = 1))
    }

    @Test
    fun `docs index tables parse manufacturer rows`() {
        val markdown = """
            #### Panasonic
            | Code                               | Supported Models                     | Controller |
            | ---------------------------------- | ------------------------------------ | ---------- |
            | [1020](../codes/climate/1020.json) | CS-CE7HKEW<br>CS-CE9HKEW             | Broadlink  |
            | [1030](../codes/climate/1030.json) | CS-E12JKDW <b>(Swing mode)</b>	     | Broadlink  |

            #### General Electric
            | [1100](../codes/climate/1100.json) | ASW-H09A3/LOR1                       | Xiaomi     |
        """.trimIndent()

        val rows = SmartIr.parseIndex(markdown)

        assertEquals(listOf("1020", "1030", "1100"), rows.map { it.code })
        assertEquals(listOf("CS-CE7HKEW", "CS-CE9HKEW"), rows[0].models)
        assertEquals("CS-E12JKDW (Swing mode)", rows[1].models.single())
        assertEquals("General Electric", rows[2].manufacturer)
    }

    @Test
    fun `fan pages without outer pipes and with a notes column parse too`() {
        val markdown = """
            #### Atomberg

            | Code | Supported Models | Notes |Controller |
            | ------------- | ----- | ----- | ------------- |
            [1160](../codes/fan/1160.json)|Efficio||Broadlink
            [1170](../codes/fan/1170.json)|Renesa|Speeds `1,2,3` are mapped|Broadlink
        """.trimIndent()

        val rows = SmartIr.parseIndex(markdown)

        assertEquals(listOf("1160", "1170"), rows.map { it.code })
        assertEquals(listOf("Broadlink", "Broadlink"), rows.map { it.controller })
        assertEquals(listOf("Renesa"), rows[1].models)
    }
}
