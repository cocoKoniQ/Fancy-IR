package io.github.cocokoniq.fancyir

import android.content.Context
import io.github.cocokoniq.fancyir.core.data.catalog.AssetCatalog
import io.github.cocokoniq.fancyir.core.data.catalog.CatalogRepository
import io.github.cocokoniq.fancyir.core.data.catalog.HybridIrStore
import io.github.cocokoniq.fancyir.core.data.catalog.asSource
import io.github.cocokoniq.fancyir.core.data.db.FancyIrDatabase
import io.github.cocokoniq.fancyir.core.data.settings.SettingsStore
import io.github.cocokoniq.fancyir.core.data.vault.SpatialVault
import io.github.cocokoniq.fancyir.core.domain.recognition.BaselineFingerprinter
import io.github.cocokoniq.fancyir.core.haptics.HapticEngine
import io.github.cocokoniq.fancyir.core.ir.engine.IrEngine
import io.github.cocokoniq.fancyir.core.ir.engine.IrTransmitter
import io.github.cocokoniq.fancyir.core.ir.engine.RecordingIrTransmitter
import io.github.cocokoniq.fancyir.core.irdb.remote.FlipperIrdbRemote
import io.github.cocokoniq.fancyir.core.irdb.remote.SmartIrRemote
import io.github.cocokoniq.fancyir.core.irdb.remote.UrlConnectionFetcher
import io.github.cocokoniq.fancyir.core.sensors.AimSensor
import io.github.cocokoniq.fancyir.core.sensors.PhoneHardware
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

    private val database by lazy { FancyIrDatabase.open(appContext) }
    val settings by lazy { SettingsStore(database) }

    /** Layer 1. */
    val vault by lazy { SpatialVault(database) }

    /** Layer 2. */
    val catalog by lazy { CatalogRepository(database, settings) }

    /** Layer 2 → Layer 3: GitHub-hosted open IR databases, fetched on demand and cached. */
    val lookup by lazy {
        val http = UrlConnectionFetcher(userAgent = "Fancy-IR (+https://github.com/cocoKoniQ/Fancy-IR)")
        HybridIrStore(catalog, listOf(FlipperIrdbRemote(http).asSource(), SmartIrRemote(http).asSource()), settings)
    }

    val haptics = HapticEngine(appContext)
    val aimSensor = AimSensor(appContext)
    val viewfinder = ViewfinderController(appContext, BaselineFingerprinter())

    fun hudDependencies(density: Float) = HudDependencies(
        engine = engine,
        vault = vault,
        catalog = catalog,
        lookup = lookup,
        catalogAsset = AssetCatalog(appContext),
        settings = settings,
        haptics = haptics,
        aimSensor = aimSensor,
        viewfinder = viewfinder,
        emitterMatch = PhoneHardware.emitterMatch(appContext),
        density = density,
    )
}
