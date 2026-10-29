package io.github.cocokoniq.fancyir.core.domain.recon

import io.github.cocokoniq.fancyir.core.domain.onboarding.Candidate
import io.github.cocokoniq.fancyir.core.domain.onboarding.Probe
import io.github.cocokoniq.fancyir.core.domain.onboarding.ProbePlanner
import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.irdb.Command
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCodeset
import io.github.cocokoniq.fancyir.core.irdb.catalog.CodesetKind
import io.github.cocokoniq.fancyir.core.irdb.catalog.FunctionKey

/**
 * First-time recon (ARCHITECTURE.md §2.4): fire a harmless probe from the ranked candidates,
 * ask (or later, watch) whether the device reacted, and narrow down with [ProbePlanner] until
 * one code set is both most likely and has been seen working at least once.
 */
class ReconSession(candidates: List<CatalogCodeset>) {

    sealed interface Step {
        data class Fire(val probe: Probe, val label: String, val number: Int) : Step
        data class Found(val codeset: CatalogCodeset, val confidence: Double) : Step
        data object NoMatch : Step
    }

    private val byId = candidates.associateBy { it.id }
    private val probeCodes = candidates.mapNotNull { set -> ReconProbes.probeFor(set)?.let { set.id to it } }.toMap()
    private val planner: ProbePlanner? = if (probeCodes.isEmpty()) null else ProbePlanner(
        candidates = probeCodes.keys.map { id -> Candidate(id, prior = 1.0 / byId.getValue(id).rank.coerceAtLeast(1)) },
        probes = ProbePlanner.probesFor(probeCodes.mapValues { it.value.second }),
    )
    private val confirmed = HashSet<String>()
    private val retests = HashMap<String, Int>()
    private var fired = 0

    val candidateCount: Int get() = probeCodes.size
    val probesFired: Int get() = fired
    val beliefs: Map<String, Double> get() = planner?.beliefs.orEmpty()

    fun next(): Step {
        val planner = planner ?: return Step.NoMatch
        val (bestId, belief) = planner.best()
        if (belief >= RESOLVED && bestId in confirmed) return Step.Found(byId.getValue(bestId), belief)
        if (fired >= MAX_PROBES) return if (bestId in confirmed) Step.Found(byId.getValue(bestId), belief) else Step.NoMatch

        // Prefer the planner's most informative probe; once it has nothing left to learn,
        // re-test the favourite directly so a lucky false alarm can't win unconfirmed.
        val probe = planner.nextProbe()
            ?: ProbePlanner.probesFor(mapOf(bestId to probeCodes.getValue(bestId).second)).single()
                .takeIf { bestId !in confirmed && (retests[bestId] ?: 0) < MAX_RETESTS }
                ?.also { retests[bestId] = (retests[bestId] ?: 0) + 1 }
            ?: return if (bestId in confirmed) Step.Found(byId.getValue(bestId), belief) else Step.NoMatch
        val label = probeCodes.getValue(probe.covers.first()).first
        return Step.Fire(probe, label, fired + 1)
    }

    fun answer(probe: Probe, reacted: Boolean) {
        fired++
        planner?.observe(probe, reacted)
        if (reacted) confirmed += probe.covers
    }

    private companion object {
        const val RESOLVED = 0.9
        const val MAX_PROBES = 8
        const val MAX_RETESTS = 2
    }
}

/** Non-disruptive probe commands per category: never POWER unless nothing else exists. */
object ReconProbes {

    private val chains = mapOf(
        DeviceCategory.TV to listOf(Command.MUTE, Command.VOLUME_UP, Command.INFO, Command.MENU),
        DeviceCategory.MONITOR to listOf(Command.MENU, Command.INPUT, Command.VOLUME_UP),
        DeviceCategory.PROJECTOR to listOf(Command.MENU, Command.INFO, Command.VOLUME_UP),
        DeviceCategory.SOUNDBAR to listOf(Command.MUTE, Command.VOLUME_UP, Command.INPUT),
        DeviceCategory.AV_RECEIVER to listOf(Command.MUTE, Command.VOLUME_UP, Command.INPUT),
        DeviceCategory.SPEAKER to listOf(Command.MUTE, Command.VOLUME_UP),
        DeviceCategory.FAN to listOf(Command.OSCILLATE, Command.SPEED_UP, Command.FAN_SPEED, Command.TIMER),
        DeviceCategory.LIGHT to listOf(Command.BRIGHTNESS_UP, Command.MODE, Command.POWER_ON),
        DeviceCategory.SET_TOP_BOX to listOf(Command.INFO, Command.GUIDE, Command.MENU),
        DeviceCategory.STREAMING_DEVICE to listOf(Command.HOME, Command.MENU, Command.UP),
        DeviceCategory.MEDIA_PLAYER to listOf(Command.INFO, Command.MENU, Command.PAUSE),
    )
    private val fallback = listOf(Command.MODE, Command.SPEED_UP, Command.TIMER, Command.POWER)

    /** (label, code) of the probe to fire for [codeset], or null if it has nothing safe. */
    fun probeFor(codeset: CatalogCodeset): Pair<String, IrCode>? {
        if (codeset.kind == CodesetKind.CLIMATE) {
            // An AC beeps when it accepts a full state: send a mild cooling state, or "off" as a last resort.
            val preferred = listOf("cool:auto:24", "cool:auto:25", "cool:low:24").map { FunctionKey.climate(it.split(':')) }
            val command = preferred.firstNotNullOfOrNull { key -> codeset.commands.firstOrNull { it.function == key } }
                ?: codeset.commands.firstOrNull { FunctionKey.isClimate(it.function) && it.function != FunctionKey.CLIMATE_OFF }
                ?: codeset.commands.firstOrNull { it.function == FunctionKey.CLIMATE_OFF }
                ?: return null
            return "AC STATE" to command.code
        }
        val codes = codeset.toCodeset()
        val chain = chains[codeset.category].orEmpty() + fallback
        val (command, code) = codes.firstAvailable(chain) ?: return null
        return command.name.replace('_', ' ') to code
    }
}
