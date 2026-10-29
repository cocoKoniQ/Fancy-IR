package io.github.cocokoniq.fancyir.core.domain

import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import io.github.cocokoniq.fancyir.core.domain.recognition.BaselineFingerprinter
import io.github.cocokoniq.fancyir.core.domain.recognition.Gallery
import io.github.cocokoniq.fancyir.core.domain.recognition.LumaChromaTile
import io.github.cocokoniq.fancyir.core.domain.recognition.ScoreFusion
import io.github.cocokoniq.fancyir.core.domain.recognition.Similarity
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecognitionTest {

    private val fingerprinter = BaselineFingerprinter()

    /** A dark frame with a bright "screen" rectangle, like a TV seen through the reticle. */
    private fun screen(x0: Int, y0: Int, w: Int, h: Int, brightness: Float = 0.9f, noise: Float = 0f, seed: Int = 1): LumaChromaTile {
        val n = LumaChromaTile.LUMA_SIZE
        val random = Random(seed)
        val luma = FloatArray(n * n) { i ->
            val x = i % n
            val y = i / n
            val base = if (x in x0 until x0 + w && y in y0 until y0 + h) brightness else 0.1f
            (base + (random.nextFloat() - 0.5f) * noise).coerceIn(0f, 1f)
        }
        val c = LumaChromaTile.CHROMA_SIZE
        return LumaChromaTile(n, luma, c, FloatArray(c * c) { 0.55f }, FloatArray(c * c) { 0.45f })
    }

    /** A vertical-bar pattern with warm colour, like a fan grille. */
    private fun grille(): LumaChromaTile {
        val n = LumaChromaTile.LUMA_SIZE
        val c = LumaChromaTile.CHROMA_SIZE
        return LumaChromaTile(n, FloatArray(n * n) { i -> if ((i % n) % 4 < 2) 0.8f else 0.2f }, c, FloatArray(c * c) { 0.3f }, FloatArray(c * c) { 0.7f })
    }

    @Test
    fun `fingerprints are unit length`() {
        val fp = fingerprinter.fingerprint(screen(6, 8, 20, 14))
        assertEquals(fingerprinter.dimension, fp.size)
        assertTrue(abs(sqrt(fp.sumOf { (it * it).toDouble() }) - 1.0) < 1e-4)
    }

    @Test
    fun `the same device under noise and brightness change stays closer than a different device`() {
        val reference = fingerprinter.fingerprint(screen(6, 8, 20, 14))
        val sameNoisy = fingerprinter.fingerprint(screen(7, 8, 20, 14, brightness = 0.7f, noise = 0.08f, seed = 7))
        val other = fingerprinter.fingerprint(grille())

        val same = Similarity.cosine(reference, sameNoisy)
        val different = Similarity.cosine(reference, other)
        assertTrue(same > 0.9f, "same device similarity $same")
        assertTrue(same - different > 0.3f, "same=$same different=$different")
    }

    @Test
    fun `gallery rejects near-duplicates and matches the right device`() {
        val gallery = Gallery()
        val tv = DeviceId("tv")
        val fan = DeviceId("fan")
        val tvPrint = fingerprinter.fingerprint(screen(6, 8, 20, 14))

        assertTrue(gallery.add(tv, tvPrint))
        assertFalse(gallery.add(tv, tvPrint.copyOf()), "identical exemplar is redundant")
        gallery.add(fan, fingerprinter.fingerprint(grille()))

        val scores = gallery.similarities(fingerprinter.fingerprint(screen(6, 9, 20, 14, noise = 0.05f)))
        assertEquals(tv, scores.maxBy { it.value }.key)
    }

    @Test
    fun `priors alone hint in daylight but can lock in the dark`() {
        val tv = DeviceId("tv")
        val daylight = ScoreFusion.fuse(emptyMap(), mapOf(tv to 1f), recent = tv, weights = ScoreFusion.DAYLIGHT)
        val blind = ScoreFusion.fuse(emptyMap(), mapOf(tv to 1f), recent = tv, weights = ScoreFusion.BLIND)

        assertTrue(daylight.getValue(tv) < 0.75f, "daylight prior ${daylight[tv]}")
        assertTrue(blind.getValue(tv) >= 0.75f, "blind prior ${blind[tv]}")
    }
}
