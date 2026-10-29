package io.github.cocokoniq.fancyir.core.irdb.catalog

import io.github.cocokoniq.fancyir.core.irdb.CommandVocabulary
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.IrFile
import io.github.cocokoniq.fancyir.core.irdb.IrdbPath

/** Flipper `.ir` files → catalog code sets, with family ranking per brand. */
object FlipperCatalog {

    const val LICENSE = "CC0-1.0"

    fun codeset(
        path: String,
        file: IrFile,
        rank: Int,
        source: CatalogSource = CatalogSource.FLIPPER_IRDB,
        license: String = LICENSE,
        fallbackCategory: DeviceCategory = DeviceCategory.OTHER,
    ): CatalogCodeset {
        val entry = IrdbPath.parse(path)
        val commands = LinkedHashMap<String, CatalogCommand>()
        for (signal in file.signals) {
            val function = CommandVocabulary.normalize(signal.name)?.let(FunctionKey::of) ?: FunctionKey.custom(signal.name)
            commands.putIfAbsent(function, CatalogCommand(function, signal.code))
        }
        return CatalogCodeset(
            id = "${source.tag}:$path",
            kind = CodesetKind.COMMANDS,
            category = entry?.category ?: fallbackCategory,
            brand = entry?.brand ?: "Unknown",
            label = entry?.model ?: path.substringAfterLast('/').removeSuffix(".ir"),
            rank = rank,
            source = source,
            sourceRef = path,
            license = license,
            commands = commands.values.toList(),
        )
    }

    /**
     * Ranks one brand's files into families and returns the representative of each, best first,
     * capped at [maxFamilies]. Each code set's label notes how many remotes share it.
     */
    fun rankedCodesets(
        files: List<Pair<String, IrFile>>,
        maxFamilies: Int,
        source: CatalogSource = CatalogSource.FLIPPER_IRDB,
        license: String = LICENSE,
    ): List<CatalogCodeset> = CodesetFamilies.rank(files.map { (path, file) -> CodesetFamilies.Member(path, file) })
        .take(maxFamilies)
        .mapIndexed { index, family ->
            val representative = family.representative
            val set = codeset(representative.ref, representative.file, rank = index + 1, source = source, license = license)
            if (family.members.size > 1) set.copy(label = "${set.label} (+${family.members.size - 1} similar)") else set
        }
}
