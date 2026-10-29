package io.github.cocokoniq.fancyir.core.domain.device

import io.github.cocokoniq.fancyir.core.ir.CompiledCommand
import io.github.cocokoniq.fancyir.core.irdb.Codeset
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.catalog.ClimateSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@JvmInline
value class DeviceId(val value: String)

/** A device the user owns ("pinned"): what it is and the codes it answers to. */
data class Device(
    val id: DeviceId,
    val name: String,
    val category: DeviceCategory,
    val brand: String,
    val codeset: Codeset,
    /** Pre-rendered transmit buffers keyed by `FunctionKey` — the Layer 1 fire path. */
    val compiled: Map<String, CompiledCommand> = emptyMap(),
    /** Present for state-table ACs; their codes live under `climate:` function keys. */
    val climate: ClimateSpec? = null,
    /** The Layer 2 code set this device was promoted from. */
    val codesetId: String? = null,
)

interface DeviceRepository {
    val devices: StateFlow<List<Device>>

    fun get(id: DeviceId): Device? = devices.value.firstOrNull { it.id == id }

    suspend fun upsert(device: Device)
}

/** Until Room lands in M2 (ARCHITECTURE.md §4.1). */
class InMemoryDeviceRepository(initial: List<Device> = emptyList()) : DeviceRepository {
    private val state = MutableStateFlow(initial)
    override val devices: StateFlow<List<Device>> = state.asStateFlow()

    override suspend fun upsert(device: Device) {
        state.update { list -> list.filterNot { it.id == device.id } + device }
    }
}
