package io.github.cocokoniq.fancyir

import android.content.Context
import io.github.cocokoniq.fancyir.core.domain.device.Device
import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import io.github.cocokoniq.fancyir.core.domain.device.DeviceRepository
import io.github.cocokoniq.fancyir.core.domain.device.InMemoryDeviceRepository
import io.github.cocokoniq.fancyir.core.domain.recognition.BaselineFingerprinter
import io.github.cocokoniq.fancyir.core.haptics.HapticEngine
import io.github.cocokoniq.fancyir.core.ir.engine.IrEngine
import io.github.cocokoniq.fancyir.core.ir.engine.IrTransmitter
import io.github.cocokoniq.fancyir.core.ir.engine.RecordingIrTransmitter
import io.github.cocokoniq.fancyir.core.irdb.Codeset
import io.github.cocokoniq.fancyir.core.irdb.StarterLibrary
import io.github.cocokoniq.fancyir.core.sensors.AimSensor
import io.github.cocokoniq.fancyir.core.transmitter.ConsumerIrTransmitter
import io.github.cocokoniq.fancyir.core.vision.ViewfinderController
import io.github.cocokoniq.fancyir.feature.hud.HudDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph (ARCHITECTURE.md §4.1): the only place that knows every
 * implementation. Small enough that a DI framework would cost more than it saves.
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Phones without an emitter get demo mode: the HUD works, patterns are recorded. */
    val transmitter: IrTransmitter = ConsumerIrTransmitter(appContext).let { ir ->
        if (ir.capabilities.hasEmitter) ir else RecordingIrTransmitter()
    }

    val engine = IrEngine(transmitter, appScope)

    val devices: DeviceRepository = InMemoryDeviceRepository(
        StarterLibrary.entries.map { entry ->
            Device(
                id = DeviceId(entry.id),
                name = entry.label,
                category = entry.category,
                brand = entry.brand,
                codeset = Codeset.from(StarterLibrary.load(entry)),
            )
        },
    )

    val haptics = HapticEngine(appContext)
    val aimSensor = AimSensor(appContext)
    val viewfinder = ViewfinderController(appContext, BaselineFingerprinter())

    fun hudDependencies(density: Float) = HudDependencies(
        engine = engine,
        devices = devices,
        haptics = haptics,
        aimSensor = aimSensor,
        viewfinder = viewfinder,
        density = density,
    )
}
