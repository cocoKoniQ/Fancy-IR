package io.github.cocokoniq.fancyir.core.irdb.catalog

import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.IrProtocol
import io.github.cocokoniq.fancyir.core.irdb.Command
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.FlipperIrParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogBundleTest {

    private val tv = CatalogCodeset(
        id = "starter:lg", kind = CodesetKind.COMMANDS, category = DeviceCategory.TV, brand = "LG",
        label = "LG TV\twith tab", rank = 1, source = CatalogSource.STARTER, sourceRef = "lg_tv.ir", license = "MIT",
        commands = listOf(
            CatalogCommand("POWER", IrCode.Parsed(IrProtocol.NEC, 0x04, 0x08)),
            CatalogCommand("custom:Netflix", IrCode.Raw(38_000, 0.33f, intArrayOf(9000, 4500, 560))),
        ),
    )
    private val ac = CatalogCodeset(
        id = "smartir:climate:1000", kind = CodesetKind.CLIMATE, category = DeviceCategory.AIR_CONDITIONER,
        brand = "Toyotomi", label = "AKIRA", rank = 2, source = CatalogSource.SMARTIR, sourceRef = "codes/climate/1000.json",
        license = "MIT", commands = listOf(CatalogCommand("climate:cool:auto:24", IrCode.Raw(38_000, 0.33f, intArrayOf(3000, 1500, 500)))),
        climate = ClimateSpec(16, 30, 1f, listOf("cool", "heat"), listOf("auto", "low")),
    )

    @Test
    fun `bundle round-trips code sets, raw timings and climate specs`() {
        val text = buildString { CatalogBundle.write(sequenceOf(tv, ac), this, comment = "generated in test") }

        val read = CatalogBundle.read(text.reader().buffered()).toList()

        assertEquals(listOf(tv.copy(label = "LG TV with tab"), ac), read)
    }

    @Test
    fun `codesets expose semantic commands and keep the custom tail`() {
        val codeset = tv.toCodeset()
        assertEquals(IrCode.Parsed(IrProtocol.NEC, 0x04, 0x08), codeset[Command.POWER])
        assertEquals(setOf("custom:Netflix"), codeset.custom.keys)
        assertEquals("Netflix", FunctionKey.displayName("custom:Netflix"))
    }

    @Test
    fun `corrupt bundles fail with a line number`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CatalogBundle.read("#fancyir-catalog 1\nP\tPOWER\tNEC\t4\t8\n".reader().buffered()).toList()
        }
        assertTrue("line 2" in error.message.orEmpty())
    }

    @Test
    fun `families group remotes that share their core codes and rank by size`() {
        fun file(power: Int, extra: String = "") = FlipperIrParser.parse(
            "name: Power\ntype: parsed\nprotocol: NEC\naddress: 04 00 00 00\ncommand: ${"%02X".format(power)} 00 00 00\n$extra",
        )
        val members = listOf(
            "TVs/LG/LG_A.ir" to file(0x08),
            "TVs/LG/LG_B.ir" to file(0x08, "name: Mute\ntype: parsed\nprotocol: NEC\naddress: 04 00 00 00\ncommand: 09 00 00 00\n"),
            "TVs/LG/LG_C.ir" to file(0x10),
        )

        val ranked = FlipperCatalog.rankedCodesets(members, maxFamilies = 5)

        assertEquals(2, ranked.size)
        assertEquals(1, ranked[0].rank)
        assertEquals("TVs/LG/LG_B.ir", ranked[0].sourceRef, "representative has the most buttons")
        assertTrue("+1 similar" in ranked[0].label)
        assertEquals(DeviceCategory.TV, ranked[0].category)
        assertEquals("LG", ranked[0].brand)
    }

    @Test
    fun `brand keys ignore case and separators`() {
        assertEquals(BrandKey.of("Mitsubishi_Electric"), BrandKey.of("mitsubishi electric"))
    }
}
