package io.github.cocokoniq.fancyir.core.data

import androidx.test.core.app.ApplicationProvider
import io.github.cocokoniq.fancyir.core.data.catalog.AssetCatalog
import io.github.cocokoniq.fancyir.core.data.catalog.CandidateLookup
import io.github.cocokoniq.fancyir.core.data.catalog.CatalogAsset
import io.github.cocokoniq.fancyir.core.data.catalog.CatalogRepository
import io.github.cocokoniq.fancyir.core.data.catalog.HybridIrStore
import io.github.cocokoniq.fancyir.core.data.catalog.RemoteCodesetSource
import io.github.cocokoniq.fancyir.core.data.db.FancyIrDatabase
import io.github.cocokoniq.fancyir.core.data.settings.SettingsStore
import io.github.cocokoniq.fancyir.core.data.vault.SpatialVault
import io.github.cocokoniq.fancyir.core.domain.climate.ClimateState
import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import io.github.cocokoniq.fancyir.core.domain.spatial.AimDirection
import io.github.cocokoniq.fancyir.core.domain.spatial.SpatialPrior
import io.github.cocokoniq.fancyir.core.ir.CompiledCommand
import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.IrProtocol
import io.github.cocokoniq.fancyir.core.irdb.Command
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogBundle
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCodeset
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCommand
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogSource
import io.github.cocokoniq.fancyir.core.irdb.catalog.ClimateSpec
import io.github.cocokoniq.fancyir.core.irdb.catalog.CodesetKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DataLayerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = FancyIrDatabase.inMemory(context)
    private val settings = SettingsStore(db)
    private val catalog = CatalogRepository(db, settings)

    @After
    fun close() = db.close()

    private fun tv(id: String, brand: String, rank: Int, power: Long = 0x08, source: CatalogSource = CatalogSource.FLIPPER_IRDB) = CatalogCodeset(
        id = id, kind = CodesetKind.COMMANDS, category = DeviceCategory.TV, brand = brand, label = id, rank = rank,
        source = source, sourceRef = id, license = "CC0-1.0",
        commands = listOf(
            CatalogCommand("POWER", IrCode.Parsed(IrProtocol.NEC, 0x04, power)),
            CatalogCommand("MUTE", IrCode.Parsed(IrProtocol.RC5, 0x00, 0x0D)),
            CatalogCommand("custom:Netflix", IrCode.Raw(38_000, 0.33f, intArrayOf(9000, 4500, 560, 560, 560))),
        ),
    )

    private fun asset(revision: String, vararg sets: CatalogCodeset) = object : CatalogAsset {
        override val revision = revision
        override fun open() = buildString { CatalogBundle.write(sets.asSequence(), this) }.reader().buffered()
    }

    @Test
    fun `bundled catalog seeds once per revision and orders candidates by rank`() = runTest {
        val bundle = asset("r1", tv("lg-2", "LG", 2, power = 0x10), tv("lg-1", "LG", 1), tv("sony", "Sony", 1))

        assertEquals(3, catalog.ensureSeeded(bundle))
        assertEquals(0, catalog.ensureSeeded(bundle), "same revision is a no-op")

        val lg = catalog.candidates(DeviceCategory.TV, "lg")
        assertEquals(listOf("lg-1", "lg-2"), lg.map { it.id })
        assertEquals(tv("lg-1", "LG", 1), lg.first(), "commands round-trip, raw timings included")
        assertEquals(listOf("LG", "Sony"), catalog.brands(DeviceCategory.TV).map { it.name })

        catalog.ensureSeeded(asset("r2", tv("sony", "Sony", 1)))
        assertTrue(catalog.candidates(DeviceCategory.TV, "LG").isEmpty(), "a new revision replaces bundled rows")
    }

    @Test
    fun `remote results are cached after bundled ones and survive re-seeding`() = runTest {
        catalog.ensureSeeded(asset("r1", tv("tcl-bundled", "TCL", 1)))
        var calls = 0
        val remote = RemoteCodesetSource { _, brand ->
            calls++
            listOf(tv("remote-$brand", brand, 1, power = 0x20))
        }
        val store = HybridIrStore(catalog, listOf(remote), settings)

        assertIs<CandidateLookup.Local>(store.candidates(DeviceCategory.TV, "TCL"))
        val fetched = assertIs<CandidateLookup.Fetched>(store.candidates(DeviceCategory.TV, "Hisense"))
        assertEquals(listOf("remote-Hisense"), fetched.codesets.map { it.id })
        assertIs<CandidateLookup.Local>(store.candidates(DeviceCategory.TV, "Hisense"), "cached for next time")
        assertEquals(1, calls)

        catalog.ensureSeeded(asset("r2", tv("tcl-bundled", "TCL", 1)))
        assertEquals(1, catalog.candidates(DeviceCategory.TV, "Hisense").size, "remote cache is kept")

        settings.put(SettingsStore.REMOTE_LOOKUP_ENABLED, "false")
        assertIs<CandidateLookup.Unavailable>(store.candidates(DeviceCategory.TV, "Philips"))
    }

    @Test
    fun `promoting a code set compiles every command and reloads identically`() = runTest {
        val vault = SpatialVault(db)
        val device = vault.promote(tv("lg-1", "LG", 1), label = "Living room TV", roomContext = "wifi:aa")

        val expected = CompiledCommand.compile(IrCode.Parsed(IrProtocol.RC5, 0x00, 0x0D))
        assertEquals(expected, device.compiled["MUTE"])
        assertNotNull(device.compiled["MUTE"]?.togglePress, "toggle twin compiled for RC5")

        val snapshot = SpatialVault(db).load()
        val reloaded = snapshot.devices.single()
        assertEquals("Living room TV", reloaded.name)
        assertEquals(device.compiled, reloaded.compiled)
        assertEquals(IrCode.Parsed(IrProtocol.NEC, 0x04, 0x08), reloaded.codeset[Command.POWER])
        assertTrue("custom:Netflix" in reloaded.codeset.custom)
    }

    @Test
    fun `embeddings are trimmed, anchors replaced and climate state persisted`() = runTest {
        val vault = SpatialVault(db)
        val ac = CatalogCodeset("ac", CodesetKind.CLIMATE, DeviceCategory.AIR_CONDITIONER, "Daikin", "ac", 1, CatalogSource.SMARTIR, "ref", "MIT",
            listOf(CatalogCommand("climate:cool:auto:24", IrCode.Raw(38_000, 0.33f, intArrayOf(3000, 1500, 500)))),
            ClimateSpec(18, 30, 1f, listOf("cool"), listOf("auto")))
        val device = vault.promote(ac, "Bedroom AC", roomContext = null)

        repeat(5) { vault.addEmbedding(device.id, floatArrayOf(it.toFloat(), 1f), meanLuma = 0.4f, keep = 3) }
        vault.saveAnchors(device.id, listOf(SpatialPrior.Anchor(AimDirection(120f, 30f), 4, 0.8f, "wifi:bb")))
        vault.saveAnchors(device.id, listOf(SpatialPrior.Anchor(AimDirection(121f, 31f), 5, 0.9f, "wifi:bb")))
        vault.saveClimateState(device.id, ClimateState(true, "cool", "auto", null, 23))

        val snapshot = SpatialVault(db).load()
        assertEquals(listOf(2f, 3f, 4f), snapshot.embeddings.getValue(device.id).map { it[0] }, "only the newest three kept")
        assertEquals(5, snapshot.anchors.getValue(device.id).single().weight)
        assertEquals(ClimateSpec(18, 30, 1f, listOf("cool"), listOf("auto")), snapshot.devices.single().climate)
        assertEquals(23, vault.climateState(device.id)?.tempC)
        assertEquals(null, vault.climateState(DeviceId("missing")))
    }

    @Test
    fun `the shipped catalog asset parses and seeds`() = runTest {
        val asset = AssetCatalog(context)
        val written = catalog.ensureSeeded(asset)

        assertTrue(written > 50, "seeded $written code sets")
        val samsung = catalog.candidates(DeviceCategory.TV, "Samsung")
        assertEquals(CatalogSource.STARTER, samsung.first().source, "our verified starter ranks first")
        assertTrue(catalog.categories().containsKey(DeviceCategory.AIR_CONDITIONER))
    }
}
