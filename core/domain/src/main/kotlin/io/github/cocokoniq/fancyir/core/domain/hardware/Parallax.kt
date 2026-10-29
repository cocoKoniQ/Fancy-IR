package io.github.cocokoniq.fancyir.core.domain.hardware

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.tan

/**
 * Lens-to-emitter parallax (docs/hardware/rear-camera-ir-blaster-spec.md §5.A–B). Beyond about
 * a metre it's under a degree and the reticle centre is the beam centre. Point-blank, the
 * 14–28 mm offset matters, and the HUD shows where the beam actually lands.
 */
object Parallax {

    /** Below this distance the HUD draws the beam marker apart from the reticle. */
    const val POINT_BLANK_M = 0.5f

    fun angularErrorDeg(offsetMm: Float, distanceM: Float): Float =
        (atan(offsetMm / 1000f / distanceM) * 180f / PI.toFloat())

    /**
     * Where the beam centre appears in the viewfinder relative to the reticle, in pixels
     * (+x right, +y down on screen), using a pinhole model of the preview.
     *
     * The back view and the screen view are mirror images horizontally: an emitter to the right
     * of the lens seen from the back is to the left from the user's side.
     */
    fun screenOffsetPx(offset: EmitterOffset, distanceM: Float, horizontalFovDeg: Float, viewWidthPx: Float): Pair<Float, Float>? {
        val dx = offset.dxMm ?: return null
        val dy = offset.dyMm ?: return null
        if (distanceM <= 0f) return null
        val focalPx = (viewWidthPx / 2f) / tan(horizontalFovDeg / 2f * PI.toFloat() / 180f)
        return Pair(-dx / 1000f / distanceM * focalPx, dy / 1000f / distanceM * focalPx)
    }

    /** Camera2 reports focus distance in diopters (1/m); 0 means infinity. */
    fun distanceFromDiopters(diopters: Float): Float? = if (diopters > 0.01f) 1f / diopters else null
}
