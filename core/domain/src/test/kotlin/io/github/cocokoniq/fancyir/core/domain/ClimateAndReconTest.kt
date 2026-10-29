package io.github.cocokoniq.fancyir.core.domain

import io.github.cocokoniq.fancyir.core.domain.climate.ClimateController
import io.github.cocokoniq.fancyir.core.domain.climate.ClimateIntent
import io.github.cocokoniq.fancyir.core.domain.recon.ReconProbes
import io.github.cocokoniq.fancyir.core.domain.recon.ReconSession
import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.IrProtocol
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCodeset
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCommand
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogSource
import io.github.cocokoniq.fancyir.core.irdb.catalog.ClimateSpec
import io.github.cocokoniq.fancyir.core.irdb.catalog.CodesetKind
import io.github.cocokoniq.fancyir.core.irdb.catalog.FunctionKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClimateAndReconTest {

    private val spec = ClimateSpec(16, 30, 1f, modes = listOf("cool", "heat", "fan_only"), fanModes = listOf("low", "auto"))
    private val keys = listOf(FunctionKey.CLIMATE_OFF, "climate:cool:auto:24", "climate:cool:auto:25", "climate:cool:low:24",
        "climate:heat:auto:20", "climate:heat:auto:22", "climate:fan_only:auto")

    @Test
    fun `temperature steps produce whole-state codes`() {
        val controller = ClimateController(spec, keys)
        var state = controller.initial()
        assertEquals(FunctionKey.CLIMATE_OFF, controller.functionFor(state))

        state = controller.apply(state, ClimateIntent.TEMP_UP)

        assertEquals(true, state.power)
        assertEquals("climate:cool:auto:25", controller.functionFor(state))
    }

    @Test
    fun `missing states fall back to the nearest temperature or a temperature-less code`() {
        val controller = ClimateController(spec, keys)
        val heat = controller.initial().copy(power = true, mode = "heat", tempC = 23)
        assertEquals("climate:heat:auto:22", controller.functionFor(heat))
        val fan = heat.copy(mode = "fan_only")
        assertEquals("climate:fan_only:auto", controller.functionFor(fan))
    }

    @Test
    fun `mode and fan cycle`() {
        val controller = ClimateController(spec, keys)
        val state = controller.initial()
        assertEquals("heat", controller.apply(state, ClimateIntent.NEXT_MODE).mode)
        assertEquals("low", controller.apply(state, ClimateIntent.NEXT_FAN).fan)
    }

    private fun tv(id: String, rank: Int, mute: Long) = CatalogCodeset(
        id = id, kind = CodesetKind.COMMANDS, category = DeviceCategory.TV, brand = "LG", label = id, rank = rank,
        source = CatalogSource.FLIPPER_IRDB, sourceRef = id, license = "CC0-1.0",
        commands = listOf(
            CatalogCommand("POWER", IrCode.Parsed(IrProtocol.NEC, 0x04, 0x08)),
            CatalogCommand("MUTE", IrCode.Parsed(IrProtocol.NEC, 0x04, mute)),
        ),
    )

    @Test
    fun `recon never probes with power and finds the reacting code set`() {
        val candidates = listOf(tv("a", 1, 0x09), tv("b", 2, 0x0A), tv("c", 3, 0x0B))
        assertEquals("MUTE", ReconProbes.probeFor(candidates[0])?.first)

        val session = ReconSession(candidates)
        var step = session.next()
        while (step is ReconSession.Step.Fire) {
            val reacted = "b" in step.probe.covers
            session.answer(step.probe, reacted)
            step = session.next()
        }

        val found = assertIs<ReconSession.Step.Found>(step)
        assertEquals("b", found.codeset.id)
        assertTrue(session.probesFired <= 4, "took ${session.probesFired} probes")
    }

    @Test
    fun `a single candidate must react at least once`() {
        val session = ReconSession(listOf(tv("only", 1, 0x09)))
        repeat(3) {
            val step = session.next()
            if (step is ReconSession.Step.Fire) session.answer(step.probe, reacted = false)
        }
        assertIs<ReconSession.Step.NoMatch>(session.next())
    }

    @Test
    fun `climate code sets probe with a full state`() {
        val ac = CatalogCodeset("ac", CodesetKind.CLIMATE, DeviceCategory.AIR_CONDITIONER, "Daikin", "ac", 1, CatalogSource.SMARTIR,
            "ref", "MIT", listOf(CatalogCommand("climate:cool:auto:24", IrCode.Raw(38_000, 0.33f, intArrayOf(3000, 1500, 500)))), spec)
        assertEquals("AC STATE", ReconProbes.probeFor(ac)?.first)
    }
}
