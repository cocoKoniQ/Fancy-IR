package io.github.cocokoniq.fancyir.core.vision

import androidx.camera.core.ImageProxy
import io.github.cocokoniq.fancyir.core.domain.recognition.LumaChromaTile

/**
 * Samples the reticle's centre square straight from the YUV_420_888 planes into a small
 * upright [LumaChromaTile]: no RGB conversion, no bitmap, a few thousand byte reads per frame.
 */
internal class TileSampler(private val cropFraction: Float = 0.5f) {

    fun sample(image: ImageProxy): LumaChromaTile {
        val width = image.width
        val height = image.height
        val side = (minOf(width, height) * cropFraction).toInt().coerceAtLeast(LumaChromaTile.LUMA_SIZE)
        val left = (width - side) / 2
        val top = (height - side) / 2
        val rotation = image.imageInfo.rotationDegrees

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        fun sourceOf(u: Float, v: Float): Pair<Int, Int> {
            // (u, v) are upright tile coordinates in [0, 1); undo the sensor rotation.
            val (a, b) = when (rotation) {
                90 -> v to 1f - u
                180 -> 1f - u to 1f - v
                270 -> 1f - v to u
                else -> u to v
            }
            val x = (left + a * side).toInt().coerceIn(0, width - 1)
            val y = (top + b * side).toInt().coerceIn(0, height - 1)
            return x to y
        }

        val n = LumaChromaTile.LUMA_SIZE
        val luma = FloatArray(n * n)
        for (ty in 0 until n) for (tx in 0 until n) {
            var sum = 0
            for (sy in 0..1) for (sx in 0..1) {
                val (x, y) = sourceOf((tx + 0.25f + 0.5f * sx) / n, (ty + 0.25f + 0.5f * sy) / n)
                sum += yPlane.buffer.get(y * yPlane.rowStride + x * yPlane.pixelStride).toInt() and 0xFF
            }
            luma[ty * n + tx] = sum / (4 * 255f)
        }

        val c = LumaChromaTile.CHROMA_SIZE
        val uValues = FloatArray(c * c)
        val vValues = FloatArray(c * c)
        for (ty in 0 until c) for (tx in 0 until c) {
            val (x, y) = sourceOf((tx + 0.5f) / c, (ty + 0.5f) / c)
            val cx = x / 2
            val cy = y / 2
            uValues[ty * c + tx] = (uPlane.buffer.get(cy * uPlane.rowStride + cx * uPlane.pixelStride).toInt() and 0xFF) / 255f
            vValues[ty * c + tx] = (vPlane.buffer.get(cy * vPlane.rowStride + cx * vPlane.pixelStride).toInt() and 0xFF) / 255f
        }
        return LumaChromaTile(n, luma, c, uValues, vValues)
    }
}
