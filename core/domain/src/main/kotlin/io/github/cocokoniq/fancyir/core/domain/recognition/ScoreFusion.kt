package io.github.cocokoniq.fancyir.core.domain.recognition

import io.github.cocokoniq.fancyir.core.domain.device.DeviceId

/**
 * Noisy-OR fusion of visual match, spatial prior and recency. In daylight vision dominates and
 * priors alone can only produce a hint; in the dark priors can carry a (PREDICTED) lock.
 */
object ScoreFusion {

    data class Weights(val visual: Float, val spatial: Float, val recency: Float)

    val DAYLIGHT = Weights(visual = 1.0f, spatial = 0.35f, recency = 0.15f)
    val BLIND = Weights(visual = 0.5f, spatial = 0.8f, recency = 0.3f)

    fun fuse(
        visual: Map<DeviceId, Float>,
        spatial: Map<DeviceId, Float>,
        recent: DeviceId?,
        weights: Weights,
    ): Map<DeviceId, Float> {
        val ids = visual.keys + spatial.keys + listOfNotNull(recent)
        return ids.associateWith { id ->
            val v = weights.visual * (visual[id] ?: 0f)
            val s = weights.spatial * (spatial[id] ?: 0f)
            val r = if (id == recent) weights.recency else 0f
            1f - (1f - v) * (1f - s) * (1f - r)
        }
    }
}
