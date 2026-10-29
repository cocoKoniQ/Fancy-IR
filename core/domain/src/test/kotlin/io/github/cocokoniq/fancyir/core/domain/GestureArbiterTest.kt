package io.github.cocokoniq.fancyir.core.domain

import io.github.cocokoniq.fancyir.core.domain.gesture.Direction
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureArbiter
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureConfig
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureEvent
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureEvent.*
import io.github.cocokoniq.fancyir.core.domain.gesture.PointerInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GestureArbiterTest {

    private val config = GestureConfig.forDensity(1f)
    private val arbiter = GestureArbiter(config)

    private fun run(vararg inputs: PointerInput): List<GestureEvent> = inputs.flatMap { arbiter.onInput(it) }

    private fun down(x: Float = 500f, y: Float = 1000f, t: Long = 0) = PointerInput.Down(x, y, t)
    private fun move(x: Float, y: Float, t: Long) = PointerInput.Move(x, y, t)
    private fun up(t: Long) = PointerInput.Up(t)
    private fun frame(t: Long) = PointerInput.Frame(t)

    @Test
    fun `quick press and release is a tap`() {
        assertEquals(listOf(Began, Tap, Ended), run(down(), up(120)))
    }

    @Test
    fun `vertical drag dials one step on slop then one per detent`() {
        val events = run(down(), move(500f, 990f, 16), move(500f, 950f, 32), move(500f, 905f, 48), up(64))

        assertEquals(listOf(Began, Dial(1), Dial(1), Dial(1), Ended), events)
    }

    @Test
    fun `dragging back reverses the dial`() {
        val events = run(down(), move(500f, 990f, 16), move(500f, 950f, 32), move(500f, 1075f, 48), up(64))

        assertEquals(1 + 1 - 3, events.filterIsInstance<Dial>().sumOf { it.steps })
    }

    @Test
    fun `horizontal drag steps`() {
        val events = run(down(), move(512f, 1001f, 16), move(580f, 1002f, 32), up(48))

        assertEquals(listOf(Step(1), Step(1)), events.filterIsInstance<Step>())
    }

    @Test
    fun `holding still charges power and releasing fires it`() {
        val events = run(down(), frame(200), frame(config.holdMs), frame(config.holdMs + config.chargeMs), up(1_000))

        assertTrue(HoldStarted(500f, 1000f) in events)
        assertTrue(Charged in events)
        assertEquals(PowerReleased, events[events.lastIndex - 1])
    }

    @Test
    fun `releasing before the charge completes fizzles`() {
        val events = run(down(), frame(config.holdMs), frame(config.holdMs + 200), up(700))

        assertEquals(listOf(Fizzled, Ended), events.takeLast(2))
    }

    @Test
    fun `sliding out of a hold selects a radial slot`() {
        val events = run(down(), frame(config.holdMs), move(600f, 1005f, 400), up(450))

        assertTrue(RadialHover(Direction.RIGHT) in events)
        assertEquals(RadialSelected(Direction.RIGHT), events[events.lastIndex - 1])
        assertTrue(events.none { it is Dial || it is Step })
    }

    @Test
    fun `returning to the centre after arming cancels the selection`() {
        val events = run(down(), frame(config.holdMs), move(500f, 900f, 400), move(500f, 1000f, 450), up(2_000))

        assertEquals(listOf(Fizzled, Ended), events.takeLast(2))
    }

    @Test
    fun `nav mode turns drags into d-pad flicks`() {
        arbiter.navMode = true
        val events = run(down(), move(520f, 1000f, 16), move(600f, 1000f, 32), up(48))

        assertEquals(listOf(Flick(Direction.RIGHT), Flick(Direction.RIGHT)), events.filterIsInstance<Flick>())
    }

    @Test
    fun `a second finger cancels the gesture`() {
        assertEquals(listOf(Began, Fizzled, Ended), run(down(), frame(config.holdMs), down(t = 400)).filterNot { it is Charging || it is HoldStarted })
    }
}
