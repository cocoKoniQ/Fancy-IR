package io.github.cocokoniq.fancyir.feature.hud

import androidx.compose.runtime.Immutable
import io.github.cocokoniq.fancyir.core.domain.gesture.Direction
import io.github.cocokoniq.fancyir.core.domain.targeting.LockSource

enum class TargetPhase { SCANNING, ACQUIRING, LOCKED, UNRECOGNIZED }

/** Everything the HUD draws, rebuilt by [HudViewModel] after each input. */
@Immutable
data class HudUiState(
    val phase: TargetPhase = TargetPhase.SCANNING,
    val lockSource: LockSource? = null,
    val targetName: String? = null,
    val targetCategory: String? = null,
    /** Lock confidence, or acquisition progress while acquiring. */
    val confidence: Float = 0f,
    /** Candidate "whisper" while acquiring. */
    val hint: String? = null,
    val dialLabel: String? = null,
    val stepLabel: String? = null,
    val navMode: Boolean = false,
    val charge: Float = 0f,
    val radial: RadialUi? = null,
    val status: String? = null,
    val fire: FireUi? = null,
    /** Learning progress while painting a newly picked device into the gallery. */
    val painting: Float? = null,
    val picker: PickerUi? = null,
    /** No IR emitter: commands are recorded, not sent. */
    val demoMode: Boolean = false,
    val night: Boolean = false,
    val radar: List<RadarBlip> = emptyList(),
)

@Immutable
data class RadialUi(val x: Float, val y: Float, val labels: Map<Direction, String>, val hover: Direction?)

/** A new [serial] triggers the beam animation; [envelope] spaces its rings. */
@Immutable
class FireUi(val serial: Int, val label: String, val envelope: FloatArray)

@Immutable
data class PickerUi(val title: String, val entries: List<PickerEntry>)

@Immutable
data class PickerEntry(val id: String, val label: String, val detail: String? = null)

@Immutable
data class RadarBlip(val label: String, val relativeAzimuthDeg: Float, val locked: Boolean)
