package io.github.cocokoniq.fancyir.core.irdb.catalog

import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.irdb.Codeset
import io.github.cocokoniq.fancyir.core.irdb.Command
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory

/** A stateless remote (one code per button) or a stateful climate table (one code per state). */
enum class CodesetKind { COMMANDS, CLIMATE }

/** Where a code set came from; drives provenance, licence notices and refresh policy. */
enum class CatalogSource(val tag: String) {
    STARTER("starter"),
    FLIPPER_IRDB("flipper-irdb"),
    SMARTIR("smartir"),
    USER("user");

    companion object {
        fun fromTag(tag: String): CatalogSource = entries.first { it.tag == tag }
    }
}

/** Stateful AC: every IR frame carries the whole state, so codes are keyed by state. */
data class ClimateSpec(
    val minTempC: Int,
    val maxTempC: Int,
    val precision: Float,
    val modes: List<String>,
    val fanModes: List<String>,
    /** Empty when the remote has no swing setting in its state. */
    val swingModes: List<String> = emptyList(),
)

data class CatalogCommand(val function: String, val code: IrCode)

/**
 * One remote profile in the parametric catalog (Layer 2, ARCHITECTURE.md §3.8). [rank] orders
 * candidates for first-time recon: 1 is test-fired first.
 */
data class CatalogCodeset(
    val id: String,
    val kind: CodesetKind,
    val category: DeviceCategory,
    val brand: String,
    val label: String,
    val rank: Int,
    val source: CatalogSource,
    val sourceRef: String,
    val license: String,
    val commands: List<CatalogCommand>,
    val climate: ClimateSpec? = null,
) {
    val brandKey: String get() = BrandKey.of(brand)

    /** Semantic view for the HUD: known functions become [Command]s, the rest stay custom. */
    fun toCodeset(): Codeset {
        val codes = LinkedHashMap<Command, IrCode>()
        val custom = LinkedHashMap<String, IrCode>()
        for (command in commands) {
            val known = FunctionKey.command(command.function)
            if (known != null) codes.putIfAbsent(known, command.code) else custom.putIfAbsent(command.function, command.code)
        }
        return Codeset(codes, custom)
    }
}

/** Stable function names shared by the bundle, the database and the climate controller. */
object FunctionKey {
    private const val CUSTOM = "custom:"
    private const val CLIMATE = "climate:"
    const val CLIMATE_OFF = "climate:off"

    fun of(command: Command): String = command.name

    fun custom(name: String): String = CUSTOM + name

    /** `climate:<mode>:<fan>[:<swing>]:<temp>`, mirroring SmartIR's nesting order. */
    fun climate(path: List<String>): String = CLIMATE + path.joinToString(":")

    fun command(function: String): Command? =
        if (function.startsWith(CUSTOM) || function.startsWith(CLIMATE)) null
        else Command.entries.firstOrNull { it.name == function }

    fun isClimate(function: String): Boolean = function.startsWith(CLIMATE)

    fun displayName(function: String): String = when {
        function.startsWith(CUSTOM) -> function.removePrefix(CUSTOM)
        else -> function.replace('_', ' ')
    }
}

/** Case-, space- and punctuation-insensitive brand identity ("Mitsubishi_Electric" == "mitsubishi electric"). */
object BrandKey {
    fun of(brand: String): String = brand.lowercase().filter { it.isLetterOrDigit() }
}
