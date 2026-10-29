package io.github.cocokoniq.fancyir.core.ir

/**
 * Accumulates mark/space slices, keeping the alternation invariant: consecutive slices of
 * the same level merge, and a leading space is dropped (a pattern must start with a mark).
 */
internal class TimingBuilder {
    private var slices = IntArray(64)
    private var size = 0

    val totalUs: Long
        get() {
            var total = 0L
            for (i in 0 until size) total += slices[i]
            return total
        }

    fun mark(us: Int) = append(us, isMark = true)

    fun space(us: Int) = append(us, isMark = false)

    fun level(isMark: Boolean, us: Int) = append(us, isMark)

    /** Extends the trailing space so the frame lasts [periodUs] from its first mark. */
    fun padToPeriod(periodUs: Int, minGapUs: Int) {
        val remaining = periodUs - totalUs
        space(if (remaining >= minGapUs) remaining.toInt() else minGapUs)
    }

    fun build(): IntArray = slices.copyOf(size)

    private fun append(us: Int, isMark: Boolean) {
        if (us <= 0) return
        val lastIsMark = size % 2 == 1
        when {
            size == 0 && !isMark -> return
            size > 0 && lastIsMark == isMark -> slices[size - 1] += us
            else -> {
                if (size == slices.size) slices = slices.copyOf(size * 2)
                slices[size++] = us
            }
        }
    }
}
