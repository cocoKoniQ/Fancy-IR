package io.github.cocokoniq.fancyir.feature.hud

import androidx.compose.runtime.Immutable
import io.github.cocokoniq.fancyir.core.domain.gesture.Direction
import io.github.cocokoniq.fancyir.core.domain.hardware.IslandShape
import io.github.cocokoniq.fancyir.core.domain.spatial.MotionState
import io.github.cocokoniq.fancyir.core.domain.targeting.LockSource
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory

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
    /** Show the faint gesture hints (first few locks only, DESIGN.md §2.3). */
    val showGestureHints: Boolean = false,
    val navMode: Boolean = false,
    val charge: Float = 0f,
    val radial: RadialUi? = null,
    val status: String? = null,
    val fire: FireUi? = null,
    /** Learning progress while painting a newly pinned device into the gallery. */
    val painting: Float? = null,
    val sheet: SheetUi? = null,
    val recon: ReconUi? = null,
    val climate: ClimateUi? = null,
    /** Phone lowered or lying down: the lock is kept, analysis pauses (DESIGN.md §2.7). */
    val lapMode: Boolean = false,
    /** Point-blank only: where the beam lands relative to the reticle, in px. */
    val beamMarker: BeamMarker? = null,
    /** No IR emitter: commands are recorded, not sent. */
    val demoMode: Boolean = false,
    val night: Boolean = false,
    val radar: List<RadarBlip> = emptyList(),
    /** Nothing pinned yet: show the first-run guidance card. */
    val vaultEmpty: Boolean = false,
    val headingConfidence: Float = 0f,
    val motion: MotionState = MotionState.STEADY,
)

@Immutable
data class RadialUi(val x: Float, val y: Float, val labels: Map<Direction, String>, val hover: Direction?)

/** A new [serial] triggers the beam animation; [envelope] spaces its rings. */
@Immutable
class FireUi(val serial: Int, val label: String, val envelope: FloatArray)

@Immutable
data class BeamMarker(val dxPx: Float, val dyPx: Float, val distanceM: Float)

@Immutable
data class ClimateUi(val power: Boolean, val mode: String, val fan: String, val tempC: Int)

@Immutable
data class RadarBlip(val label: String, val relativeAzimuthDeg: Float, val locked: Boolean)

@Immutable
data class PickerEntry(val id: String, val label: String, val detail: String? = null)

@Immutable
data class CategoryTile(val category: DeviceCategory, val label: String, val glyph: String, val codesets: Int)

/** Bottom sheets (DESIGN.md §3.5): the long tail, adding devices and the phone's emitter. */
@Immutable
sealed interface SheetUi {
    data class Devices(val pinned: List<PickerEntry>, val categories: List<CategoryTile>) : SheetUi

    data class Brands(val category: DeviceCategory, val title: String, val brands: List<PickerEntry>, val loading: Boolean) : SheetUi

    data class Commands(val title: String, val entries: List<PickerEntry>) : SheetUi

    data class Phone(
        val profileName: String?,
        /** Detected from the device's identity; [bestGuess] when only a partial name or code matched. */
        val matchedAutomatically: Boolean,
        val bestGuess: Boolean,
        val topFrame: Boolean,
        val description: String?,
        /** Back view, from the 1× lens: +x right, +y down (mm); null for top-frame or unknown phones. */
        val offsetMm: Pair<Float, Float>?,
        val diagram: PlateauDiagram?,
        val profiles: List<PickerEntry>,
    ) : SheetUi
}

/** The camera island schematic: positions normalised to the island's box, back view, (0, 0) top-left. */
@Immutable
data class PlateauDiagram(
    val shape: IslandShape,
    val lensX: Float,
    val lensY: Float,
    val emitterX: Float,
    val emitterY: Float,
)

enum class ReconStage { LOOKING_UP, PROBING, SAVING, DONE, NO_MATCH, UNAVAILABLE }

/** First-time recon, in the thumb zone (DESIGN.md §2.6). */
@Immutable
data class ReconUi(
    val title: String,
    val stage: ReconStage,
    val detail: String,
    val candidates: Int = 0,
    val probe: Int = 0,
)
