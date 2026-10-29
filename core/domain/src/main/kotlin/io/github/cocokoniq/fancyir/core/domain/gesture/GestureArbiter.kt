package io.github.cocokoniq.fancyir.core.domain.gesture

import kotlin.math.hypot

/** Platform-neutral pointer stream. The UI layer forwards only the first pointer. */
sealed interface PointerInput {
    val timeMs: Long

    data class Down(val x: Float, val y: Float, override val timeMs: Long) : PointerInput
    data class Move(val x: Float, val y: Float, override val timeMs: Long) : PointerInput
    data class Up(override val timeMs: Long) : PointerInput

    /** A second finger, the system taking the gesture, or the HUD losing focus. */
    data class Cancel(override val timeMs: Long) : PointerInput

    /** Display-frame clock tick, so holds charge while the finger is perfectly still. */
    data class Frame(override val timeMs: Long) : PointerInput
}

sealed interface GestureEvent {
    /** Finger down: the targeting lock freezes until [Ended]. */
    data object Began : GestureEvent
    data object Ended : GestureEvent

    data object Tap : GestureEvent

    /** Vertical detents; positive = up. */
    data class Dial(val steps: Int) : GestureEvent

    /** Horizontal detents; positive = right. */
    data class Step(val steps: Int) : GestureEvent

    /** NAV layer: each detent of a drag is one D-pad press. */
    data class Flick(val direction: Direction) : GestureEvent

    data class HoldStarted(val x: Float, val y: Float) : GestureEvent
    data class Charging(val progress: Float) : GestureEvent
    data object Charged : GestureEvent
    data class RadialHover(val slot: Direction?) : GestureEvent
    data class RadialSelected(val slot: Direction) : GestureEvent
    data object PowerReleased : GestureEvent

    /** A hold released without charge or selection: nothing is sent. */
    data object Fizzled : GestureEvent
}

data class GestureConfig(
    val touchSlopPx: Float,
    val dialStepPx: Float,
    val stepStepPx: Float,
    val navStepPx: Float,
    val radialArmPx: Float,
    val holdMs: Long = 350,
    val chargeMs: Long = 600,
) {
    companion object {
        /** The DESIGN.md §3.1 sizes in dp, scaled by [density]. */
        fun forDensity(density: Float) = GestureConfig(
            touchSlopPx = 8 * density,
            dialStepPx = 40 * density,
            stepStepPx = 64 * density,
            navStepPx = 72 * density,
            radialArmPx = 56 * density,
        )
    }
}

/**
 * The four-verb grammar of DESIGN.md §3: TAP, DIAL (vertical drag), STEP (horizontal drag)
 * and HOLD (charge POWER at the centre, or slide out to pick from the radial deck).
 *
 * A pure state machine over [PointerInput], so it can be tested with synthetic traces and has
 * no conflicts between competing platform gesture detectors.
 */
class GestureArbiter(private val config: GestureConfig) {

    /** In the NAV layer drags become D-pad flicks (DESIGN.md §3.4). */
    var navMode: Boolean = false

    private enum class Axis { VERTICAL, HORIZONTAL }

    private sealed interface Phase {
        data object Idle : Phase
        class Pressed(val x0: Float, val y0: Float, val t0: Long) : Phase
        class Dragging(val axis: Axis, val anchor: Float, var emitted: Int) : Phase
        class Holding(val x0: Float, val y0: Float, val since: Long) : Phase {
            var progress = 0f
            var charged = false
            var armed = false
            var hover: Direction? = null
        }
    }

    private var phase: Phase = Phase.Idle

    fun onInput(input: PointerInput): List<GestureEvent> {
        val out = ArrayList<GestureEvent>(2)
        when (input) {
            is PointerInput.Down -> {
                if (phase != Phase.Idle) {
                    cancel(out)
                } else {
                    phase = Phase.Pressed(input.x, input.y, input.timeMs)
                    out += GestureEvent.Began
                }
            }
            is PointerInput.Move -> onMove(input, out)
            is PointerInput.Frame -> onTime(input.timeMs, out)
            is PointerInput.Up -> onUp(input.timeMs, out)
            is PointerInput.Cancel -> cancel(out)
        }
        return out
    }

    private fun onMove(input: PointerInput.Move, out: MutableList<GestureEvent>) {
        when (val p = phase) {
            is Phase.Pressed -> {
                val dx = input.x - p.x0
                val dy = input.y - p.y0
                if (hypot(dx, dy) > config.touchSlopPx) {
                    val axis = if (kotlin.math.abs(dy) >= kotlin.math.abs(dx)) Axis.VERTICAL else Axis.HORIZONTAL
                    val anchor = if (axis == Axis.VERTICAL) input.y else input.x
                    phase = Phase.Dragging(axis, anchor, emitted = 0)
                    // Feedback starts on the first frame of the gesture, not after a full detent.
                    val direction = Direction.of(dx, dy)
                    out += when {
                        navMode -> GestureEvent.Flick(direction)
                        axis == Axis.VERTICAL -> GestureEvent.Dial(if (dy < 0) 1 else -1)
                        else -> GestureEvent.Step(if (dx > 0) 1 else -1)
                    }
                } else {
                    onTime(input.timeMs, out)
                }
            }
            is Phase.Dragging -> {
                val delta = if (p.axis == Axis.VERTICAL) p.anchor - input.y else input.x - p.anchor
                val stepPx = when {
                    navMode -> config.navStepPx
                    p.axis == Axis.VERTICAL -> config.dialStepPx
                    else -> config.stepStepPx
                }
                val total = (delta / stepPx).toInt()
                val diff = total - p.emitted
                if (diff != 0) {
                    p.emitted = total
                    when {
                        navMode -> {
                            val direction = when (p.axis) {
                                Axis.VERTICAL -> if (diff > 0) Direction.UP else Direction.DOWN
                                Axis.HORIZONTAL -> if (diff > 0) Direction.RIGHT else Direction.LEFT
                            }
                            repeat(kotlin.math.abs(diff)) { out += GestureEvent.Flick(direction) }
                        }
                        p.axis == Axis.VERTICAL -> out += GestureEvent.Dial(diff)
                        else -> out += GestureEvent.Step(diff)
                    }
                }
            }
            is Phase.Holding -> {
                val dx = input.x - p.x0
                val dy = input.y - p.y0
                val slot = if (hypot(dx, dy) > config.radialArmPx) Direction.of(dx, dy) else null
                if (slot != null && !p.armed) {
                    p.armed = true
                    if (p.progress > 0f && !p.charged) out += GestureEvent.Charging(0f)
                }
                if (slot != p.hover) {
                    p.hover = slot
                    out += GestureEvent.RadialHover(slot)
                }
                onTime(input.timeMs, out)
            }
            Phase.Idle -> Unit
        }
    }

    private fun onTime(timeMs: Long, out: MutableList<GestureEvent>) {
        when (val p = phase) {
            is Phase.Pressed -> if (timeMs - p.t0 >= config.holdMs) {
                phase = Phase.Holding(p.x0, p.y0, since = timeMs)
                out += GestureEvent.HoldStarted(p.x0, p.y0)
            }
            is Phase.Holding -> if (!p.armed && !p.charged) {
                val progress = ((timeMs - p.since).toFloat() / config.chargeMs).coerceIn(0f, 1f)
                if (progress > p.progress) {
                    p.progress = progress
                    out += GestureEvent.Charging(progress)
                    if (progress >= 1f) {
                        p.charged = true
                        out += GestureEvent.Charged
                    }
                }
            }
            else -> Unit
        }
    }

    private fun onUp(timeMs: Long, out: MutableList<GestureEvent>) {
        when (val p = phase) {
            is Phase.Pressed -> out += if (timeMs - p.t0 < config.holdMs) GestureEvent.Tap else GestureEvent.Fizzled
            is Phase.Holding -> {
                val hover = p.hover
                out += when {
                    p.armed && hover != null -> GestureEvent.RadialSelected(hover)
                    p.charged && !p.armed -> GestureEvent.PowerReleased
                    else -> GestureEvent.Fizzled
                }
            }
            is Phase.Dragging, Phase.Idle -> Unit
        }
        if (phase != Phase.Idle) out += GestureEvent.Ended
        phase = Phase.Idle
    }

    private fun cancel(out: MutableList<GestureEvent>) {
        if (phase is Phase.Holding) out += GestureEvent.Fizzled
        if (phase != Phase.Idle) out += GestureEvent.Ended
        phase = Phase.Idle
    }
}
