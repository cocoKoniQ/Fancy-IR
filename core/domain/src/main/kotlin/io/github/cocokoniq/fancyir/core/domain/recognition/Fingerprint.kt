package io.github.cocokoniq.fancyir.core.domain.recognition

import kotlin.math.sqrt

/**
 * A downsampled centre crop straight from the camera's YUV planes: luma and chroma in [0, 1].
 * Filled by the Android analyzer without any RGB conversion (ARCHITECTURE.md §4.3).
 */
class LumaChromaTile(
    val lumaSize: Int,
    val luma: FloatArray,
    val chromaSize: Int,
    val u: FloatArray,
    val v: FloatArray,
) {
    init {
        require(luma.size == lumaSize * lumaSize) { "luma must be $lumaSize²" }
        require(u.size == chromaSize * chromaSize && v.size == chromaSize * chromaSize) { "chroma must be $chromaSize²" }
    }

    val meanLuma: Float get() = luma.average().toFloat()

    companion object {
        const val LUMA_SIZE = 32
        const val CHROMA_SIZE = 16

        fun blank() = LumaChromaTile(
            LUMA_SIZE, FloatArray(LUMA_SIZE * LUMA_SIZE),
            CHROMA_SIZE, FloatArray(CHROMA_SIZE * CHROMA_SIZE) { 0.5f }, FloatArray(CHROMA_SIZE * CHROMA_SIZE) { 0.5f },
        )
    }
}

/** Turns a tile into an L2-normalised descriptor; swappable for a LiteRT embedding in M2. */
interface Fingerprinter {
    val dimension: Int

    fun fingerprint(tile: LumaChromaTile): FloatArray
}

object Similarity {

    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    /** Maps raw cosine similarity onto the [0, 1] score the targeting machine expects. */
    fun toScore(similarity: Float, floor: Float = 0.55f, ceiling: Float = 0.92f): Float =
        ((similarity - floor) / (ceiling - floor)).coerceIn(0f, 1f)

    internal fun normalizeInPlace(values: FloatArray, from: Int = 0, until: Int = values.size, weight: Float = 1f) {
        var sum = 0f
        for (i in from until until) sum += values[i] * values[i]
        val norm = sqrt(sum)
        if (norm < 1e-6f) {
            for (i in from until until) values[i] = 0f
            return
        }
        for (i in from until until) values[i] = values[i] / norm * weight
    }
}
