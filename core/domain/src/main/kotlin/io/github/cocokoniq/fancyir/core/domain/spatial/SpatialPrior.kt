package io.github.cocokoniq.fancyir.core.domain.spatial

import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import kotlin.math.exp

/**
 * Remembers where each device is relative to where you usually stand (ARCHITECTURE.md §2.2):
 * every successful fire records the aim direction as an anchor; a device can have several
 * (couch, bed). Scores are a Gaussian kernel on the angle to the nearest anchor, widened when
 * the heading was or is uncertain, and anchors can be tagged with a room context (Wi-Fi BSSID)
 * so identical monitors in two rooms don't steal each other's locks.
 */
class SpatialPrior(
    private val sigmaDeg: Float = 10f,
    private val maxAnchorsPerDevice: Int = 8,
) {
    data class Anchor(val direction: AimDirection, val weight: Int, val confidence: Float, val context: String?)

    data class Bearing(val device: DeviceId, val relativeAzimuthDeg: Float, val elevationDeg: Float, val weight: Int)

    private val anchors = HashMap<DeviceId, MutableList<Anchor>>()

    /** Records an anchor and returns the device's updated anchors (for persistence). */
    fun record(device: DeviceId, aim: AimDirection, confidence: Float = 1f, context: String? = null): List<Anchor> {
        val list = anchors.getOrPut(device) { mutableListOf() }
        val nearestIndex = list.indices
            .filter { list[it].context == context }
            .minByOrNull { list[it].direction.angleTo(aim) }
        if (nearestIndex != null && list[nearestIndex].direction.angleTo(aim) <= sigmaDeg) {
            val nearest = list[nearestIndex]
            list[nearestIndex] = nearest.copy(
                direction = blend(nearest.direction, nearest.weight, aim),
                weight = nearest.weight + 1,
                confidence = maxOf(nearest.confidence, confidence),
            )
        } else {
            if (list.size >= maxAnchorsPerDevice) list.remove(list.minBy { it.weight * (0.5f + it.confidence) })
            list += Anchor(aim, weight = 1, confidence = confidence, context = context)
        }
        return list.toList()
    }

    fun restore(device: DeviceId, saved: List<Anchor>) {
        anchors[device] = saved.takeLast(maxAnchorsPerDevice).toMutableList()
    }

    fun anchors(device: DeviceId): List<Anchor> = anchors[device].orEmpty().toList()

    fun forget(device: DeviceId) {
        anchors.remove(device)
    }

    fun scores(aim: AimDirection, confidence: Float = 1f, context: String? = null): Map<DeviceId, Float> =
        anchors.mapNotNull { (device, list) ->
            list.filter { context == null || it.context == null || it.context == context }
                .maxOfOrNull { anchor ->
                    val sigma = headingSigmaDeg(sigmaDeg, minOf(confidence, anchor.confidence))
                    val angle = anchor.direction.angleTo(aim)
                    exp(-(angle * angle) / (2 * sigma * sigma)) * (0.5f + 0.5f * anchor.confidence)
                }
                ?.let { device to it }
        }.toMap()

    /** The strongest anchor of each device relative to [aim]: what the HUD radar draws. */
    fun bearings(aim: AimDirection, context: String? = null): List<Bearing> = anchors.mapNotNull { (device, list) ->
        list.filter { context == null || it.context == null || it.context == context }
            .maxByOrNull { it.weight }
            ?.let { Bearing(device, aim.relativeAzimuthTo(it.direction), it.direction.elevationDeg, it.weight) }
    }

    private fun blend(current: AimDirection, weight: Int, sample: AimDirection): AimDirection {
        val a = current.unitVector()
        val b = sample.unitVector()
        val w = weight.toFloat()
        return AimDirection.fromVector(
            east = a[0] * w + b[0],
            north = a[1] * w + b[1],
            up = a[2] * w + b[2],
        )
    }
}
