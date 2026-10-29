package io.github.cocokoniq.fancyir.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ---- Layer 2: parametric catalog -------------------------------------------------------------

/** Where a catalog row came from: the bundled asset (re-seeded on upgrade), a Layer 3 fetch, or the user. */
enum class CatalogOrigin { BUNDLED, REMOTE, USER }

@Entity(
    tableName = "catalog_codesets",
    indices = [Index(value = ["category", "brandKey", "rank"]), Index(value = ["origin"])],
)
class CatalogCodesetEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val category: String,
    val brand: String,
    val brandKey: String,
    val label: String,
    /** 1 = test-fire first during recon. */
    val rank: Int,
    val source: String,
    val sourceRef: String,
    val license: String,
    val origin: String,
    /** `min|max|precision|modes|fans|swings` for climate tables, else null. */
    val climate: String?,
    val addedAtMs: Long,
)

@Entity(
    tableName = "catalog_commands",
    primaryKeys = ["codesetId", "function"],
    foreignKeys = [ForeignKey(CatalogCodesetEntity::class, ["id"], ["codesetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("codesetId")],
)
class CatalogCommandEntity(
    val codesetId: String,
    val function: String,
    /** Flipper protocol name for parsed codes; null for raw captures. */
    val protocol: String?,
    val address: Long?,
    val command: Long?,
    val carrierHz: Int?,
    val dutyCycle: Float?,
    /** Varint-encoded µs timings for raw captures (`TimingCodec`). */
    val timings: ByteArray?,
)

// ---- Layer 1: the spatial vault ----------------------------------------------------------------

@Entity(tableName = "vault_devices")
class VaultDeviceEntity(
    @PrimaryKey val deviceId: String,
    val label: String,
    val category: String,
    val brand: String,
    val kind: String,
    val codesetId: String?,
    /** Room context (Wi-Fi BSSID) where the device was set up; null when unknown. */
    val roomContext: String?,
    val createdAtMs: Long,
    val lastUsedAtMs: Long,
    val climate: String?,
)

/** Pre-compiled transmit buffers plus the source code they came from (for export and re-compiling). */
@Entity(
    tableName = "vault_commands",
    primaryKeys = ["deviceId", "function"],
    foreignKeys = [ForeignKey(VaultDeviceEntity::class, ["deviceId"], ["deviceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("deviceId")],
)
class VaultCommandEntity(
    val deviceId: String,
    val function: String,
    val carrierHz: Int,
    val press: ByteArray,
    val togglePress: ByteArray?,
    val protocol: String?,
    val address: Long?,
    val command: Long?,
    val dutyCycle: Float?,
    val timings: ByteArray?,
)

/** Gallery exemplars: fingerprints (float32 little-endian), never images. */
@Entity(
    tableName = "vault_embeddings",
    foreignKeys = [ForeignKey(VaultDeviceEntity::class, ["deviceId"], ["deviceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("deviceId")],
)
class VaultEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val vector: ByteArray,
    val meanLuma: Float,
    val capturedAtMs: Long,
)

/** Spatial anchors: where the device was when you fired at it. */
@Entity(
    tableName = "vault_anchors",
    foreignKeys = [ForeignKey(VaultDeviceEntity::class, ["deviceId"], ["deviceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("deviceId")],
)
class VaultAnchorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val azimuthDeg: Float,
    val elevationDeg: Float,
    val weight: Int,
    val confidence: Float,
    val context: String?,
    val updatedAtMs: Long,
)

/** Shadow state of a state-table AC. */
@Entity(
    tableName = "climate_states",
    foreignKeys = [ForeignKey(VaultDeviceEntity::class, ["deviceId"], ["deviceId"], onDelete = ForeignKey.CASCADE)],
)
class ClimateStateEntity(
    @PrimaryKey val deviceId: String,
    val power: Boolean,
    val mode: String,
    val fan: String,
    val swing: String?,
    val tempC: Int,
    val updatedAtMs: Long,
)

// ---- Variables ---------------------------------------------------------------------------------

/** Key-value app variables: chosen emitter profile, calibration, feature flags, catalog revision. */
@Entity(tableName = "settings")
class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAtMs: Long,
)
