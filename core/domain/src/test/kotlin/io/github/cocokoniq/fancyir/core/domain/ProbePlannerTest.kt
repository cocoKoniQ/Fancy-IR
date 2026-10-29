package io.github.cocokoniq.fancyir.core.domain

import io.github.cocokoniq.fancyir.core.domain.onboarding.Candidate
import io.github.cocokoniq.fancyir.core.domain.onboarding.ProbePlanner
import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.IrProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProbePlannerTest {

    // Four code-set families; A and B share the same MUTE code.
    private val mute = mapOf(
        "A" to IrCode.Parsed(IrProtocol.SAMSUNG32, 0x07, 0x0F),
        "B" to IrCode.Parsed(IrProtocol.SAMSUNG32, 0x07, 0x0F),
        "C" to IrCode.Parsed(IrProtocol.NEC, 0x07, 0x0F),
        "D" to IrCode.Parsed(IrProtocol.NEC_EXT, 0x0707, 0xF00F),
    )
    private val info = mapOf(
        "A" to IrCode.Parsed(IrProtocol.SAMSUNG32, 0x07, 0x1F),
        "B" to IrCode.Parsed(IrProtocol.SAMSUNG32, 0x07, 0x1E),
    )
    private val candidates = listOf(Candidate("A", 4.0), Candidate("B", 3.0), Candidate("C", 2.0), Candidate("D", 1.0))

    @Test
    fun `identical codes share one probe`() {
        val probes = ProbePlanner.probesFor(mute)
        assertEquals(3, probes.size)
        assertEquals(setOf("A", "B"), probes.single { it.covers.size == 2 }.covers)
    }

    @Test
    fun `converges on the true family within a few probes`() {
        for (truth in listOf("A", "B", "C", "D")) {
            val planner = ProbePlanner(candidates, ProbePlanner.probesFor(mute) + ProbePlanner.probesFor(info))
            var fired = 0
            while (!planner.isResolved() && fired < 8) {
                val probe = planner.nextProbe() ?: break
                planner.observe(probe, reacted = truth in probe.covers)
                fired++
            }
            assertEquals(truth, planner.best().first, "truth=$truth beliefs=${planner.beliefs}")
            assertTrue(fired <= 6, "truth=$truth took $fired probes")
        }
    }

    @Test
    fun `the first probe is the most informative shared one`() {
        val planner = ProbePlanner(candidates, ProbePlanner.probesFor(mute))
        assertEquals(setOf("A", "B"), planner.nextProbe()?.covers)
    }
}
