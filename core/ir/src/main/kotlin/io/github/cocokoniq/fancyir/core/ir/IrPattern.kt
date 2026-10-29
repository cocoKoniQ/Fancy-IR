package io.github.cocokoniq.fancyir.core.ir

/**
 * Exactly what reaches `ConsumerIrManager.transmit()`: a carrier frequency and alternating
 * mark/space durations in microseconds, starting with a mark and ending with a mark.
 */
class IrPattern(val carrierHz: Int, val timingsUs: IntArray) {

    val durationUs: Long get() = timingsUs.fold(0L) { acc, slice -> acc + slice }

    override fun equals(other: Any?): Boolean =
        other is IrPattern && carrierHz == other.carrierHz && timingsUs.contentEquals(other.timingsUs)

    override fun hashCode(): Int = 31 * carrierHz + timingsUs.contentHashCode()

    override fun toString(): String = "IrPattern(${carrierHz}Hz, ${timingsUs.size} slices, ${durationUs}µs)"
}
