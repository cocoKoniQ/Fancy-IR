package io.github.cocokoniq.fancyir.core.data.catalog

import io.github.cocokoniq.fancyir.core.data.settings.SettingsStore
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCodeset
import io.github.cocokoniq.fancyir.core.irdb.remote.FlipperIrdbRemote
import io.github.cocokoniq.fancyir.core.irdb.remote.RemoteIrdbException
import io.github.cocokoniq.fancyir.core.irdb.remote.SmartIrRemote

/** One Layer 3 source: GitHub-hosted, community-maintained, no backend of our own. */
fun interface RemoteCodesetSource {
    suspend fun fetch(category: DeviceCategory, brand: String): List<CatalogCodeset>
}

fun FlipperIrdbRemote.asSource() = RemoteCodesetSource { category, brand -> codesets(category, brand) }

fun SmartIrRemote.asSource() = RemoteCodesetSource { category, brand -> codesets(category, brand) }

sealed interface CandidateLookup {
    val codesets: List<CatalogCodeset>

    /** Answered from Layer 2 (bundled or previously cached). */
    data class Local(override val codesets: List<CatalogCodeset>) : CandidateLookup

    /** Layer 2 had nothing; Layer 3 found these and they are now cached. */
    data class Fetched(override val codesets: List<CatalogCodeset>) : CandidateLookup

    /** Nothing locally and the network lookup failed or is disabled. */
    data class Unavailable(val reason: String, val rateLimited: Boolean = false) : CandidateLookup {
        override val codesets: List<CatalogCodeset> get() = emptyList()
    }
}

/**
 * The 3-layer lookup (ARCHITECTURE.md §3.8). Layer 1 (the vault) is consulted by the HUD
 * directly; this resolves first-time recon candidates from Layer 2, falling back to Layer 3
 * and caching what it finds so the same brand works offline next time.
 */
class HybridIrStore(
    private val catalog: CatalogRepository,
    private val remotes: List<RemoteCodesetSource>,
    private val settings: SettingsStore,
) {
    suspend fun candidates(category: DeviceCategory, brand: String, allowRemote: Boolean = true): CandidateLookup {
        val local = catalog.candidates(category, brand)
        if (local.isNotEmpty()) return CandidateLookup.Local(local)
        if (!allowRemote || !settings.getBoolean(SettingsStore.REMOTE_LOOKUP_ENABLED, default = true)) {
            return CandidateLookup.Unavailable("not in the offline catalog")
        }
        return fetchRemote(category, brand)
    }

    /**
     * Asks Layer 3 even when Layer 2 has candidates — used when none of the bundled code sets
     * made the device react. New finds are cached, and only those are returned.
     */
    suspend fun fetchRemote(category: DeviceCategory, brand: String): CandidateLookup {
        val known = catalog.candidates(category, brand).map { it.id }.toSet()
        val found = ArrayList<CatalogCodeset>()
        var failure: RemoteIrdbException? = null
        for (remote in remotes) {
            try {
                found += remote.fetch(category, brand)
            } catch (e: RemoteIrdbException) {
                failure = failure ?: e
            }
        }
        val fresh = found.distinctBy { it.id }.filterNot { it.id in known }
        if (fresh.isEmpty()) {
            return CandidateLookup.Unavailable(failure?.message ?: "no new code sets found online", rateLimited = failure?.isRateLimited == true)
        }
        catalog.cacheRemote(fresh)
        // Only the newcomers: whatever was already known has just been tried.
        return CandidateLookup.Fetched(catalog.candidates(category, brand).filterNot { it.id in known })
    }
}
