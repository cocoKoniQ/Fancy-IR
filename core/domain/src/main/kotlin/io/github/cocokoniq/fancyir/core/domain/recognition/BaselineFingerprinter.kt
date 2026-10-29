package io.github.cocokoniq.fancyir.core.domain.recognition

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Model-free descriptor (ARCHITECTURE.md §2.2): gradient-orientation histograms on a 4×4 grid
 * (shape), a zero-mean 8×8 luma layout (arrangement, incl. a lit screen) and a 4×4 UV histogram
 * (colour). Well under a millisecond, no model download, decent in a static room.
 */
class BaselineFingerprinter : Fingerprinter {

    override val dimension: Int = HOG_DIMS + LAYOUT_DIMS + COLOR_DIMS

    override fun fingerprint(tile: LumaChromaTile): FloatArray {
        val out = FloatArray(dimension)
        orientationHistograms(tile, out)
        layout(tile, out)
        colorHistogram(tile, out)
        Similarity.normalizeInPlace(out, 0, HOG_DIMS, weight = HOG_WEIGHT)
        Similarity.normalizeInPlace(out, HOG_DIMS, HOG_DIMS + LAYOUT_DIMS, weight = LAYOUT_WEIGHT)
        Similarity.normalizeInPlace(out, HOG_DIMS + LAYOUT_DIMS, dimension, weight = COLOR_WEIGHT)
        Similarity.normalizeInPlace(out)
        return out
    }

    private fun orientationHistograms(tile: LumaChromaTile, out: FloatArray) {
        val n = tile.lumaSize
        val l = boxBlur(tile.luma, n)
        for (y in 1 until n - 1) {
            for (x in 1 until n - 1) {
                val gx = l[y * n + x + 1] - l[y * n + x - 1]
                val gy = l[(y + 1) * n + x] - l[(y - 1) * n + x]
                val magnitude = sqrt(gx * gx + gy * gy)
                if (magnitude < MIN_GRADIENT) continue
                var angle = atan2(gy, gx)
                if (angle < 0) angle += PI.toFloat()
                // Soft-bin between the two nearest orientations, wrapping π back to 0: axis-aligned
                // edges sit exactly on bin centres, and hard bins would flip them on sensor noise.
                val position = angle / PI.toFloat() * BINS
                val lower = floor(position).toInt()
                val weight = position - lower
                val cell = (y * GRID / n) * GRID + (x * GRID / n)
                out[cell * BINS + lower % BINS] += magnitude * (1 - weight)
                out[cell * BINS + (lower + 1) % BINS] += magnitude * weight
            }
        }
    }

    /** 3×3 box blur (edges clamped): sensor noise otherwise dominates the gradient histograms. */
    private fun boxBlur(src: FloatArray, n: Int): FloatArray {
        val dst = FloatArray(src.size)
        for (y in 0 until n) for (x in 0 until n) {
            var sum = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val yy = (y + dy).coerceIn(0, n - 1)
                val xx = (x + dx).coerceIn(0, n - 1)
                sum += src[yy * n + xx]
            }
            dst[y * n + x] = sum / 9f
        }
        return dst
    }

    private fun layout(tile: LumaChromaTile, out: FloatArray) {
        val n = tile.lumaSize
        val block = n / LAYOUT
        var mean = 0f
        for (by in 0 until LAYOUT) for (bx in 0 until LAYOUT) {
            var sum = 0f
            for (y in by * block until (by + 1) * block) for (x in bx * block until (bx + 1) * block) sum += tile.luma[y * n + x]
            val value = sum / (block * block)
            out[HOG_DIMS + by * LAYOUT + bx] = value
            mean += value
        }
        mean /= LAYOUT_DIMS
        for (i in HOG_DIMS until HOG_DIMS + LAYOUT_DIMS) out[i] -= mean
    }

    private fun colorHistogram(tile: LumaChromaTile, out: FloatArray) {
        val offset = HOG_DIMS + LAYOUT_DIMS
        for (i in tile.u.indices) {
            val ub = (tile.u[i] * COLOR_BINS).toInt().coerceIn(0, COLOR_BINS - 1)
            val vb = (tile.v[i] * COLOR_BINS).toInt().coerceIn(0, COLOR_BINS - 1)
            out[offset + ub * COLOR_BINS + vb] += 1f
        }
    }

    private companion object {
        const val GRID = 4
        const val BINS = 8
        const val HOG_DIMS = GRID * GRID * BINS
        const val LAYOUT = 8
        const val LAYOUT_DIMS = LAYOUT * LAYOUT
        const val COLOR_BINS = 4
        const val COLOR_DIMS = COLOR_BINS * COLOR_BINS
        const val HOG_WEIGHT = 1.0f
        const val LAYOUT_WEIGHT = 0.7f
        const val COLOR_WEIGHT = 0.5f
        const val MIN_GRADIENT = 0.02f
    }
}
