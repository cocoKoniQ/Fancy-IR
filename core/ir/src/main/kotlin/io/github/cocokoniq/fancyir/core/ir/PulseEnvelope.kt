package io.github.cocokoniq.fancyir.core.ir

/**
 * The burst envelope of a pattern: for each time bin, the fraction of it spent emitting.
 * The HUD turns this into the haptic "IR texture" and the beam ring spacing (DESIGN.md §2.4),
 * so every protocol looks and feels different.
 */
object PulseEnvelope {

    fun of(timingsUs: IntArray, binUs: Int): FloatArray {
        require(binUs > 0)
        val totalUs = timingsUs.fold(0L) { acc, slice -> acc + slice }
        val bins = ((totalUs + binUs - 1) / binUs).toInt()
        val marked = LongArray(bins)
        var cursor = 0L
        timingsUs.forEachIndexed { index, slice ->
            if (index % 2 == 0) {
                var from = cursor
                val to = cursor + slice
                while (from < to) {
                    val bin = (from / binUs).toInt()
                    val binEnd = (bin + 1L) * binUs
                    val until = minOf(to, binEnd)
                    marked[bin] += until - from
                    from = until
                }
            }
            cursor += slice
        }
        return FloatArray(bins) { marked[it].toFloat() / binUs }
    }
}
