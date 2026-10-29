package io.github.cocokoniq.fancyir.core.domain.onboarding

import io.github.cocokoniq.fancyir.core.ir.IrCode
import kotlin.math.ln

/** A code-set family that might be the right one for the device being identified. */
data class Candidate(val id: String, val prior: Double)

/** One IR signal to fire; [covers] are the candidates that would react if it is theirs. */
data class Probe(val code: IrCode, val covers: Set<String>)

/**
 * Probe & Watch (ARCHITECTURE.md §2.4): Bayesian elimination over candidate code sets. Each
 * probe is chosen to maximise expected information gain, so a signal shared by many families
 * tests them all at once. Observations are noisy: [pDetect] is the chance a reaction is seen
 * when the probe is right, [pFalseAlarm] the chance one is "seen" when it isn't.
 */
class ProbePlanner(
    candidates: List<Candidate>,
    private val probes: List<Probe>,
    private val pDetect: Double = 0.85,
    private val pFalseAlarm: Double = 0.05,
    private val maxUsesPerProbe: Int = 2,
) {
    private val posterior = LinkedHashMap<String, Double>()
    private val uses = HashMap<Probe, Int>()

    init {
        require(candidates.isNotEmpty())
        val total = candidates.sumOf { it.prior }
        candidates.forEach { posterior[it.id] = it.prior / total }
    }

    val beliefs: Map<String, Double> get() = posterior.toMap()

    fun best(): Pair<String, Double> = posterior.maxBy { it.value }.toPair()

    fun isResolved(threshold: Double = 0.9): Boolean = best().second >= threshold

    fun nextProbe(): Probe? {
        val current = entropy(posterior.values)
        return probes
            .filter { (uses[it] ?: 0) < maxUsesPerProbe }
            .map { it to current - expectedEntropyAfter(it) }
            .filter { it.second > 1e-6 }
            .maxByOrNull { it.second }
            ?.first
    }

    fun observe(probe: Probe, reacted: Boolean) {
        uses[probe] = (uses[probe] ?: 0) + 1
        update(posterior, probe, reacted)
    }

    private fun likelihood(candidate: String, probe: Probe, reacted: Boolean): Double {
        val p = if (candidate in probe.covers) pDetect else pFalseAlarm
        return if (reacted) p else 1 - p
    }

    private fun update(beliefs: MutableMap<String, Double>, probe: Probe, reacted: Boolean) {
        var total = 0.0
        for (entry in beliefs.entries) {
            entry.setValue(entry.value * likelihood(entry.key, probe, reacted))
            total += entry.value
        }
        if (total > 0) for (entry in beliefs.entries) entry.setValue(entry.value / total)
    }

    private fun expectedEntropyAfter(probe: Probe): Double {
        val pReact = posterior.entries.sumOf { (id, p) -> p * likelihood(id, probe, reacted = true) }
        var expected = 0.0
        for ((reacted, pOutcome) in listOf(true to pReact, false to 1 - pReact)) {
            if (pOutcome <= 0) continue
            val after = LinkedHashMap(posterior).also { update(it, probe, reacted) }
            expected += pOutcome * entropy(after.values)
        }
        return expected
    }

    private fun entropy(values: Collection<Double>): Double =
        -values.filter { it > 0 }.sumOf { it * ln(it) }

    companion object {
        /** Groups candidates whose probe command has the identical code into one shared probe. */
        fun probesFor(codes: Map<String, IrCode>): List<Probe> =
            codes.entries.groupBy({ it.value }, { it.key }).map { (code, ids) -> Probe(code, ids.toSet()) }
    }
}
