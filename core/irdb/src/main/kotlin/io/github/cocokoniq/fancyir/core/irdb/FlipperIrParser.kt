package io.github.cocokoniq.fancyir.core.irdb

import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.IrProtocol

data class NamedSignal(val name: String, val code: IrCode)

data class ParseIssue(val line: Int, val message: String)

data class IrFile(
    val filetype: String?,
    val version: Int?,
    val signals: List<NamedSignal>,
    /** Problems with individual entries; the rest of the file is still usable. */
    val issues: List<ParseIssue>,
)

/**
 * Parser for Flipper Zero `.ir` files (signal files and libraries):
 *
 * ```
 * Filetype: IR signals file
 * Version: 1
 * #
 * name: Power
 * type: parsed
 * protocol: NEC
 * address: 04 00 00 00
 * command: 08 00 00 00
 * #
 * name: Vol_up
 * type: raw
 * frequency: 38000
 * duty_cycle: 0.330000
 * data: 9024 4512 564 ...
 * ```
 *
 * Lenient by design: community files vary, so a bad entry becomes a [ParseIssue], not a failure.
 */
object FlipperIrParser {

    fun parse(text: String): IrFile {
        var filetype: String? = null
        var version: Int? = null
        val signals = ArrayList<NamedSignal>()
        val issues = ArrayList<ParseIssue>()
        var block: MutableMap<String, String>? = null
        var blockLine = 0

        fun flush() {
            val current = block ?: return
            when (val result = toSignal(current)) {
                is Result.Ok -> signals += result.signal
                is Result.Error -> issues += ParseIssue(blockLine, "'${current["name"]}': ${result.message}")
            }
            block = null
        }

        text.lineSequence().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val colon = line.indexOf(':')
            if (colon < 0) {
                issues += ParseIssue(lineNumber, "expected 'key: value'")
                return@forEachIndexed
            }
            val key = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            val current = block
            when {
                key == "filetype" -> filetype = value
                key == "version" -> version = value.toIntOrNull()
                key == "name" -> {
                    flush()
                    block = mutableMapOf("name" to value)
                    blockLine = lineNumber
                }
                current == null -> issues += ParseIssue(lineNumber, "'$key' outside a signal block")
                key == "data" -> current["data"] = listOfNotNull(current["data"], value).joinToString(" ")
                else -> current[key] = value
            }
        }
        flush()
        return IrFile(filetype, version, signals, issues)
    }

    private sealed interface Result {
        data class Ok(val signal: NamedSignal) : Result
        data class Error(val message: String) : Result
    }

    private fun toSignal(block: Map<String, String>): Result {
        val name = block.getValue("name")
        return when (val type = block["type"]?.lowercase()) {
            "parsed" -> {
                val protocolName = block["protocol"] ?: return Result.Error("missing protocol")
                val protocol = IrProtocol.fromFlipperName(protocolName)
                    ?: return Result.Error("unknown protocol $protocolName")
                val address = block["address"]?.let(::hexBytesLittleEndian) ?: return Result.Error("bad address")
                val command = block["command"]?.let(::hexBytesLittleEndian) ?: return Result.Error("bad command")
                Result.Ok(NamedSignal(name, IrCode.Parsed(protocol, address, command)))
            }
            "raw" -> {
                val frequency = block["frequency"]?.toIntOrNull() ?: return Result.Error("bad frequency")
                val dutyCycle = block["duty_cycle"]?.toFloatOrNull() ?: DEFAULT_DUTY_CYCLE
                val timings = block["data"]
                    ?.split(Regex("\\s+"))
                    ?.filter { it.isNotEmpty() }
                    ?.map { it.toIntOrNull() ?: return Result.Error("bad timing '$it'") }
                    ?: return Result.Error("missing data")
                if (timings.isEmpty()) return Result.Error("empty data")
                Result.Ok(NamedSignal(name, IrCode.Raw(frequency, dutyCycle, timings.toIntArray())))
            }
            else -> Result.Error("unknown type $type")
        }
    }

    /** "07 00 00 00" → 0x07; "00 7F 00 00" → 0x7F00. */
    private fun hexBytesLittleEndian(value: String): Long? {
        val bytes = value.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (bytes.isEmpty() || bytes.size > 8) return null
        var result = 0L
        bytes.forEachIndexed { i, byte ->
            val b = byte.toIntOrNull(16)?.takeIf { it in 0..0xFF } ?: return null
            result = result or (b.toLong() shl (8 * i))
        }
        return result
    }

    private const val DEFAULT_DUTY_CYCLE = 0.33f
}
