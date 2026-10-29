package io.github.cocokoniq.fancyir.core.data.vault

import androidx.room.withTransaction
import io.github.cocokoniq.fancyir.core.data.db.ClimateStateEntity
import io.github.cocokoniq.fancyir.core.data.db.FancyIrDatabase
import io.github.cocokoniq.fancyir.core.data.db.Mappers
import io.github.cocokoniq.fancyir.core.data.db.VaultAnchorEntity
import io.github.cocokoniq.fancyir.core.data.db.VaultCommandEntity
import io.github.cocokoniq.fancyir.core.data.db.VaultDeviceEntity
import io.github.cocokoniq.fancyir.core.data.db.VaultEmbeddingEntity
import io.github.cocokoniq.fancyir.core.domain.climate.ClimateState
import io.github.cocokoniq.fancyir.core.domain.device.Device
import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import io.github.cocokoniq.fancyir.core.domain.device.DeviceRepository
import io.github.cocokoniq.fancyir.core.domain.spatial.AimDirection
import io.github.cocokoniq.fancyir.core.domain.spatial.SpatialPrior
import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.irdb.Codeset
import io.github.cocokoniq.fancyir.core.irdb.Command
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCodeset
import io.github.cocokoniq.fancyir.core.irdb.catalog.CodesetKind
import io.github.cocokoniq.fancyir.core.irdb.catalog.FunctionKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything Layer 1 holds for one device, restored into the HUD's in-memory models at start-up. */
data class VaultSnapshot(
    val devices: List<Device>,
    val embeddings: Map<DeviceId, List<FloatArray>>,
    val anchors: Map<DeviceId, List<SpatialPrior.Anchor>>,
)

/**
 * Layer 1, the active spatial vault (ARCHITECTURE.md §3.8): devices you have locked onto, with
 * every command pre-compiled to transmit buffers, their gallery fingerprints and spatial anchors.
 * [load] pulls it all into memory once, so lock-on and firing never touch the database.
 */
class SpatialVault(
    private val db: FancyIrDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : DeviceRepository {

    private val dao = db.vault()
    private val state = MutableStateFlow<List<Device>>(emptyList())
    override val devices: StateFlow<List<Device>> = state.asStateFlow()

    suspend fun load(): VaultSnapshot {
        val commands = dao.commands().groupBy { it.deviceId }
        val loaded = dao.devices().map { entity -> toDevice(entity, commands[entity.deviceId].orEmpty()) }
        state.value = loaded
        return VaultSnapshot(
            devices = loaded,
            embeddings = dao.embeddings().groupBy({ DeviceId(it.deviceId) }, { Mappers.decodeVector(it.vector) }),
            anchors = dao.anchors().groupBy({ DeviceId(it.deviceId) }) {
                SpatialPrior.Anchor(AimDirection(it.azimuthDeg, it.elevationDeg), it.weight, it.confidence, it.context)
            },
        )
    }

    /**
     * Promotes a confirmed Layer 2 code set into the vault: compiles every command to transmit
     * buffers now, so the first fire after lock-on needs no encoding.
     */
    suspend fun promote(codeset: CatalogCodeset, label: String, roomContext: String?): Device {
        val now = clock()
        val deviceId = "dev-" + now.toString(36) + "-" + codeset.id.hashCode().toUInt().toString(36)
        val entity = VaultDeviceEntity(
            deviceId = deviceId,
            label = label,
            category = codeset.category.name,
            brand = codeset.brand,
            kind = codeset.kind.name,
            codesetId = codeset.id,
            roomContext = roomContext,
            createdAtMs = now,
            lastUsedAtMs = now,
            climate = codeset.climate?.let(Mappers::encodeClimate),
        )
        val rows = codeset.commands.mapNotNull { command ->
            runCatching { Mappers.vaultCommand(deviceId, command.function, command.code) }.getOrNull()
        }
        db.withTransaction {
            dao.upsertDevice(entity)
            dao.insertCommands(rows)
        }
        val device = toDevice(entity, rows)
        state.update { list -> listOf(device) + list.filterNot { it.id == device.id } }
        return device
    }

    override suspend fun upsert(device: Device) {
        val now = clock()
        dao.upsertDevice(
            VaultDeviceEntity(device.id.value, device.name, device.category.name, device.brand,
                if (device.climate != null) CodesetKind.CLIMATE.name else CodesetKind.COMMANDS.name,
                device.codesetId, null, now, now, device.climate?.let(Mappers::encodeClimate)),
        )
        val codes = device.codeset.codes.map { (command, code) -> FunctionKey.of(command) to code } + device.codeset.custom.toList()
        dao.insertCommands(codes.map { (function, code) -> Mappers.vaultCommand(device.id.value, function, code) })
        state.update { list -> listOf(device) + list.filterNot { it.id == device.id } }
    }

    suspend fun addEmbedding(device: DeviceId, vector: FloatArray, meanLuma: Float, keep: Int = 48) {
        dao.insertEmbedding(VaultEmbeddingEntity(deviceId = device.value, vector = Mappers.encodeVector(vector), meanLuma = meanLuma, capturedAtMs = clock()))
        dao.trimEmbeddings(device.value, keep)
    }

    suspend fun saveAnchors(device: DeviceId, anchors: List<SpatialPrior.Anchor>) {
        val now = clock()
        dao.replaceAnchors(device.value, anchors.map {
            VaultAnchorEntity(deviceId = device.value, azimuthDeg = it.direction.azimuthDeg, elevationDeg = it.direction.elevationDeg,
                weight = it.weight, confidence = it.confidence, context = it.context, updatedAtMs = now)
        })
    }

    suspend fun touch(device: DeviceId) = dao.touch(device.value, clock())

    suspend fun rename(device: DeviceId, label: String) {
        dao.rename(device.value, label)
        state.update { list -> list.map { if (it.id == device) it.copy(name = label) else it } }
    }

    suspend fun delete(device: DeviceId) {
        dao.delete(device.value)
        state.update { list -> list.filterNot { it.id == device } }
    }

    suspend fun climateState(device: DeviceId): ClimateState? = dao.climateState(device.value)?.let {
        ClimateState(it.power, it.mode, it.fan, it.swing, it.tempC)
    }

    suspend fun saveClimateState(device: DeviceId, state: ClimateState) =
        dao.saveClimateState(ClimateStateEntity(device.value, state.power, state.mode, state.fan, state.swing, state.tempC, clock()))

    private fun toDevice(entity: VaultDeviceEntity, rows: List<VaultCommandEntity>): Device {
        val codes = LinkedHashMap<Command, IrCode>()
        val custom = LinkedHashMap<String, IrCode>()
        for (row in rows) {
            val code = Mappers.sourceCode(row) ?: continue
            val command = FunctionKey.command(row.function)
            if (command != null) codes.putIfAbsent(command, code) else custom.putIfAbsent(row.function, code)
        }
        return Device(
            id = DeviceId(entity.deviceId),
            name = entity.label,
            category = DeviceCategory.valueOf(entity.category),
            brand = entity.brand,
            codeset = Codeset(codes, custom),
            compiled = rows.associate { it.function to Mappers.compiled(it) },
            climate = entity.climate?.let(Mappers::decodeClimate),
            codesetId = entity.codesetId,
        )
    }
}
