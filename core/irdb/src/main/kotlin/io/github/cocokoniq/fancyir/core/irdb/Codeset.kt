package io.github.cocokoniq.fancyir.core.irdb

import io.github.cocokoniq.fancyir.core.ir.IrCode

/** A device's buttons: semantic commands plus whatever names we couldn't normalise. */
class Codeset(
    val codes: Map<Command, IrCode>,
    val custom: Map<String, IrCode> = emptyMap(),
) {
    operator fun get(command: Command): IrCode? = codes[command]

    operator fun contains(command: Command): Boolean = command in codes

    fun getValue(command: Command): IrCode = codes.getValue(command)

    /** The first command of [chain] this device supports, e.g. `[PLAY_PAUSE, PLAY]`. */
    fun firstAvailable(chain: List<Command>): Pair<Command, IrCode>? =
        chain.firstNotNullOfOrNull { command -> codes[command]?.let { command to it } }

    companion object {
        /** The first occurrence of a command wins, as on the physical remote's main key. */
        fun from(file: IrFile): Codeset {
            val codes = LinkedHashMap<Command, IrCode>()
            val custom = LinkedHashMap<String, IrCode>()
            for (signal in file.signals) {
                val command = CommandVocabulary.normalize(signal.name)
                if (command != null) codes.putIfAbsent(command, signal.code) else custom.putIfAbsent(signal.name, signal.code)
            }
            return Codeset(codes, custom)
        }
    }
}
