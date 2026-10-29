package io.github.cocokoniq.fancyir.core.domain.control

import io.github.cocokoniq.fancyir.core.domain.gesture.Direction
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureEvent
import io.github.cocokoniq.fancyir.core.irdb.Command

sealed interface ControlIntent {
    /** Send the first supported command of [chain], [presses] times. */
    data class Send(
        val label: String,
        val chain: List<Command>,
        val presses: Int = 1,
        val urgent: Boolean = false,
    ) : ControlIntent

    data object EnterNav : ControlIntent
    data object ExitNav : ControlIntent
    data object OpenAllCommands : ControlIntent
}

/** Gesture verbs → intents for the active profile. Feedback-only events map to null. */
object ControlMapper {

    fun map(event: GestureEvent, profile: ControlProfile): ControlIntent? = when (event) {
        GestureEvent.Tap -> ControlIntent.Send(label(profile.tap), profile.tap)
        is GestureEvent.Dial -> detents(event.steps, profile.dialUp, profile.dialDown, profile.dialLabel)
        is GestureEvent.Step -> detents(event.steps, profile.stepNext, profile.stepPrev, profile.stepLabel)
        is GestureEvent.Flick -> when (event.direction) {
            Direction.UP -> ControlIntent.Send("UP", profile.dialUp)
            Direction.DOWN -> ControlIntent.Send("DOWN", profile.dialDown)
            Direction.RIGHT -> ControlIntent.Send("RIGHT", profile.stepNext)
            Direction.LEFT -> ControlIntent.Send("LEFT", profile.stepPrev)
        }
        GestureEvent.PowerReleased -> ControlIntent.Send("POWER", profile.power, urgent = true)
        is GestureEvent.RadialSelected -> when (val action = profile.radial[event.slot]) {
            is RadialAction.Send -> ControlIntent.Send(action.label, action.chain)
            RadialAction.EnterNav -> ControlIntent.EnterNav
            RadialAction.ExitNav -> ControlIntent.ExitNav
            RadialAction.AllCommands -> ControlIntent.OpenAllCommands
            null -> null
        }
        else -> null
    }

    /** Volume keys always dial the device's primary quantity, even in the NAV layer. */
    fun volumeKey(up: Boolean, deviceProfile: ControlProfile): ControlIntent.Send =
        detents(if (up) 1 else -1, deviceProfile.dialUp, deviceProfile.dialDown, deviceProfile.dialLabel)

    fun navBack(): ControlIntent.Send = ControlIntent.Send("BACK", ControlProfiles.navBack)

    private fun detents(steps: Int, up: List<Command>, down: List<Command>, label: String): ControlIntent.Send =
        if (steps >= 0) ControlIntent.Send("$label +", up, presses = steps.coerceAtLeast(1))
        else ControlIntent.Send("$label −", down, presses = -steps)

    private fun label(chain: List<Command>) = chain.firstOrNull()?.name?.replace('_', ' ') ?: "?"
}
