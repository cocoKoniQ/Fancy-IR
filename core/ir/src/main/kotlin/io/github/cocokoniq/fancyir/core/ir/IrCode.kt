package io.github.cocokoniq.fancyir.core.ir

/** A stored IR code: either protocol parameters or a raw capture. */
sealed interface IrCode {

    /** Builds the transmittable signal. [toggle] only matters for RC5/RC6. */
    fun toSignal(toggle: Boolean = false): IrSignal

    val usesToggle: Boolean

    data class Parsed(val protocol: IrProtocol, val address: Long, val command: Long) : IrCode {
        override val usesToggle: Boolean get() = protocol.hasToggle

        override fun toSignal(toggle: Boolean): IrSignal =
            ProtocolEncoders.encode(protocol, address, command, toggle)

        override fun toString(): String =
            "${protocol.flipperName}(0x${address.toString(16)}, 0x${command.toString(16)})"
    }

    class Raw(val carrierHz: Int, val dutyCycle: Float, val timingsUs: IntArray) : IrCode {
        override val usesToggle: Boolean get() = false

        override fun toSignal(toggle: Boolean): IrSignal {
            val clean = PatternSanitizer.sanitize(timingsUs)
            require(clean.isNotEmpty()) { "raw capture has no marks" }
            return IrSignal(carrierHz = carrierHz, frame = clean + RAW_REPEAT_GAP_US)
        }

        override fun equals(other: Any?): Boolean =
            other is Raw && carrierHz == other.carrierHz && timingsUs.contentEquals(other.timingsUs)

        override fun hashCode(): Int = 31 * carrierHz + timingsUs.contentHashCode()

        override fun toString(): String = "Raw(${carrierHz}Hz, ${timingsUs.size} slices)"

        private companion object {
            /** Captures don't record the inter-frame gap; this is long enough for every common protocol. */
            const val RAW_REPEAT_GAP_US = 40_000
        }
    }
}
