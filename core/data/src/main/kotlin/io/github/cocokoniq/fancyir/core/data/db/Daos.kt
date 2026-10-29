package io.github.cocokoniq.fancyir.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A brand present in the catalog for one category. */
class BrandRow(val brand: String, val brandKey: String, val codesets: Int)

class CategoryRow(val category: String, val codesets: Int)

@Dao
interface CatalogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCodesets(codesets: List<CatalogCodesetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommands(commands: List<CatalogCommandEntity>)

    @Query("SELECT * FROM catalog_codesets WHERE category = :category AND brandKey = :brandKey ORDER BY rank, id")
    suspend fun candidates(category: String, brandKey: String): List<CatalogCodesetEntity>

    @Query("SELECT * FROM catalog_codesets WHERE id = :id")
    suspend fun codeset(id: String): CatalogCodesetEntity?

    @Query("SELECT * FROM catalog_commands WHERE codesetId = :codesetId")
    suspend fun commands(codesetId: String): List<CatalogCommandEntity>

    @Query(
        "SELECT brand, brandKey, COUNT(*) AS codesets FROM catalog_codesets WHERE category = :category " +
            "GROUP BY brandKey ORDER BY codesets DESC, brand",
    )
    suspend fun brands(category: String): List<BrandRow>

    @Query("SELECT category, COUNT(*) AS codesets FROM catalog_codesets GROUP BY category ORDER BY codesets DESC")
    suspend fun categories(): List<CategoryRow>

    @Query("SELECT COALESCE(MAX(rank), 0) FROM catalog_codesets WHERE category = :category AND brandKey = :brandKey")
    suspend fun maxRank(category: String, brandKey: String): Int

    @Query("SELECT COUNT(*) FROM catalog_codesets WHERE origin = :origin")
    suspend fun count(origin: String): Int

    @Query("DELETE FROM catalog_codesets WHERE origin = :origin")
    suspend fun deleteOrigin(origin: String)
}

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_devices ORDER BY lastUsedAtMs DESC")
    fun observeDevices(): Flow<List<VaultDeviceEntity>>

    @Query("SELECT * FROM vault_devices ORDER BY lastUsedAtMs DESC")
    suspend fun devices(): List<VaultDeviceEntity>

    @Query("SELECT * FROM vault_commands")
    suspend fun commands(): List<VaultCommandEntity>

    @Query("SELECT * FROM vault_embeddings ORDER BY capturedAtMs")
    suspend fun embeddings(): List<VaultEmbeddingEntity>

    @Query("SELECT * FROM vault_anchors")
    suspend fun anchors(): List<VaultAnchorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevice(device: VaultDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommands(commands: List<VaultCommandEntity>)

    @Insert
    suspend fun insertEmbedding(embedding: VaultEmbeddingEntity)

    @Query(
        "DELETE FROM vault_embeddings WHERE deviceId = :deviceId AND id NOT IN " +
            "(SELECT id FROM vault_embeddings WHERE deviceId = :deviceId ORDER BY capturedAtMs DESC LIMIT :keep)",
    )
    suspend fun trimEmbeddings(deviceId: String, keep: Int)

    @Query("DELETE FROM vault_anchors WHERE deviceId = :deviceId")
    suspend fun deleteAnchors(deviceId: String)

    @Insert
    suspend fun insertAnchors(anchors: List<VaultAnchorEntity>)

    @Transaction
    suspend fun replaceAnchors(deviceId: String, anchors: List<VaultAnchorEntity>) {
        deleteAnchors(deviceId)
        insertAnchors(anchors)
    }

    @Query("UPDATE vault_devices SET lastUsedAtMs = :atMs WHERE deviceId = :deviceId")
    suspend fun touch(deviceId: String, atMs: Long)

    @Query("UPDATE vault_devices SET label = :label WHERE deviceId = :deviceId")
    suspend fun rename(deviceId: String, label: String)

    @Query("DELETE FROM vault_devices WHERE deviceId = :deviceId")
    suspend fun delete(deviceId: String)

    @Query("SELECT * FROM climate_states WHERE deviceId = :deviceId")
    suspend fun climateState(deviceId: String): ClimateStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveClimateState(state: ClimateStateEntity)
}

@Dao
interface SettingsDao {

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun remove(key: String)
}
