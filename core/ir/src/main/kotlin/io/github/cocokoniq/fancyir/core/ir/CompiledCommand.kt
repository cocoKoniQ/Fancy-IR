package io.github.cocokoniq.fancyir.core.ir

/**
 * A command rendered ahead of time (the Layer 1 "spatial vault" fast path, ARCHITECTURE.md §3.8):
 * one complete press per toggle phase, each ending with its inter-frame gap, so firing is only
 * array concatenation — no protocol encoding, parsing or database access on the fire path.
 */
class CompiledCommand(
    val carrierHz: Int,
    /** One press with the toggle bit clear (or the only form, for protocols without a toggle). */
    val press: IntArray,
    /** The same press with the toggle bit set; RC5/RC6 only. */
    val togglePress: IntArray? = null,
) {
    init {
        require(press.isNotEmpty() && press.size % 2 == 0) { "press must end with a gap" }
        require(togglePress == null || (togglePress.isNotEmpty() && togglePress.size % 2 == 0))
    }

    val usesToggle: Boolean get() = togglePress != null

    /** Slices for [count] presses; [toggles] gives the toggle phase of each press (ignored without one). */
    fun render(count: Int, toggles: (Int) -> Boolean = { false }): IntArray {
        require(count >= 1)
        val parts = List(count) { if (togglePress != null && toggles(it)) togglePress else press }
        val out = IntArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }

    override fun equals(other: Any?): Boolean =
        other is CompiledCommand && carrierHz == other.carrierHz &&
            press.contentEquals(other.press) && togglePress.contentEquals(other.togglePress)

    override fun hashCode(): Int = 31 * (31 * carrierHz + press.contentHashCode()) + togglePress.contentHashCode()

    companion object {
        fun compile(code: IrCode): CompiledCommand {
            val plain = code.toSignal(toggle = false)
            val toggled = if (code.usesToggle) code.toSignal(toggle = true).press() else null
            return CompiledCommand(plain.carrierHz, plain.press(), toggled)
        }
    }
}
