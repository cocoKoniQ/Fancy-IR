package io.github.cocokoniq.fancyir.core.domain.recognition

import io.github.cocokoniq.fancyir.core.domain.device.DeviceId

/**
 * Per-device exemplar fingerprints for few-shot instance retrieval. New exemplars are accepted
 * only if they add diversity, so the gallery learns new lighting and viewpoints without
 * filling up with near-duplicates (ARCHITECTURE.md §2.2).
 */
class Gallery(
    private val maxPerDevice: Int = 48,
    /** Exemplars at least this similar to an existing one are redundant. */
    private val redundancy: Float = 0.98f,
) {
    private val exemplars = LinkedHashMap<DeviceId, ArrayDeque<FloatArray>>()

    val devices: Set<DeviceId> get() = exemplars.keys

    fun size(device: DeviceId): Int = exemplars[device]?.size ?: 0

    /** Returns true if the exemplar was kept. */
    fun add(device: DeviceId, fingerprint: FloatArray): Boolean {
        val list = exemplars.getOrPut(device) { ArrayDeque() }
        if (list.any { Similarity.cosine(it, fingerprint) >= redundancy }) return false
        if (list.size >= maxPerDevice) list.removeFirst()
        list.addLast(fingerprint)
        return true
    }

    fun remove(device: DeviceId) {
        exemplars.remove(device)
    }

    /** Best cosine similarity per device. */
    fun similarities(fingerprint: FloatArray): Map<DeviceId, Float> =
        exemplars.mapValues { (_, list) -> list.maxOf { Similarity.cosine(it, fingerprint) } }
}
