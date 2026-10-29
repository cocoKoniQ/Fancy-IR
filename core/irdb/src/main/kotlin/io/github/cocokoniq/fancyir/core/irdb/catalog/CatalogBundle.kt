package io.github.cocokoniq.fancyir.core.irdb.catalog

import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.IrProtocol
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import java.io.BufferedReader

/**
 * The bundled Layer 2 catalog as a line-oriented, tab-separated text file (gzipped in the APK).
 * Text keeps the generated asset diffable in review; the app streams it into Room on first launch.
 *
 * ```
 * #fancyir-catalog 1
 * S  id  kind  category  brand  rank  label  source  sourceRef  license
 * C  minTemp  maxTemp  precision  modes,…  fans,…  swings,…        (climate code sets only)
 * P  function  protocol  address-hex  command-hex                  (parsed code)
 * R  function  carrierHz  dutyCycle  t1 t2 t3 …                    (raw code, µs)
 * ```
 * P/R/C lines belong to the preceding S line.
 */
object CatalogBundle {

    const val FORMAT_VERSION = 1
    private const val HEADER = "#fancyir-catalog"

    fun write(codesets: Sequence<CatalogCodeset>, out: Appendable, comment: String? = null) {
        out.append("$HEADER $FORMAT_VERSION\n")
        comment?.lineSequence()?.forEach { out.append("# ").append(it).append('\n') }
        for (set in codesets) {
            out.row("S", set.id, set.kind.name, set.category.name, set.brand, set.rank.toString(), set.label,
                set.source.tag, set.sourceRef, set.license)
            set.climate?.let { c ->
                out.row("C", c.minTempC.toString(), c.maxTempC.toString(), c.precision.toString(),
                    c.modes.joinToString(","), c.fanModes.joinToString(","), c.swingModes.joinToString(","))
            }
            for (command in set.commands) {
                when (val code = command.code) {
                    is IrCode.Parsed -> out.row("P", command.function, code.protocol.flipperName,
                        code.address.toString(16), code.command.toString(16))
                    is IrCode.Raw -> out.row("R", command.function, code.carrierHz.toString(), code.dutyCycle.toString(),
                        code.timingsUs.joinToString(" "))
                }
            }
        }
    }

    /** Streams code sets; malformed lines fail loudly because the bundle is generated, not hand-written. */
    fun read(reader: BufferedReader): Sequence<CatalogCodeset> = sequence {
        val first = reader.readLine() ?: return@sequence
        require(first.startsWith(HEADER)) { "not a Fancy-IR catalog" }
        val version = first.removePrefix(HEADER).trim().toInt()
        require(version == FORMAT_VERSION) { "unsupported catalog version $version" }

        var current: Builder? = null
        var lineNumber = 1
        while (true) {
            val line = reader.readLine() ?: break
            lineNumber++
            if (line.isEmpty() || line.startsWith("#")) continue
            val f = line.split('\t')
            try {
                when (f[0]) {
                    "S" -> {
                        current?.let { yield(it.build()) }
                        current = Builder(
                            id = f[1], kind = CodesetKind.valueOf(f[2]), category = DeviceCategory.valueOf(f[3]),
                            brand = f[4], rank = f[5].toInt(), label = f[6], source = CatalogSource.fromTag(f[7]),
                            sourceRef = f[8], license = f[9],
                        )
                    }
                    "C" -> checkNotNull(current).climate = ClimateSpec(
                        minTempC = f[1].toInt(), maxTempC = f[2].toInt(), precision = f[3].toFloat(),
                        modes = f[4].csv(), fanModes = f[5].csv(), swingModes = f.getOrElse(6) { "" }.csv(),
                    )
                    "P" -> checkNotNull(current).commands += CatalogCommand(
                        f[1],
                        IrCode.Parsed(
                            protocol = requireNotNull(IrProtocol.fromFlipperName(f[2])) { "unknown protocol ${f[2]}" },
                            address = f[3].toLong(16),
                            command = f[4].toLong(16),
                        ),
                    )
                    "R" -> checkNotNull(current).commands += CatalogCommand(
                        f[1],
                        IrCode.Raw(f[2].toInt(), f[3].toFloat(), f[4].split(' ').map(String::toInt).toIntArray()),
                    )
                    else -> error("unknown record ${f[0]}")
                }
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("catalog line $lineNumber: ${e.message}", e)
            }
        }
        current?.let { yield(it.build()) }
    }

    private class Builder(
        val id: String,
        val kind: CodesetKind,
        val category: DeviceCategory,
        val brand: String,
        val rank: Int,
        val label: String,
        val source: CatalogSource,
        val sourceRef: String,
        val license: String,
    ) {
        val commands = ArrayList<CatalogCommand>()
        var climate: ClimateSpec? = null

        fun build() = CatalogCodeset(id, kind, category, brand, label, rank, source, sourceRef, license, commands.toList(), climate)
    }

    private fun Appendable.row(vararg fields: String) {
        fields.forEachIndexed { i, field ->
            if (i > 0) append('\t')
            append(field.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '))
        }
        append('\n')
    }

    private fun String.csv(): List<String> = if (isEmpty()) emptyList() else split(',')
}
