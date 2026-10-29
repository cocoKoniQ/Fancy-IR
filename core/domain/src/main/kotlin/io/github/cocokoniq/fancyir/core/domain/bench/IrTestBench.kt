package io.github.cocokoniq.fancyir.core.domain.bench

import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.IrProtocol

/**
 * One reference frame for hardware verification with an IR receiver (docs/testing/ir-verification.md).
 * [word] is the frame's data bits in transmission order printed MSB first — what
 * IRremoteESP8266-style decoders show; null for Manchester protocols, which decoders report as
 * address and command instead.
 */
data class BenchVector(
    val label: String,
    val code: IrCode.Parsed,
    val leaderMarkUs: Int,
    val leaderSpaceUs: Int,
    val word: Long?,
    val bits: Int,
    val note: String,
)

/**
 * The encoder test bench (ARCHITECTURE.md §6): fires one known frame per verified protocol so
 * a microcontroller receiver can confirm carrier, timings and bits on real hardware.
 */
object IrTestBench {

    const val SPACING_MS = 1_500L

    val vectors: List<BenchVector> = listOf(
        BenchVector("NEC 0x04 / 0x08", IrCode.Parsed(IrProtocol.NEC, 0x04, 0x08), 9024, 4512, 0x20DF10EF, 32,
            "LG power. 38 kHz, 564 µs bit unit, 108 ms period."),
        BenchVector("Samsung32 0x07 / 0x02", IrCode.Parsed(IrProtocol.SAMSUNG32, 0x07, 0x02), 4512, 4512, 0xE0E040BF, 32,
            "Samsung power. 38 kHz, address byte sent twice."),
        BenchVector("NECext 0x7F00 / 0xEA15", IrCode.Parsed(IrProtocol.NEC_EXT, 0x7F00, 0xEA15), 9024, 4512, 0x00FEA857, 32,
            "38 kHz, 16-bit address without inversion."),
        BenchVector("NEC42 0x1ABC / 0x5A", IrCode.Parsed(IrProtocol.NEC42, 0x1ABC, 0x5A), 9024, 4512, null, 42,
            "38 kHz, 13-bit address + inverse, 8-bit command + inverse (Aiwa layout)."),
        BenchVector("SIRC-12 0x01 / 0x15", IrCode.Parsed(IrProtocol.SIRC, 0x01, 0x15), 2400, 600, 0xA90, 12,
            "Sony power. 40 kHz, pulse width 600/1200 µs, sent 3× at 45 ms."),
        BenchVector("SIRC-15 0x1A / 0x15", IrCode.Parsed(IrProtocol.SIRC15, 0x1A, 0x15), 2400, 600, 0x5458, 15,
            "40 kHz, 8-bit address, sent 3×."),
        BenchVector("SIRC-20 0x1A3F / 0x7F", IrCode.Parsed(IrProtocol.SIRC20, 0x1A3F, 0x7F), 2400, 600, 0xFFF8B, 20,
            "40 kHz, 5-bit device + 8-bit extended, sent 3×."),
        BenchVector("RC5 0x00 / 0x0C", IrCode.Parsed(IrProtocol.RC5, 0x00, 0x0C), 889, 889, null, 14,
            "Philips power. 36 kHz Manchester, 889 µs half-bit; the toggle bit alternates between runs."),
        BenchVector("RC6 0x00 / 0x0C", IrCode.Parsed(IrProtocol.RC6, 0x00, 0x0C), 2664, 888, null, 21,
            "36 kHz Manchester, 444 µs unit, mode 0, double-width toggle."),
    )
}
