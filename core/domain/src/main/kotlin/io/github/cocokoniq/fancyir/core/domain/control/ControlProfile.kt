package io.github.cocokoniq.fancyir.core.domain.control

import io.github.cocokoniq.fancyir.core.domain.gesture.Direction
import io.github.cocokoniq.fancyir.core.irdb.Command
import io.github.cocokoniq.fancyir.core.irdb.Command.*
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory

/** What a radial deck slot does (DESIGN.md §3.1). */
sealed interface RadialAction {
    data class Send(val label: String, val chain: List<Command>) : RadialAction
    data object EnterNav : RadialAction
    data object ExitNav : RadialAction
    data object AllCommands : RadialAction
}

/**
 * How the four verbs map onto one category. Each entry is a fallback chain: the first command
 * the device's codeset supports wins (e.g. `[PLAY_PAUSE, PLAY]`).
 */
data class ControlProfile(
    val tap: List<Command>,
    val dialUp: List<Command>,
    val dialDown: List<Command>,
    val stepNext: List<Command>,
    val stepPrev: List<Command>,
    val radial: Map<Direction, RadialAction>,
    val dialLabel: String,
    val stepLabel: String,
    /** HOLD-charge is POWER on every device, so muscle memory carries across devices. */
    val power: List<Command> = listOf(POWER, POWER_OFF),
)

object ControlProfiles {

    private val volume = Pair(listOf(VOLUME_UP), listOf(VOLUME_DOWN))

    private val tv = ControlProfile(
        tap = listOf(MUTE),
        dialUp = volume.first, dialDown = volume.second,
        stepNext = listOf(CHANNEL_UP), stepPrev = listOf(CHANNEL_DOWN),
        radial = mapOf(
            Direction.UP to RadialAction.Send("INPUT", listOf(INPUT)),
            Direction.LEFT to RadialAction.Send("PLAY/PAUSE", listOf(PLAY_PAUSE, PLAY, PAUSE)),
            Direction.RIGHT to RadialAction.EnterNav,
            Direction.DOWN to RadialAction.AllCommands,
        ),
        dialLabel = "VOL", stepLabel = "CH",
    )

    private val audio = ControlProfile(
        tap = listOf(MUTE),
        dialUp = volume.first, dialDown = volume.second,
        stepNext = listOf(INPUT), stepPrev = listOf(INPUT),
        radial = mapOf(
            Direction.UP to RadialAction.Send("SOUND", listOf(SOUND_MODE)),
            Direction.LEFT to RadialAction.Send("PLAY/PAUSE", listOf(PLAY_PAUSE, PLAY, PAUSE)),
            Direction.RIGHT to RadialAction.Send("BASS+", listOf(BASS_UP)),
            Direction.DOWN to RadialAction.AllCommands,
        ),
        dialLabel = "VOL", stepLabel = "INPUT",
    )

    private val climate = ControlProfile(
        tap = listOf(SWING),
        dialUp = listOf(TEMP_UP), dialDown = listOf(TEMP_DOWN),
        stepNext = listOf(MODE), stepPrev = listOf(MODE),
        radial = mapOf(
            Direction.UP to RadialAction.Send("FAN", listOf(FAN_SPEED)),
            Direction.LEFT to RadialAction.Send("TIMER", listOf(TIMER)),
            Direction.RIGHT to RadialAction.Send("TURBO", listOf(TURBO)),
            Direction.DOWN to RadialAction.AllCommands,
        ),
        dialLabel = "TEMP", stepLabel = "MODE",
    )

    private val fan = ControlProfile(
        tap = listOf(OSCILLATE, SWING),
        dialUp = listOf(SPEED_UP, FAN_SPEED), dialDown = listOf(SPEED_DOWN, FAN_SPEED),
        stepNext = listOf(TIMER), stepPrev = listOf(TIMER),
        radial = mapOf(
            Direction.UP to RadialAction.Send("MODE", listOf(MODE)),
            Direction.LEFT to RadialAction.Send("LIGHT", listOf(LIGHT)),
            Direction.DOWN to RadialAction.AllCommands,
        ),
        dialLabel = "SPEED", stepLabel = "TIMER",
    )

    private val projector = tv.copy(
        stepNext = listOf(INPUT), stepPrev = listOf(INPUT),
        radial = tv.radial + (Direction.UP to RadialAction.Send("MENU", listOf(MENU))),
        stepLabel = "INPUT",
    )

    private val light = ControlProfile(
        tap = listOf(POWER, POWER_ON),
        dialUp = listOf(BRIGHTNESS_UP), dialDown = listOf(BRIGHTNESS_DOWN),
        stepNext = listOf(MODE), stepPrev = listOf(MODE),
        radial = mapOf(Direction.DOWN to RadialAction.AllCommands),
        dialLabel = "BRIGHT", stepLabel = "SCENE",
    )

    private val player = ControlProfile(
        tap = listOf(PLAY_PAUSE, PLAY, OK),
        dialUp = volume.first, dialDown = volume.second,
        stepNext = listOf(CHANNEL_UP, NEXT), stepPrev = listOf(CHANNEL_DOWN, PREVIOUS),
        radial = mapOf(
            Direction.UP to RadialAction.Send("HOME", listOf(HOME, MENU)),
            Direction.LEFT to RadialAction.Send("BACK", listOf(BACK, EXIT)),
            Direction.RIGHT to RadialAction.EnterNav,
            Direction.DOWN to RadialAction.AllCommands,
        ),
        dialLabel = "VOL", stepLabel = "SKIP",
    )

    private val generic = ControlProfile(
        tap = listOf(MODE),
        dialUp = listOf(SPEED_UP, TEMP_UP), dialDown = listOf(SPEED_DOWN, TEMP_DOWN),
        stepNext = listOf(TIMER), stepPrev = listOf(TIMER),
        radial = mapOf(Direction.DOWN to RadialAction.AllCommands),
        dialLabel = "LEVEL", stepLabel = "TIMER",
    )

    /** The TV-style trackpad layer (DESIGN.md §3.4). */
    val nav = ControlProfile(
        tap = listOf(OK),
        dialUp = listOf(UP), dialDown = listOf(DOWN),
        stepNext = listOf(RIGHT), stepPrev = listOf(LEFT),
        radial = mapOf(
            Direction.UP to RadialAction.Send("HOME", listOf(HOME, MENU)),
            Direction.RIGHT to RadialAction.Send("MENU", listOf(MENU)),
            Direction.DOWN to RadialAction.ExitNav,
        ),
        dialLabel = "NAV", stepLabel = "NAV",
    )

    /** BACK in the NAV layer is the system back gesture. */
    val navBack: List<Command> = listOf(BACK, EXIT)

    fun forCategory(category: DeviceCategory): ControlProfile = when (category) {
        DeviceCategory.TV, DeviceCategory.MONITOR -> tv
        DeviceCategory.SOUNDBAR, DeviceCategory.AV_RECEIVER, DeviceCategory.SPEAKER -> audio
        DeviceCategory.AIR_CONDITIONER -> climate
        DeviceCategory.FAN -> fan
        DeviceCategory.PROJECTOR -> projector
        DeviceCategory.LIGHT -> light
        DeviceCategory.SET_TOP_BOX, DeviceCategory.STREAMING_DEVICE, DeviceCategory.MEDIA_PLAYER -> player
        DeviceCategory.AIR_PURIFIER, DeviceCategory.HEATER, DeviceCategory.HUMIDIFIER, DeviceCategory.OTHER -> generic
    }
}
