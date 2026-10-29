package io.github.cocokoniq.fancyir.core.ir

/**
 * One key press as a protocol defines it.
 *
 * [frame] and [repeatFrame] start with a mark and end with the inter-frame gap (a space), so
 * frames can be concatenated directly.
 */
class IrSignal(
    val carrierHz: Int,
    val frame: IntArray,
    /** Burst sent while a key is held (e.g. the NEC repeat code); `null` repeats [frame]. */
    val repeatFrame: IntArray? = null,
    /** Frames a single press needs to be accepted (Sony SIRC requires 3). */
    val minFrames: Int = 1,
) {
    init {
        require(frame.isNotEmpty() && frame.size % 2 == 0) { "frame must end with a gap" }
        require(repeatFrame == null || (repeatFrame.isNotEmpty() && repeatFrame.size % 2 == 0)) {
            "repeatFrame must end with a gap"
        }
        require(minFrames >= 1)
    }

    /** Slices for one complete press, ending with the trailing gap. */
    fun press(): IntArray = hold(minFrames)

    /** Slices for a key held for [frames] frames: the first frame, then repeat frames. */
    fun hold(frames: Int): IntArray {
        val count = maxOf(frames, minFrames)
        val repeat = repeatFrame ?: frame
        val out = IntArray(frame.size + (count - 1) * repeat.size)
        frame.copyInto(out)
        var offset = frame.size
        repeat(count - 1) {
            repeat.copyInto(out, offset)
            offset += repeat.size
        }
        return out
    }

    val pressDurationUs: Long get() = press().fold(0L) { acc, slice -> acc + slice }
}
