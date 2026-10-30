package io.github.cocokoniq.fancyir.core.domain.hardware

import kotlin.math.hypot

/** Camera island outline, viewed from the back in portrait (docs/hardware/rear-camera-ir-blaster-spec.md §4). */
enum class IslandShape { SQUIRCLE_TOP_LEFT, CIRCLE_CENTER, CIRCLE_TOP_LEFT, PILL_TOP_LEFT, VISOR_TOP }

/** Where the IR LED sits on the phone. */
enum class IrMountType {
    /** On the camera island: the beam is parallel to the camera's optical axis. */
    REAR_PLATEAU,

    /** On the top frame rail, 90° to the camera: the viewfinder can't aim it, the top edge does. */
    TOP_FRAME,
}

/**
 * One phone family's IR emitter (docs/hardware/rear-camera-ir-blaster-spec.md v2).
 *
 * Positions are normalised to the camera island's bounding box seen from the back in portrait:
 * (0, 0) top-left, (1, 1) bottom-right. They place the 1× lens and the emitter on the schematic.
 * The physical offset from the 1× lens to the emitter is [offsetDxMm] (+ right) and
 * [offsetDyMm] (+ down), in millimetres; it drives the point-blank beam marker. Neither applies
 * to [IrMountType.TOP_FRAME] phones.
 */
data class PhoneIrProfile(
    val id: String,
    val displayName: String,
    val mountType: IrMountType = IrMountType.REAR_PLATEAU,
    val islandShape: IslandShape = IslandShape.SQUIRCLE_TOP_LEFT,
    val mainLensNormX: Float = 0.27f,
    val mainLensNormY: Float = 0.27f,
    val irEmitterNormX: Float = 0.50f,
    val irEmitterNormY: Float = 0.82f,
    val offsetDxMm: Float = 10f,
    val offsetDyMm: Float = 20f,
    val description: String,
    /** Marketing names as shown in Settings → About phone; matched against the device's identity. */
    val models: List<String>,
    val manufacturer: String,
    /** Exact `Build.MODEL` codes or `Build.DEVICE` codenames, for phones whose device name was changed. */
    val aliases: List<String> = emptyList(),
    /** `Build.MODEL` prefixes, honoured only for [prefixBrands] and only when nothing else matched. */
    val modelPrefixes: List<String> = emptyList(),
    val prefixBrands: List<String> = listOf(manufacturer),
) {
    val isRearPlateau: Boolean get() = mountType == IrMountType.REAR_PLATEAU

    /** Straight-line lens-to-emitter distance; null for top-frame phones. */
    val offsetMm: Float? get() = if (isRearPlateau) hypot(offsetDxMm, offsetDyMm) else null
}
