package io.github.cocokoniq.fancyir.core.data.db

import io.github.cocokoniq.fancyir.core.ir.CompiledCommand
import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.IrProtocol
import io.github.cocokoniq.fancyir.core.ir.TimingCodec
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCodeset
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCommand
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogSource
import io.github.cocokoniq.fancyir.core.irdb.catalog.ClimateSpec
import io.github.cocokoniq.fancyir.core.irdb.catalog.CodesetKind
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object Mappers {

    fun toEntity(set: CatalogCodeset, origin: CatalogOrigin, rank: Int = set.rank, nowMs: Long): CatalogCodesetEntity =
        CatalogCodesetEntity(
            id = set.id,
            kind = set.kind.name,
            category = set.category.name,
            brand = set.brand,
            brandKey = set.brandKey,
            label = set.label,
            rank = rank,
            source = set.source.tag,
            sourceRef = set.sourceRef,
            license = set.license,
            origin = origin.name,
            climate = set.climate?.let(::encodeClimate),
            addedAtMs = nowMs,
        )

    fun commandEntities(set: CatalogCodeset): List<CatalogCommandEntity> = set.commands.map { command ->
        when (val code = command.code) {
            is IrCode.Parsed -> CatalogCommandEntity(set.id, command.function, code.protocol.flipperName, code.address, code.command, null, null, null)
            is IrCode.Raw -> CatalogCommandEntity(set.id, command.function, null, null, null, code.carrierHz, code.dutyCycle, TimingCodec.encode(code.timingsUs))
        }
    }

    fun toCodeset(entity: CatalogCodesetEntity, commands: List<CatalogCommandEntity>): CatalogCodeset = CatalogCodeset(
        id = entity.id,
        kind = CodesetKind.valueOf(entity.kind),
        category = DeviceCategory.valueOf(entity.category),
        brand = entity.brand,
        label = entity.label,
        rank = entity.rank,
        source = CatalogSource.fromTag(entity.source),
        sourceRef = entity.sourceRef,
        license = entity.license,
        commands = commands.mapNotNull { row -> decodeCode(row.protocol, row.address, row.command, row.carrierHz, row.dutyCycle, row.timings)?.let { CatalogCommand(row.function, it) } },
        climate = entity.climate?.let(::decodeClimate),
    )

    fun vaultCommand(deviceId: String, function: String, code: IrCode): VaultCommandEntity {
        val compiled = CompiledCommand.compile(code)
        return when (code) {
            is IrCode.Parsed -> VaultCommandEntity(deviceId, function, compiled.carrierHz, TimingCodec.encode(compiled.press),
                compiled.togglePress?.let(TimingCodec::encode), code.protocol.flipperName, code.address, code.command, null, null)
            is IrCode.Raw -> VaultCommandEntity(deviceId, function, compiled.carrierHz, TimingCodec.encode(compiled.press),
                compiled.togglePress?.let(TimingCodec::encode), null, null, null, code.dutyCycle, TimingCodec.encode(code.timingsUs))
        }
    }

    fun compiled(entity: VaultCommandEntity): CompiledCommand =
        CompiledCommand(entity.carrierHz, TimingCodec.decode(entity.press), entity.togglePress?.let(TimingCodec::decode))

    fun sourceCode(entity: VaultCommandEntity): IrCode? =
        decodeCode(entity.protocol, entity.address, entity.command, entity.carrierHz, entity.dutyCycle, entity.timings)

    private fun decodeCode(protocol: String?, address: Long?, command: Long?, carrierHz: Int?, dutyCycle: Float?, timings: ByteArray?): IrCode? =
        if (protocol != null) {
            IrProtocol.fromFlipperName(protocol)?.let { IrCode.Parsed(it, address ?: 0, command ?: 0) }
        } else {
            timings?.let { IrCode.Raw(carrierHz ?: 38_000, dutyCycle ?: 0.33f, TimingCodec.decode(it)) }
        }

    fun encodeClimate(spec: ClimateSpec): String = listOf(
        spec.minTempC.toString(), spec.maxTempC.toString(), spec.precision.toString(),
        spec.modes.joinToString(","), spec.fanModes.joinToString(","), spec.swingModes.joinToString(","),
    ).joinToString("|")

    fun decodeClimate(text: String): ClimateSpec {
        val f = text.split('|')
        fun csv(i: Int) = f.getOrElse(i) { "" }.let { if (it.isEmpty()) emptyList() else it.split(',') }
        return ClimateSpec(f[0].toInt(), f[1].toInt(), f[2].toFloat(), csv(3), csv(4), csv(5))
    }

    fun encodeVector(vector: FloatArray): ByteArray =
        ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(vector) }.array()

    fun decodeVector(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }
}
