package io.github.cocokoniq.fancyir.core.domain

import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import io.github.cocokoniq.fancyir.core.domain.targeting.LockSource
import io.github.cocokoniq.fancyir.core.domain.targeting.TargetObservation
import io.github.cocokoniq.fancyir.core.domain.targeting.TargetingMachine
import io.github.cocokoniq.fancyir.core.domain.targeting.TargetingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TargetingMachineTest {

    private val tv = DeviceId("tv")
    private val soundbar = DeviceId("soundbar")
    private val machine = TargetingMachine()
    private var t = 0L

    private fun feed(ms: Long, scores: Map<DeviceId, Float>, salient: Boolean = false, visual: Boolean = true): TargetingState {
        var last: TargetingState = machine.state
        val end = t + ms
        while (t < end) {
            t += 33
            last = machine.onObservation(TargetObservation(t, scores, salient, visual))
        }
        return last
    }

    @Test
    fun `a clear match acquires then locks`() {
        val early = machine.onObservation(TargetObservation(33, mapOf(tv to 0.95f)))
        t = 33
        assertIs<TargetingState.Acquiring>(early)

        val state = feed(600, mapOf(tv to 0.95f))

        assertEquals(TargetingState.Locked(tv, (state as TargetingState.Locked).confidence, LockSource.VISUAL), state)
    }

    @Test
    fun `two similar candidates never lock`() {
        val state = feed(1_000, mapOf(tv to 0.9f, soundbar to 0.85f))

        assertIs<TargetingState.Acquiring>(state)
    }

    @Test
    fun `lock survives a short dropout and releases after a long one`() {
        feed(600, mapOf(tv to 0.95f))

        assertIs<TargetingState.Locked>(feed(400, emptyMap()))
        assertEquals(TargetingState.Scanning, feed(1_200, emptyMap()))
    }

    @Test
    fun `lock is frozen while a finger is down`() {
        feed(600, mapOf(tv to 0.95f))
        machine.interactionActive = true

        val state = feed(2_000, mapOf(soundbar to 0.95f))

        assertEquals(tv, (state as TargetingState.Locked).device)
    }

    @Test
    fun `a clearly better device takes over the lock`() {
        feed(600, mapOf(tv to 0.95f))

        val state = feed(1_000, mapOf(soundbar to 0.95f))

        assertEquals(soundbar, (state as TargetingState.Locked).device)
    }

    @Test
    fun `prior-only scores lock as predicted`() {
        val state = feed(600, mapOf(tv to 0.9f), visual = false)

        assertEquals(LockSource.PREDICTED, (state as TargetingState.Locked).source)
    }

    @Test
    fun `salient but unknown target becomes unrecognized`() {
        assertEquals(TargetingState.Scanning, feed(300, emptyMap(), salient = true))
        assertEquals(TargetingState.Unrecognized, feed(600, emptyMap(), salient = true))
    }

    @Test
    fun `manual lock persists without evidence`() {
        machine.lockManually(tv)

        val state = feed(5_000, emptyMap())

        assertTrue(state is TargetingState.Locked && state.source == LockSource.MANUAL)
    }
}
