package io.github.cocokoniq.fancyir.core.ir

/**
 * Makes patterns acceptable to every `ConsumerIrManager` implementation: AOSP rejects
 * non-positive slices and patterns longer than 2 s, and some HALs misbehave on a trailing space.
 */
object PatternSanitizer {

    const val MAX_TRANSMIT_US = 2_000_000

    /**
     * Drops non-positive slices (merging the neighbours they separated), drops a leading space
     * and trims the trailing space. Input index parity defines the level: even = mark.
     */
    fun sanitize(timingsUs: IntArray): IntArray {
        val builder = TimingBuilder()
        timingsUs.forEachIndexed { index, slice -> builder.level(isMark = index % 2 == 0, slice) }
        return trimTrailingSpace(builder.build())
    }

    fun trimTrailingSpace(timingsUs: IntArray): IntArray =
        if (timingsUs.size % 2 == 0 && timingsUs.isNotEmpty()) timingsUs.copyOf(timingsUs.size - 1) else timingsUs

    /**
     * Splits a pattern into chunks no longer than [maxUs], cutting only inside spaces (the cut
     * space is dropped; the gap between transmit calls replaces it).
     */
    fun split(pattern: IrPattern, maxUs: Int = MAX_TRANSMIT_US): List<IrPattern> {
        val timings = trimTrailingSpace(pattern.timingsUs)
        if (pattern.durationUs <= maxUs) return listOf(IrPattern(pattern.carrierHz, timings))

        val chunks = ArrayList<IrPattern>()
        var start = 0
        var chunkUs = 0L
        var i = 0
        while (i < timings.size) {
            val mark = timings[i].toLong()
            val space = if (i + 1 < timings.size) timings[i + 1].toLong() else 0L
            if (chunkUs + mark > maxUs && i > start) {
                chunks += IrPattern(pattern.carrierHz, timings.copyOfRange(start, i - 1))
                start = i
                chunkUs = 0
            }
            chunkUs += mark + space
            i += 2
        }
        chunks += IrPattern(pattern.carrierHz, trimTrailingSpace(timings.copyOfRange(start, timings.size)))
        return chunks
    }
}
