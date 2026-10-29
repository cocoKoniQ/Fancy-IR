package io.github.cocokoniq.fancyir.core.domain.hardware

import kotlin.math.sqrt

/** Where the IR LED sits on the phone (docs/hardware/rear-camera-ir-blaster-spec.md §5.D). */
enum class EmitterMount {
    /** On the camera island: the beam is parallel to the camera's optical axis. */
    REAR_PLATEAU,

    /** On the top edge: the beam points out of the top of the phone, so the viewfinder can't aim it. */
    TOP_EDGE,
}

/**
 * Emitter position relative to the main 1× lens, viewed from the back of the phone held in
 * portrait: +x is to the right, +y is down, in millimetres. [dxMm]/[dyMm] are null when the
 * spec gives only a distance (e.g. "across the island").
 */
data class EmitterOffset(val distanceMm: Float, val dxMm: Float?, val dyMm: Float?) {
    val hasDirection: Boolean get() = dxMm != null && dyMm != null

    companion object {
        fun right(mm: Float) = EmitterOffset(mm, mm, 0f)

        fun downRight(mm: Float): EmitterOffset {
            val axis = mm / sqrt(2f)
            return EmitterOffset(mm, axis, axis)
        }

        /** "Above and slightly right": about 10° off vertical. */
        fun upSlightlyRight(mm: Float) = EmitterOffset(mm, mm * 0.17f, -mm * 0.98f)

        fun unspecified(mm: Float) = EmitterOffset(mm, null, null)
    }
}

data class EmitterProfile(
    val id: String,
    val manufacturer: String,
    /** Marketing names as shown in Settings → About phone; matched against the device's identity. */
    val models: List<String>,
    val mount: EmitterMount,
    val islandLayout: String,
    val position: String,
    val offset: EmitterOffset,
    /** Exact `Build.MODEL` codes or `Build.DEVICE` codenames, for phones whose device name was changed. */
    val aliases: List<String> = emptyList(),
) {
    val displayName: String get() = models.joinToString(" / ")
}
