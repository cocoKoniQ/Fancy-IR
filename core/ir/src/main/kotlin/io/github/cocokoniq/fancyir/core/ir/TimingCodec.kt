package io.github.cocokoniq.fancyir.core.ir

/**
 * Compact binary form of timing arrays for database blobs: unsigned LEB128 varints, so typical
 * IR slices (< 16 ms) take two bytes instead of four.
 */
object TimingCodec {

    fun encode(timings: IntArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(timings.size * 2)
        for (value in timings) {
            require(value >= 0) { "negative timing $value" }
            var v = value
            while (v >= 0x80) {
                out.write((v and 0x7F) or 0x80)
                v = v ushr 7
            }
            out.write(v)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): IntArray {
        val out = ArrayList<Int>(bytes.size / 2)
        var value = 0
        var shift = 0
        for (b in bytes) {
            val byte = b.toInt() and 0xFF
            value = value or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) {
                out += value
                value = 0
                shift = 0
            } else {
                shift += 7
                require(shift <= 28) { "varint too long" }
            }
        }
        require(shift == 0) { "truncated varint" }
        return out.toIntArray()
    }
}
