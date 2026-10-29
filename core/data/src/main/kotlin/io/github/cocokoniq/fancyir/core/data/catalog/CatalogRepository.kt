package io.github.cocokoniq.fancyir.core.data.catalog

import androidx.room.withTransaction
import io.github.cocokoniq.fancyir.core.data.db.CatalogOrigin
import io.github.cocokoniq.fancyir.core.data.db.FancyIrDatabase
import io.github.cocokoniq.fancyir.core.data.db.Mappers
import io.github.cocokoniq.fancyir.core.data.settings.SettingsStore
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.catalog.BrandKey
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogBundle
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCodeset
import java.io.BufferedReader

/** The bundled catalog as shipped in the APK: its revision and a reader over its contents. */
interface CatalogAsset {
    val revision: String

    fun open(): BufferedReader
}

data class CatalogBrand(val name: String, val key: String, val codesets: Int)

/**
 * Layer 2 (ARCHITECTURE.md §3.8): the offline parametric catalog. The bundled asset is streamed
 * into Room once per revision; Layer 3 fetches are cached here permanently, ranked after the
 * bundled code sets of the same brand.
 */
class CatalogRepository(
    private val db: FancyIrDatabase,
    private val settings: SettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.catalog()

    /** Seeds or re-seeds the bundled rows when the asset revision changed; returns the code sets written. */
    suspend fun ensureSeeded(asset: CatalogAsset): Int {
        if (settings.get(SettingsStore.CATALOG_REVISION) == asset.revision && dao.count(CatalogOrigin.BUNDLED.name) > 0) return 0
        var written = 0
        db.withTransaction {
            dao.deleteOrigin(CatalogOrigin.BUNDLED.name)
            asset.open().use { reader ->
                CatalogBundle.read(reader).chunked(SEED_BATCH).forEach { batch ->
                    dao.insertCodesets(batch.map { Mappers.toEntity(it, CatalogOrigin.BUNDLED, nowMs = clock()) })
                    dao.insertCommands(batch.flatMap(Mappers::commandEntities))
                    written += batch.size
                }
            }
        }
        settings.put(SettingsStore.CATALOG_REVISION, asset.revision)
        return written
    }

    suspend fun brands(category: DeviceCategory): List<CatalogBrand> =
        dao.brands(category.name).map { CatalogBrand(it.brand, it.brandKey, it.codesets) }

    suspend fun categories(): Map<DeviceCategory, Int> =
        dao.categories().associate { DeviceCategory.valueOf(it.category) to it.codesets }

    /** Code sets for (category, brand), best rank first, with their commands. */
    suspend fun candidates(category: DeviceCategory, brand: String): List<CatalogCodeset> =
        dao.candidates(category.name, BrandKey.of(brand)).map { Mappers.toCodeset(it, dao.commands(it.id)) }

    suspend fun codeset(id: String): CatalogCodeset? = dao.codeset(id)?.let { Mappers.toCodeset(it, dao.commands(id)) }

    /** Caches Layer 3 results; they rank after whatever is already known for that brand. */
    suspend fun cacheRemote(codesets: List<CatalogCodeset>, origin: CatalogOrigin = CatalogOrigin.REMOTE) {
        if (codesets.isEmpty()) return
        db.withTransaction {
            for ((key, group) in codesets.groupBy { it.category to it.brandKey }) {
                val base = dao.maxRank(key.first.name, key.second)
                val sorted = group.sortedBy { it.rank }
                dao.insertCodesets(sorted.mapIndexed { i, set -> Mappers.toEntity(set, origin, rank = base + i + 1, nowMs = clock()) })
                dao.insertCommands(sorted.flatMap(Mappers::commandEntities))
            }
        }
    }

    private companion object {
        const val SEED_BATCH = 50
    }
}
