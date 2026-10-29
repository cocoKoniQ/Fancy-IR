package io.github.cocokoniq.fancyir.core.data.settings

import io.github.cocokoniq.fancyir.core.data.db.FancyIrDatabase
import io.github.cocokoniq.fancyir.core.data.db.SettingEntity
import kotlinx.coroutines.flow.Flow

/** App variables in the database (ARCHITECTURE.md §3.8): typed keys over a key-value table. */
class SettingsStore(db: FancyIrDatabase, private val clock: () -> Long = System::currentTimeMillis) {

    private val dao = db.settings()

    suspend fun get(key: String): String? = dao.get(key)

    fun observe(key: String): Flow<String?> = dao.observe(key)

    suspend fun put(key: String, value: String?) {
        if (value == null) dao.remove(key) else dao.put(SettingEntity(key, value, clock()))
    }

    suspend fun getBoolean(key: String, default: Boolean): Boolean = get(key)?.toBooleanStrictOrNull() ?: default

    suspend fun getFloat(key: String): Float? = get(key)?.toFloatOrNull()

    companion object {
        /** Revision of the bundled catalog currently seeded into Room. */
        const val CATALOG_REVISION = "catalog.revision"

        /** User-chosen emitter profile id, overriding automatic device matching. */
        const val EMITTER_PROFILE = "hardware.emitterProfile"

        /** Allow Layer 3 GitHub lookups during recon. */
        const val REMOTE_LOOKUP_ENABLED = "catalog.remoteLookup"

        /** Count of locks seen; gesture hints fade out after the first few (DESIGN.md §2.3). */
        const val HINT_LOCKS_SEEN = "hud.hintLocksSeen"

        /** Heading calibration offset in degrees, applied on top of the fused azimuth. */
        const val HEADING_OFFSET_DEG = "sensors.headingOffsetDeg"
    }
}
