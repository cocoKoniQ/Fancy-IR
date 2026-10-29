package io.github.cocokoniq.fancyir.core.irdb.catalog

import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.irdb.Codeset
import io.github.cocokoniq.fancyir.core.irdb.Command
import io.github.cocokoniq.fancyir.core.irdb.IrFile

/**
 * Groups one brand's remote files into code-set families (ARCHITECTURE.md §3.5): files whose
 * core buttons send the same codes are the same family, and a family's size is our popularity
 * proxy for ranking which code set to test-fire first.
 */
object CodesetFamilies {

    /**
     * Buttons that identify a family, in priority order. Only the first one present is used:
     * captures of the same remote often differ in which extra buttons were recorded, but its
     * power (or primary) code is what distinguishes one code family from another.
     */
    private val signatureCommands = listOf(
        Command.POWER, Command.POWER_OFF, Command.VOLUME_UP, Command.VOLUME_DOWN, Command.MUTE,
        Command.TEMP_UP, Command.TEMP_DOWN, Command.SPEED_UP, Command.OSCILLATE, Command.BRIGHTNESS_UP,
    )

    data class Member(val ref: String, val file: IrFile) {
        val codeset: Codeset by lazy { Codeset.from(file) }
    }

    data class Family(val signature: String, val members: List<Member>) {
        /** The member with the most recognised buttons stands for the family. */
        val representative: Member get() = members.maxWith(compareBy({ it.codeset.codes.size }, { -it.ref.length }))
    }

    /** Families ranked by size (descending), ties broken by representative path for stability. */
    fun rank(members: List<Member>): List<Family> = members
        .filter { it.codeset.codes.isNotEmpty() }
        .groupBy { signature(it.codeset) }
        .map { (signature, group) -> Family(signature, group.sortedBy { it.ref }) }
        .sortedWith(compareByDescending<Family> { it.members.size }.thenBy { it.representative.ref })

    fun signature(codeset: Codeset): String {
        val command = signatureCommands.firstOrNull { it in codeset } ?: codeset.codes.keys.first()
        return "${command.name}=${fingerprint(codeset.getValue(command))}"
    }

    private fun fingerprint(code: IrCode): String = when (code) {
        is IrCode.Parsed -> "${code.protocol.flipperName}:${code.address.toString(16)}:${code.command.toString(16)}"
        // Raw captures of the same button jitter by tens of µs; quantise before comparing.
        is IrCode.Raw -> "raw:" + code.timingsUs.take(24).joinToString(",") { (it / 200).toString() }
    }
}
