package io.github.cocokoniq.fancyir.core.domain.spatial

import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import kotlin.math.exp

/**
 * Remembers where each device is relative to where you usually stand (ARCHITECTURE.md §2.2):
 * every successful fire records the aim direction as an anchor; a device can have several
 * (couch, bed). Scores are a Gaussian kernel on the angle to the nearest anchor.
 */
class SpatialPrior(
    private val sigmaDeg: Float = 10f,
    private val maxAnchorsPerDevice: Int = 8,
) {
    private class Anchor(var direction: AimDirection, var weight: Int)

    data class Bearing(val device: DeviceId, val relativeAzimuthDeg: Float, val elevationDeg: Float, val weight: Int)

    private val anchors = HashMap<DeviceId, MutableList<Anchor>>()

    fun record(device: DeviceId, aim: AimDirection) {
        val list = anchors.getOrPut(device) { mutableListOf() }
        val nearest = list.minByOrNull { it.direction.angleTo(aim) }
        if (nearest != null && nearest.direction.angleTo(aim) <= sigmaDeg) {
            nearest.direction = blend(nearest.direction, nearest.weight, aim)
            nearest.weight += 1
            return
        }
        if (list.size >= maxAnchorsPerDevice) list.remove(list.minBy { it.weight })
        list += Anchor(aim, weight = 1)
    }

    fun scores(aim: AimDirection): Map<DeviceId, Float> = anchors.mapValues { (_, list) ->
        list.maxOf { anchor ->
            val angle = anchor.direction.angleTo(aim)
            exp(-(angle * angle) / (2 * sigmaDeg * sigmaDeg))
        }
    }

    /** The strongest anchor of each device relative to [aim]: what the HUD radar draws. */
    fun bearings(aim: AimDirection): List<Bearing> = anchors.mapNotNull { (device, list) ->
        list.maxByOrNull { it.weight }?.let {
            Bearing(device, aim.relativeAzimuthTo(it.direction), it.direction.elevationDeg, it.weight)
        }
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
