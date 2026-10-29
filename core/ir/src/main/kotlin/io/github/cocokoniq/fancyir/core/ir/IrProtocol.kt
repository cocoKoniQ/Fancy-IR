package io.github.cocokoniq.fancyir.core.ir

/**
 * Protocols named as in Flipper Zero `.ir` files, so every `type: parsed` entry maps directly.
 *
 * Bit widths follow Flipper's address/command conventions.
 */
enum class IrProtocol(
    val flipperName: String,
    val carrierHz: Int,
    val addressBits: Int,
    val commandBits: Int,
    val hasToggle: Boolean = false,
    /** False until the encoder has been verified against a hardware receiver. */
    val isEncodable: Boolean = true,
) {
    NEC("NEC", 38_000, addressBits = 8, commandBits = 8),
    NEC_EXT("NECext", 38_000, addressBits = 16, commandBits = 16),
    NEC42("NEC42", 38_000, addressBits = 13, commandBits = 8),
    NEC42_EXT("NEC42ext", 38_000, addressBits = 26, commandBits = 16),
    SAMSUNG32("Samsung32", 38_000, addressBits = 8, commandBits = 8),
    RC5("RC5", 36_000, addressBits = 5, commandBits = 6, hasToggle = true),
    RC5X("RC5X", 36_000, addressBits = 5, commandBits = 7, hasToggle = true),
    RC6("RC6", 36_000, addressBits = 8, commandBits = 8, hasToggle = true),
    SIRC("SIRC", 40_000, addressBits = 5, commandBits = 7),
    SIRC15("SIRC15", 40_000, addressBits = 8, commandBits = 7),
    SIRC20("SIRC20", 40_000, addressBits = 13, commandBits = 7),
    KASEIKYO("Kaseikyo", 37_000, addressBits = 32, commandBits = 10, isEncodable = false),
    RCA("RCA", 56_000, addressBits = 4, commandBits = 8, isEncodable = false),
    PIONEER("Pioneer", 40_000, addressBits = 8, commandBits = 8, isEncodable = false);

    val maxAddress: Long get() = (1L shl addressBits) - 1
    val maxCommand: Long get() = (1L shl commandBits) - 1

    companion object {
        private val byFlipperName = entries.associateBy { it.flipperName.lowercase() }

        fun fromFlipperName(name: String): IrProtocol? = byFlipperName[name.trim().lowercase()]
    }
}
