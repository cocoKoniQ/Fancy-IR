package io.github.cocokoniq.fancyir.core.ir.engine

import io.github.cocokoniq.fancyir.core.ir.IrPattern

/** The only seam between the pure IR core and the platform (`ConsumerIrManager`). */
interface IrTransmitter {
    val capabilities: IrCapabilities

    /** Suspends until the pattern has been emitted; implementations serialise calls. */
    suspend fun transmit(pattern: IrPattern)
}

data class IrCapabilities(
    val hasEmitter: Boolean,
    /** Supported carrier ranges in Hz; empty means "unknown, try anything". */
    val carrierRangesHz: List<IntRange>,
) {
    /** The requested carrier if supported, otherwise the nearest supported bound. */
    fun clampCarrier(hz: Int): Int {
        if (carrierRangesHz.isEmpty() || carrierRangesHz.any { hz in it }) return hz
        return carrierRangesHz
            .flatMap { listOf(it.first, it.last) }
            .minBy { kotlin.math.abs(it - hz) }
    }

    companion object {
        val NONE = IrCapabilities(hasEmitter = false, carrierRangesHz = emptyList())
    }
}

/** Records instead of emitting: JVM tests, demo mode on phones without an emitter. */
class RecordingIrTransmitter(
    override val capabilities: IrCapabilities = IrCapabilities(hasEmitter = true, carrierRangesHz = emptyList()),
) : IrTransmitter {
    private val _sent = mutableListOf<IrPattern>()
    val sent: List<IrPattern> get() = synchronized(_sent) { _sent.toList() }

    override suspend fun transmit(pattern: IrPattern) {
        synchronized(_sent) { _sent += pattern }
    }
}
