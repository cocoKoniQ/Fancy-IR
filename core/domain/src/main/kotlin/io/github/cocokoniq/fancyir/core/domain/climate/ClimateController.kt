package io.github.cocokoniq.fancyir.core.domain.climate

import io.github.cocokoniq.fancyir.core.irdb.catalog.ClimateSpec
import io.github.cocokoniq.fancyir.core.irdb.catalog.FunctionKey
import kotlin.math.abs
import kotlin.math.roundToInt

/** The AC's shadow state (ARCHITECTURE.md §3.2): what we believe it's set to. */
data class ClimateState(
    val power: Boolean,
    val mode: String,
    val fan: String,
    val swing: String?,
    val tempC: Int,
)

enum class ClimateIntent { POWER, TEMP_UP, TEMP_DOWN, NEXT_MODE, NEXT_FAN, NEXT_SWING }

/**
 * Turns gestures into whole-state codes for state-table ACs (SmartIR climate profiles): every
 * press sends the code for the complete new state. When the table lacks an exact state (many
 * profiles skip temperatures in fan/dry modes) the nearest available temperature is used.
 */
class ClimateController(private val spec: ClimateSpec, availableFunctions: Collection<String>) {

    private val available = availableFunctions.toSet()

    fun initial(): ClimateState {
        val mode = spec.modes.firstOrNull { it == "cool" } ?: spec.modes.firstOrNull() ?: "cool"
        val fan = spec.fanModes.firstOrNull { it == "auto" } ?: spec.fanModes.firstOrNull() ?: "auto"
        val temp = 24.coerceIn(spec.minTempC, spec.maxTempC)
        return ClimateState(power = false, mode = mode, fan = fan, swing = spec.swingModes.firstOrNull(), tempC = temp)
    }

    fun apply(state: ClimateState, intent: ClimateIntent): ClimateState = when (intent) {
        ClimateIntent.POWER -> state.copy(power = !state.power)
        ClimateIntent.TEMP_UP -> state.copy(power = true, tempC = (state.tempC + step()).coerceAtMost(spec.maxTempC))
        ClimateIntent.TEMP_DOWN -> state.copy(power = true, tempC = (state.tempC - step()).coerceAtLeast(spec.minTempC))
        ClimateIntent.NEXT_MODE -> state.copy(power = true, mode = spec.modes.next(state.mode))
        ClimateIntent.NEXT_FAN -> state.copy(power = true, fan = spec.fanModes.next(state.fan))
        ClimateIntent.NEXT_SWING -> state.copy(power = true, swing = spec.swingModes.takeIf { it.isNotEmpty() }?.next(state.swing))
    }

    /** The function key to fire for [state], or null when the table can't express it. */
    fun functionFor(state: ClimateState): String? {
        if (!state.power) return FunctionKey.CLIMATE_OFF.takeIf { it in available }
        val prefix = listOfNotNull(state.mode, state.fan, state.swing)
        val exact = FunctionKey.climate(prefix + state.tempC.toString())
        if (exact in available) return exact
        // Nearest temperature for this mode/fan/swing, else the mode's temperature-less code ("fan_only").
        val stem = FunctionKey.climate(prefix) + ":"
        return available.filter { it.startsWith(stem) }
            .mapNotNull { key -> key.removePrefix(stem).toIntOrNull()?.let { key to it } }
            .minByOrNull { (_, temp) -> abs(temp - state.tempC) }
            ?.first
            ?: FunctionKey.climate(prefix).takeIf { it in available }
    }

    private fun step(): Int = spec.precision.roundToInt().coerceAtLeast(1)

    private fun List<String>.next(current: String?): String =
        if (isEmpty()) current.orEmpty() else this[(indexOf(current) + 1).mod(size)]
}
