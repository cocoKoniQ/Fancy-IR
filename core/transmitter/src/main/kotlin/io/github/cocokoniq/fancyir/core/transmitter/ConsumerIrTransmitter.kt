package io.github.cocokoniq.fancyir.core.transmitter

import android.content.Context
import android.hardware.ConsumerIrManager
import io.github.cocokoniq.fancyir.core.ir.IrPattern
import io.github.cocokoniq.fancyir.core.ir.engine.IrCapabilities
import io.github.cocokoniq.fancyir.core.ir.engine.IrTransmitter
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * The only class that touches [ConsumerIrManager] (ARCHITECTURE.md §3.3). `transmit()` blocks
 * for the length of the pattern, so it runs on a dedicated high-priority thread.
 */
class ConsumerIrTransmitter(context: Context) : IrTransmitter {

    private val manager: ConsumerIrManager? = context.getSystemService(ConsumerIrManager::class.java)

    private val irDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fancyir-ir").apply { priority = Thread.MAX_PRIORITY }
    }.asCoroutineDispatcher()

    override val capabilities: IrCapabilities by lazy {
        val ir = manager
        if (ir == null || !ir.hasIrEmitter()) {
            IrCapabilities.NONE
        } else {
            IrCapabilities(
                hasEmitter = true,
                carrierRangesHz = ir.carrierFrequencies?.map { it.minFrequency..it.maxFrequency }.orEmpty(),
            )
        }
    }

    override suspend fun transmit(pattern: IrPattern) {
        val ir = manager?.takeIf { capabilities.hasEmitter } ?: throw IllegalStateException("No IR emitter")
        withContext(irDispatcher) { ir.transmit(pattern.carrierHz, pattern.timingsUs) }
    }
}
