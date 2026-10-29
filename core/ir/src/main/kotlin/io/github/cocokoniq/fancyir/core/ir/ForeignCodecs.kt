package io.github.cocokoniq.fancyir.core.ir

import java.util.Base64
import kotlin.math.roundToInt

/**
 * Philips Pronto hex (`0000 006D 0022 0002 0157 00AC …`): learned, modulated codes as used by
 * many IR databases. Durations are counted in carrier cycles.
 */
object ProntoCodec {

    private const val PRONTO_CLOCK = 0.241246

    fun decode(hex: String): IrCode.Raw {
        val words = hex.trim().split(Regex("\\s+")).map { it.toInt(16) }
        require(words.size >= 4) { "Pronto code too short" }
        require(words[0] == 0x0000) { "only learned (0000) Pronto codes are supported" }
        val frequencyWord = words[1]
        require(frequencyWord > 0) { "bad Pronto frequency" }
        val oncePairs = words[2]
        val repeatPairs = words[3]
        require(words.size >= 4 + 2 * (oncePairs + repeatPairs)) { "Pronto code truncated" }
        val cycleUs = frequencyWord * PRONTO_CLOCK
        // The once-sequence is the first press; codes that only define a repeat sequence send that.
        val (from, pairs) = if (oncePairs > 0) 4 to oncePairs else 4 + 2 * oncePairs to repeatPairs
        val timings = IntArray(2 * pairs) { (words[from + it] * cycleUs).roundToInt() }
        return IrCode.Raw((1_000_000 / cycleUs).roundToInt(), dutyCycle = 0.33f, timingsUs = timings)
    }
}

/**
 * Broadlink base64 packets, the format of SmartIR's code files: `0x26` (IR), a repeat count, a
 * little-endian payload length, then durations in ticks of 8192/269 µs (≈30.45 µs); a zero byte
 * escapes a big-endian 16-bit tick count.
 */
object BroadlinkCodec {

    private const val IR_PACKET = 0x26
    private const val US_PER_TICK = 8192.0 / 269.0
    private const val DEFAULT_CARRIER_HZ = 38_000

    fun decodeBase64(base64: String): IrCode.Raw = decode(Base64.getDecoder().decode(base64.trim()))

    fun decode(packet: ByteArray): IrCode.Raw {
        require(packet.size >= 4) { "Broadlink packet too short" }
        require(packet[0].toInt() and 0xFF == IR_PACKET) { "not an IR packet (RF codes are unsupported)" }
        val length = (packet[2].toInt() and 0xFF) or ((packet[3].toInt() and 0xFF) shl 8)
        val end = minOf(packet.size, 4 + length)
        val timings = ArrayList<Int>(length)
        var i = 4
        while (i < end) {
            var ticks = packet[i].toInt() and 0xFF
            i++
            if (ticks == 0) {
                if (i + 1 >= end) break
                ticks = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
                i += 2
            }
            if (ticks > 0) timings += (ticks * US_PER_TICK).roundToInt()
        }
        require(timings.isNotEmpty()) { "empty Broadlink packet" }
        return IrCode.Raw(DEFAULT_CARRIER_HZ, dutyCycle = 0.33f, timingsUs = timings.toIntArray())
    }
}
