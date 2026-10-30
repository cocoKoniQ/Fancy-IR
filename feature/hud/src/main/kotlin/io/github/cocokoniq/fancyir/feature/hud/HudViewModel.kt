package io.github.cocokoniq.fancyir.feature.hud

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.cocokoniq.fancyir.core.data.catalog.CandidateLookup
import io.github.cocokoniq.fancyir.core.data.catalog.CatalogAsset
import io.github.cocokoniq.fancyir.core.data.catalog.CatalogRepository
import io.github.cocokoniq.fancyir.core.data.catalog.HybridIrStore
import io.github.cocokoniq.fancyir.core.data.settings.SettingsStore
import io.github.cocokoniq.fancyir.core.data.vault.SpatialVault
import io.github.cocokoniq.fancyir.core.data.vault.VaultSnapshot
import io.github.cocokoniq.fancyir.core.domain.bench.IrTestBench
import io.github.cocokoniq.fancyir.core.domain.climate.ClimateController
import io.github.cocokoniq.fancyir.core.domain.climate.ClimateIntent
import io.github.cocokoniq.fancyir.core.domain.climate.ClimateState
import io.github.cocokoniq.fancyir.core.domain.control.ControlIntent
import io.github.cocokoniq.fancyir.core.domain.control.ControlMapper
import io.github.cocokoniq.fancyir.core.domain.control.ControlProfile
import io.github.cocokoniq.fancyir.core.domain.control.ControlProfiles
import io.github.cocokoniq.fancyir.core.domain.control.RadialAction
import io.github.cocokoniq.fancyir.core.domain.device.Device
import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import io.github.cocokoniq.fancyir.core.domain.gesture.Direction
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureArbiter
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureConfig
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureEvent
import io.github.cocokoniq.fancyir.core.domain.gesture.PointerInput
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterCatalog
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterMatch
import io.github.cocokoniq.fancyir.core.domain.hardware.IrMountType
import io.github.cocokoniq.fancyir.core.domain.hardware.Parallax
import io.github.cocokoniq.fancyir.core.domain.hardware.PhoneIrProfile
import io.github.cocokoniq.fancyir.core.domain.recognition.Gallery
import io.github.cocokoniq.fancyir.core.domain.recognition.ScoreFusion
import io.github.cocokoniq.fancyir.core.domain.recognition.Similarity
import io.github.cocokoniq.fancyir.core.domain.recon.ReconSession
import io.github.cocokoniq.fancyir.core.domain.spatial.AimState
import io.github.cocokoniq.fancyir.core.domain.spatial.MotionState
import io.github.cocokoniq.fancyir.core.domain.spatial.Posture
import io.github.cocokoniq.fancyir.core.domain.spatial.SpatialPrior
import io.github.cocokoniq.fancyir.core.domain.spatial.blursFrames
import io.github.cocokoniq.fancyir.core.domain.spatial.isSteady
import io.github.cocokoniq.fancyir.core.domain.targeting.LockSource
import io.github.cocokoniq.fancyir.core.domain.targeting.TargetObservation
import io.github.cocokoniq.fancyir.core.domain.targeting.TargetingMachine
import io.github.cocokoniq.fancyir.core.domain.targeting.TargetingState
import io.github.cocokoniq.fancyir.core.haptics.HapticCue
import io.github.cocokoniq.fancyir.core.haptics.HapticEngine
import io.github.cocokoniq.fancyir.core.ir.CompiledCommand
import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.PulseEnvelope
import io.github.cocokoniq.fancyir.core.ir.engine.FireOutcome
import io.github.cocokoniq.fancyir.core.ir.engine.FireRequest
import io.github.cocokoniq.fancyir.core.ir.engine.IrEngine
import io.github.cocokoniq.fancyir.core.irdb.Command
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCodeset
import io.github.cocokoniq.fancyir.core.irdb.catalog.FunctionKey
import io.github.cocokoniq.fancyir.core.sensors.AimSensor
import io.github.cocokoniq.fancyir.core.vision.FrameObservation
import io.github.cocokoniq.fancyir.core.vision.ViewfinderController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the HUD needs from the app graph. */
class HudDependencies(
    val engine: IrEngine,
    val vault: SpatialVault,
    val catalog: CatalogRepository,
    val lookup: HybridIrStore,
    val catalogAsset: CatalogAsset,
    val settings: SettingsStore,
    val haptics: HapticEngine,
    val aimSensor: AimSensor,
    val viewfinder: ViewfinderController,
    /** Automatic match of this phone in the emitter registry, if any. */
    val emitterMatch: EmitterMatch?,
    /** Display density, to size gesture detents in dp. */
    val density: Float,
)

/**
 * Single writer for the HUD (ARCHITECTURE.md §4.3): every input — touch, keys, camera frames,
 * fused sensor state, engine outcomes and finished background work — goes through one channel
 * and is reduced in order on one coroutine, then a fresh [HudUiState] is published. Database
 * and network work runs off that loop and posts its result back as an input.
 */
class HudViewModel(private val deps: HudDependencies) : ViewModel() {

    private sealed interface Input {
        data class Pointer(val input: PointerInput) : Input
        data class Frame(val frame: FrameObservation) : Input
        data class Aim(val state: AimState) : Input
        data class Focus(val distanceM: Float?) : Input
        data class Engine(val outcome: FireOutcome) : Input
        data class VolumeKey(val up: Boolean) : Input
        data object NavBack : Input
        data class Tick(val nowMs: Long) : Input
        data class Viewport(val widthPx: Float) : Input
        data class Sheet(val action: SheetAction) : Input
        data class Recon(val action: ReconAction) : Input
        data class VaultLoaded(val snapshot: VaultSnapshot) : Input
        data class BrandsLoaded(val category: DeviceCategory, val brands: List<PickerEntry>) : Input
        data class CategoriesLoaded(val counts: Map<DeviceCategory, Int>) : Input
        data class LookupDone(val category: DeviceCategory, val brand: String, val result: CandidateLookup) : Input
        data class Promoted(val device: Device) : Input
        data class Setting(val key: String, val value: String?) : Input
        data class ClimateRestored(val device: DeviceId, val state: ClimateState) : Input
        data class BenchStep(val index: Int) : Input
        data class Failed(val message: String) : Input
    }

    sealed interface SheetAction {
        data object OpenDevices : SheetAction
        data object OpenPhone : SheetAction
        data class PickDevice(val id: String) : SheetAction
        data class PickCategory(val category: DeviceCategory) : SheetAction
        data class PickBrand(val category: DeviceCategory, val brand: String) : SheetAction
        data class PickCommand(val id: String) : SheetAction
        data class PickEmitterProfile(val id: String?) : SheetAction
        data object RunTestBench : SheetAction
        data object Close : SheetAction
    }

    enum class ReconAction { YES, NO, REFIRE, SKIP_TEST, CANCEL, LOOK_ONLINE }

    private class ReconFlow(val category: DeviceCategory, val brand: String) {
        var session: ReconSession? = null
        var current: ReconSession.Step.Fire? = null
        var codesets: List<CatalogCodeset> = emptyList()
        var ui = ReconUi(title = "${brand.uppercase()} · ${category.label.uppercase()}", stage = ReconStage.LOOKING_UP, detail = "LOOKING UP CODE SETS")
        var triedOnline = false
    }

    private val inputs = Channel<Input>(Channel.UNLIMITED)
    private val _ui = MutableStateFlow(HudUiState(demoMode = !deps.engine.capabilities.hasEmitter))
    val ui: StateFlow<HudUiState> = _ui.asStateFlow()

    private val arbiter = GestureArbiter(GestureConfig.forDensity(deps.density))
    private val targeting = TargetingMachine()
    private val gallery = Gallery()
    private val prior = SpatialPrior()

    private var devices: List<Device> = emptyList()
    private var vaultLoaded = false
    private var aim: AimState? = null
    private var recent: DeviceId? = null
    private var lastFrameMs = 0L
    private var night = false
    private var navMode = false
    private var charge = 0f
    private var radial: RadialUi? = null
    private var sheet: SheetUi? = null
    private var pendingCommands: Device? = null
    private var recon: ReconFlow? = null
    private var status: String? = null
    private var statusAtMs = 0L
    private var fire: FireUi? = null
    private var fireSerial = 0
    private var painting: Pair<DeviceId, Long>? = null
    private var paintingProgress: Float? = null
    private var acquireLevel = 0
    private var lastCommandAtMs = 0L
    private var lapMode = false
    private var focusDistanceM: Float? = null
    private var viewWidthPx = 0f
    private var hintLocksSeen = Int.MAX_VALUE
    private var categoryCounts: Map<DeviceCategory, Int> = emptyMap()
    private var emitterOverride: String? = null
    private val climateStates = HashMap<DeviceId, ClimateState>()
    private val lastAnchorSaveMs = HashMap<DeviceId, Long>()
    private var benchJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            for (input in inputs) {
                if (handle(input)) _ui.value = render()
            }
        }
        viewModelScope.launch { deps.viewfinder.frames.collect { inputs.send(Input.Frame(it)) } }
        viewModelScope.launch { deps.viewfinder.focusDistanceM.collect { inputs.send(Input.Focus(it)) } }
        viewModelScope.launch { deps.aimSensor.states().collect { inputs.send(Input.Aim(it)) } }
        viewModelScope.launch { deps.engine.events.collect { inputs.send(Input.Engine(it)) } }
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                inputs.send(Input.Tick(SystemClock.uptimeMillis()))
            }
        }
        background {
            inputs.send(Input.VaultLoaded(deps.vault.load()))
            inputs.send(Input.Setting(SettingsStore.EMITTER_PROFILE, deps.settings.get(SettingsStore.EMITTER_PROFILE)))
            inputs.send(Input.Setting(SettingsStore.HINT_LOCKS_SEEN, deps.settings.get(SettingsStore.HINT_LOCKS_SEEN) ?: "0"))
            deps.catalog.ensureSeeded(deps.catalogAsset)
            inputs.send(Input.CategoriesLoaded(deps.catalog.categories()))
        }
    }

    // ---- public API (any thread) ----

    fun onPointer(input: PointerInput) {
        inputs.trySend(Input.Pointer(input))
    }

    /** Called from `dispatchKeyEvent`; returns whether the key was consumed (only while locked). */
    fun onVolumeKey(up: Boolean): Boolean {
        if (_ui.value.phase != TargetPhase.LOCKED) return false
        inputs.trySend(Input.VolumeKey(up))
        return true
    }

    fun onNavBack() {
        inputs.trySend(Input.NavBack)
    }

    fun onSheet(action: SheetAction) {
        inputs.trySend(Input.Sheet(action))
    }

    fun onRecon(action: ReconAction) {
        inputs.trySend(Input.Recon(action))
    }

    fun onViewport(widthPx: Float) {
        inputs.trySend(Input.Viewport(widthPx))
    }

    // ---- reduction (single coroutine) ----

    /** Returns true when the UI state may have changed. */
    private fun handle(input: Input): Boolean {
        when (input) {
            is Input.Pointer -> arbiter.onInput(input.input).forEach(::onGesture)
            is Input.Frame -> onFrame(input.frame)
            is Input.Aim -> return onAim(input.state)
            is Input.Focus -> focusDistanceM = input.distanceM
            is Input.Engine -> onEngine(input.outcome)
            is Input.VolumeKey -> {
                val device = lockedDevice() ?: return false
                deps.haptics.play(HapticCue.DETENT)
                send(device, ControlMapper.volumeKey(input.up, ControlProfiles.forCategory(device.category)))
            }
            Input.NavBack -> lockedDevice()?.let { send(it, ControlMapper.navBack()) }
            is Input.Tick -> onTick(input.nowMs)
            is Input.Viewport -> viewWidthPx = input.widthPx
            is Input.Sheet -> onSheetAction(input.action)
            is Input.Recon -> onReconAction(input.action)
            is Input.VaultLoaded -> restore(input.snapshot)
            is Input.BrandsLoaded -> (sheet as? SheetUi.Brands)?.takeIf { it.category == input.category }?.let {
                sheet = it.copy(brands = input.brands, loading = false)
            }
            is Input.CategoriesLoaded -> categoryCounts = input.counts
            is Input.LookupDone -> onLookupDone(input)
            is Input.Promoted -> onPromoted(input.device)
            is Input.Setting -> when (input.key) {
                SettingsStore.EMITTER_PROFILE -> emitterOverride = input.value
                SettingsStore.HINT_LOCKS_SEEN -> hintLocksSeen = input.value?.toIntOrNull() ?: 0
            }
            is Input.ClimateRestored -> climateStates.putIfAbsent(input.device, input.state)
            is Input.BenchStep -> fireBenchVector(input.index)
            is Input.Failed -> say("ERROR · ${input.message.uppercase()}")
        }
        return true
    }

    private fun restore(snapshot: VaultSnapshot) {
        vaultLoaded = true
        devices = snapshot.devices
        snapshot.embeddings.forEach { (id, vectors) -> vectors.forEach { gallery.add(id, it) } }
        snapshot.anchors.forEach { (id, anchors) -> prior.restore(id, anchors) }
        background {
            snapshot.devices.filter { it.climate != null }.forEach { device ->
                deps.vault.climateState(device.id)?.let { state -> inputs.send(Input.ClimateRestored(device.id, state)) }
            }
        }
    }

    private fun onAim(state: AimState): Boolean {
        val wasLap = lapMode
        aim = state
        lapMode = state.posture != Posture.AIMING
        if (lapMode != wasLap) {
            if (lapMode && targeting.state is TargetingState.Locked) {
                say(if (topFrameEmitter()) "TOP-EDGE IR · POINT THE TOP OF THE PHONE" else "LAP MODE · KEYS & GESTURES STILL WORK")
            }
            return true
        }
        return false
    }

    private fun onGesture(event: GestureEvent) {
        when (event) {
            GestureEvent.Began -> targeting.interactionActive = true
            GestureEvent.Ended -> {
                targeting.interactionActive = false
                charge = 0f
                radial = null
            }
            is GestureEvent.HoldStarted -> radial = RadialUi(event.x, event.y, radialLabels(), hover = null)
            is GestureEvent.Charging -> {
                if ((event.progress * 5).toInt() > (charge * 5).toInt()) deps.haptics.play(HapticCue.CHARGE_TICK)
                charge = event.progress
            }
            GestureEvent.Charged -> deps.haptics.play(HapticCue.CHARGED)
            is GestureEvent.RadialHover -> {
                radial = radial?.copy(hover = event.slot)
                if (event.slot != null) deps.haptics.play(HapticCue.DETENT)
            }
            GestureEvent.Fizzled -> deps.haptics.play(HapticCue.FIZZLE)
            is GestureEvent.Dial, is GestureEvent.Step, is GestureEvent.Flick -> deps.haptics.play(HapticCue.DETENT)
            GestureEvent.Tap, GestureEvent.PowerReleased, is GestureEvent.RadialSelected -> Unit
        }

        // While recon asks "did it react?", the verbs answer it; once it's done they control the new device.
        if (recon?.ui?.stage?.let { it != ReconStage.DONE } == true) {
            onReconGesture(event)
            return
        }
        val device = lockedDevice()
        if (device == null) {
            onGestureWithoutTarget(event)
            return
        }
        when (val intent = ControlMapper.map(event, activeProfile(device))) {
            is ControlIntent.Send -> send(device, intent)
            ControlIntent.EnterNav -> setNavMode(true)
            ControlIntent.ExitNav -> setNavMode(false)
            ControlIntent.OpenAllCommands -> openCommands(device)
            null -> Unit
        }
    }

    private fun onGestureWithoutTarget(event: GestureEvent) {
        when (event) {
            GestureEvent.Tap, GestureEvent.PowerReleased, is GestureEvent.Dial, is GestureEvent.Step ->
                say(if (devices.isEmpty()) "ADD A DEVICE TO START" else "NO TARGET · AIM AT A DEVICE")
            is GestureEvent.RadialSelected -> when (event.slot) {
                Direction.DOWN -> onSheetAction(SheetAction.OpenDevices)
                Direction.RIGHT -> onSheetAction(SheetAction.OpenPhone)
                else -> Unit
            }
            else -> Unit
        }
    }

    /** During recon the verbs answer the probe: swipe right = reacted, left = nothing, tap = again. */
    private fun onReconGesture(event: GestureEvent) {
        when (event) {
            is GestureEvent.Step -> onReconAction(if (event.steps > 0) ReconAction.YES else ReconAction.NO)
            GestureEvent.Tap -> onReconAction(ReconAction.REFIRE)
            is GestureEvent.RadialSelected -> when (event.slot) {
                Direction.DOWN -> onReconAction(ReconAction.CANCEL)
                Direction.UP -> onReconAction(ReconAction.SKIP_TEST)
                else -> Unit
            }
            else -> Unit
        }
    }

    private fun onFrame(frame: FrameObservation) {
        lastFrameMs = frame.timestampMs
        night = frame.isDark
        if (lapMode) return // the camera sees a lap or a table: keep the lock as it is
        val motion = aim?.motion ?: MotionState.STEADY

        painting?.let { (device, startedAt) ->
            if (motion.isSteady() && gallery.add(device, frame.fingerprint)) {
                background { deps.vault.addEmbedding(device, frame.fingerprint, frame.meanLuma) }
            }
            val progress = (frame.timestampMs - startedAt).toFloat() / PAINT_MS
            paintingProgress = progress.coerceAtMost(1f)
            if (progress >= 1f) finishPainting(device)
        }

        if (motion.blursFrames()) return // smeared frames would only add noise to the evidence
        val visual = gallery.similarities(frame.fingerprint).mapValues { Similarity.toScore(it.value) }
        observe(frame.timestampMs, visual, frame.salient, isVisual = !frame.isDark)
    }

    private fun finishPainting(device: DeviceId) {
        painting = null
        paintingProgress = null
        recordAnchor(device, force = true)
        // Hand the manual lock over to recognition: it re-locks visually while aimed.
        targeting.release()
        say("LEARNED · ${gallery.size(device)} VIEWS")
        deps.haptics.play(HapticCue.ACK)
    }

    private fun onTick(nowMs: Long) {
        if (status != null && nowMs - statusAtMs > STATUS_MS) status = null
        recon?.takeIf { it.ui.stage == ReconStage.DONE && nowMs - statusAtMs > RECON_DONE_MS }?.let { recon = null }
        if (lapMode) {
            if (targeting.state is TargetingState.Locked && !targeting.interactionActive && nowMs - lastCommandAtMs > LAP_HOLD_MS && lastCommandAtMs > 0) {
                targeting.release()
                say("LOCK RELEASED")
            }
            return
        }
        // No camera frames (not started yet, or permission denied): run on priors alone.
        if (nowMs - lastFrameMs > PRIOR_ONLY_AFTER_MS) observe(nowMs, emptyMap(), salient = false, isVisual = false)
    }

    private fun observe(timeMs: Long, visual: Map<DeviceId, Float>, salient: Boolean, isVisual: Boolean) {
        val state = aim
        val spatial = state?.direction?.let { prior.scores(it, state.headingConfidence) }.orEmpty()
        val weights = if (isVisual) ScoreFusion.DAYLIGHT else ScoreFusion.BLIND
        val fused = ScoreFusion.fuse(visual, spatial, recent, weights)
        val before = targeting.state
        val after = targeting.onObservation(TargetObservation(timeMs, fused, salient, isVisual))
        playTransitionCues(before, after)
    }

    private fun playTransitionCues(before: TargetingState, after: TargetingState) {
        val wasLocked = before as? TargetingState.Locked
        val isLocked = after as? TargetingState.Locked
        when {
            isLocked != null && wasLocked == null -> {
                deps.haptics.play(HapticCue.LOCK)
                recent = isLocked.device
                countLockForHints()
                // Aimed upright, a top-frame emitter fires at the ceiling: lap mode is how it reaches the device.
                if (!lapMode && topFrameEmitter()) say("LOCKED · TILT FLAT TO FIRE FROM THE TOP EDGE")
            }
            isLocked != null && wasLocked != null && isLocked.device != wasLocked.device -> {
                deps.haptics.play(HapticCue.SWITCH)
                recent = isLocked.device
                setNavMode(false)
            }
            isLocked == null && wasLocked != null -> {
                deps.haptics.play(HapticCue.LOCK_LOST)
                setNavMode(false)
            }
        }
        val level = ((after as? TargetingState.Acquiring)?.progress?.times(3))?.toInt()?.coerceAtMost(2) ?: 0
        if (level > acquireLevel) deps.haptics.play(if (level == 1) HapticCue.CANDIDATE_LOW else HapticCue.CANDIDATE_HIGH)
        acquireLevel = level
    }

    private fun countLockForHints() {
        if (hintLocksSeen >= HINT_LOCKS) return
        hintLocksSeen++
        val seen = hintLocksSeen
        background { deps.settings.put(SettingsStore.HINT_LOCKS_SEEN, seen.toString()) }
    }

    private fun onEngine(outcome: FireOutcome) {
        when (outcome) {
            is FireOutcome.Started -> {
                val envelope = PulseEnvelope.of(outcome.pattern.timingsUs, binUs = ENVELOPE_BIN_US)
                fire = FireUi(++fireSerial, outcome.request.label, envelope)
                if (recon == null && outcome.request.targetKey != RECON_TARGET) {
                    say(outcome.request.label + if (outcome.request.presses > 1) " ×${outcome.request.presses}" else "")
                }
                deps.haptics.playTexture(envelope.take(MAX_TEXTURE_BINS).toFloatArray(), ENVELOPE_BIN_US / 1_000L)
                lockedDevice()?.let { recordAnchor(it.id, force = false) }
            }
            is FireOutcome.Failed -> {
                say("FAILED · ${outcome.error.message ?: outcome.request.label}")
                deps.haptics.play(HapticCue.ERROR)
            }
            is FireOutcome.Dropped -> Unit
        }
    }

    private fun recordAnchor(device: DeviceId, force: Boolean) {
        val state = aim ?: return
        val direction = state.direction ?: return
        val anchors = prior.record(device, direction, confidence = state.headingConfidence.coerceAtLeast(0.2f))
        val now = SystemClock.uptimeMillis()
        if (force || now - (lastAnchorSaveMs[device] ?: 0L) > ANCHOR_SAVE_MS) {
            lastAnchorSaveMs[device] = now
            background { deps.vault.saveAnchors(device, anchors) }
        }
    }

    // ---- firing ----

    private fun send(device: Device, intent: ControlIntent.Send) {
        val climate = device.climate
        if (climate != null) {
            climateIntent(intent.chain)?.let { sendClimate(device, it, intent.presses) } ?: say("${intent.label} · NOT ON THIS AC")
            return
        }
        val (command, code) = device.codeset.firstAvailable(intent.chain) ?: run {
            say("${intent.label} · NOT IN CODESET")
            return
        }
        fire(device, command.name.replace('_', ' '), code, device.compiled[FunctionKey.of(command)], intent.presses, intent.urgent)
    }

    private fun sendClimate(device: Device, intent: ClimateIntent, presses: Int) {
        val spec = device.climate ?: return
        val functions = device.compiled.keys
        val controller = ClimateController(spec, functions)
        var state = climateStates[device.id] ?: controller.initial()
        repeat(presses.coerceIn(1, 10)) { state = controller.apply(state, intent) }
        val function = controller.functionFor(state) ?: run {
            say("STATE NOT IN THIS AC'S TABLE")
            return
        }
        val code = device.codeset.custom[function] ?: return
        climateStates[device.id] = state
        val saved = state
        background { deps.vault.saveClimateState(device.id, saved) }
        val label = if (state.power) "${state.mode.uppercase()} · ${state.fan.uppercase()} · ${state.tempC}°" else "AC OFF"
        fire(device, label, code, device.compiled[function], presses = 1, urgent = intent == ClimateIntent.POWER)
    }

    private fun climateIntent(chain: List<Command>): ClimateIntent? = when (chain.firstOrNull()) {
        Command.TEMP_UP, Command.VOLUME_UP -> ClimateIntent.TEMP_UP
        Command.TEMP_DOWN, Command.VOLUME_DOWN -> ClimateIntent.TEMP_DOWN
        Command.MODE -> ClimateIntent.NEXT_MODE
        Command.FAN_SPEED -> ClimateIntent.NEXT_FAN
        Command.SWING -> ClimateIntent.NEXT_SWING
        Command.POWER, Command.POWER_OFF, Command.POWER_ON -> ClimateIntent.POWER
        else -> null
    }

    private fun fire(device: Device?, label: String, code: IrCode, compiled: CompiledCommand?, presses: Int = 1, urgent: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        lastCommandAtMs = now
        deps.engine.fire(
            FireRequest(
                targetKey = device?.id?.value ?: RECON_TARGET,
                label = label,
                code = code,
                compiled = compiled,
                presses = presses,
                priority = if (urgent) FireRequest.Priority.URGENT else FireRequest.Priority.NORMAL,
                issuedAtMs = now,
            ),
        )
        device?.let { background { deps.vault.touch(it.id) } }
    }

    // ---- sheets ----

    private fun onSheetAction(action: SheetAction) {
        when (action) {
            SheetAction.OpenDevices -> sheet = SheetUi.Devices(
                pinned = devices.map { PickerEntry(it.id.value, it.name, it.category.label) },
                categories = CATEGORY_TILES.map { (category, glyph) ->
                    CategoryTile(category, category.label, glyph, categoryCounts[category] ?: 0)
                },
            )
            SheetAction.OpenPhone -> sheet = phoneSheet()
            is SheetAction.PickDevice -> pickDevice(DeviceId(action.id))
            is SheetAction.PickCategory -> {
                sheet = SheetUi.Brands(action.category, action.category.label, brands = emptyList(), loading = true)
                background {
                    val brands = deps.catalog.brands(action.category).map { PickerEntry(it.name, it.name, "${it.codesets} code sets") }
                    inputs.send(Input.BrandsLoaded(action.category, brands))
                }
            }
            is SheetAction.PickBrand -> {
                sheet = null
                startRecon(action.category, action.brand.trim())
            }
            is SheetAction.PickCommand -> pendingCommands?.let { device -> fireListed(device, action.id) }
            is SheetAction.PickEmitterProfile -> {
                emitterOverride = action.id
                sheet = phoneSheet()
                val id = action.id
                background { deps.settings.put(SettingsStore.EMITTER_PROFILE, id) }
            }
            SheetAction.RunTestBench -> {
                sheet = null
                benchJob?.cancel()
                benchJob = viewModelScope.launch(Dispatchers.Default) {
                    for (index in IrTestBench.vectors.indices) {
                        inputs.send(Input.BenchStep(index))
                        delay(IrTestBench.SPACING_MS)
                    }
                }
            }
            SheetAction.Close -> {
                sheet = null
                pendingCommands = null
            }
        }
    }

    private fun pickDevice(id: DeviceId) {
        val device = devices.firstOrNull { it.id == id } ?: return
        sheet = null
        targeting.lockManually(device.id)
        recent = device.id
        painting = device.id to SystemClock.uptimeMillis()
        paintingProgress = 0f
        say("HOLD STEADY · LEARNING ${device.name.uppercase()}")
        deps.haptics.play(HapticCue.LOCK)
    }

    private fun openCommands(device: Device) {
        pendingCommands = device
        val known = device.codeset.codes.keys.map { PickerEntry(it.name, it.name.replace('_', ' ')) }
        val custom = device.codeset.custom.keys
            .filterNot(FunctionKey::isClimate)
            .map { PickerEntry(it, FunctionKey.displayName(it).uppercase(), "EXTRA") }
        sheet = SheetUi.Commands(title = "${device.name.uppercase()} · ALL", entries = known + custom)
    }

    private fun fireListed(device: Device, id: String) {
        val command = Command.entries.firstOrNull { it.name == id }
        val function = command?.let(FunctionKey::of) ?: id
        val code = command?.let { device.codeset[it] } ?: device.codeset.custom[id] ?: return
        fire(device, FunctionKey.displayName(function).uppercase(), code, device.compiled[function])
    }

    /** The user's pick, if it still names a registry profile (v1 ids were retired by spec v2). */
    private fun chosenEmitter(): PhoneIrProfile? = emitterOverride?.let(EmitterCatalog::byId)

    /** The user's pick wins over detection; null when neither knows the phone. */
    private fun currentEmitter(): EmitterMatch? =
        chosenEmitter()?.let { EmitterMatch(it, exact = true) } ?: deps.emitterMatch

    private fun topFrameEmitter(): Boolean = currentEmitter()?.profile?.mountType == IrMountType.TOP_FRAME

    private fun phoneSheet(): SheetUi.Phone {
        val detected = chosenEmitter() == null
        val match = currentEmitter()
        val profile = match?.profile
        val rear = profile?.takeIf { it.isRearPlateau }
        return SheetUi.Phone(
            profileName = profile?.displayName,
            matchedAutomatically = detected && match != null,
            bestGuess = detected && match?.exact == false,
            topFrame = profile?.mountType == IrMountType.TOP_FRAME,
            description = profile?.description,
            offsetMm = rear?.let { it.offsetDxMm to it.offsetDyMm },
            diagram = rear?.let { PlateauDiagram(it.islandShape, it.mainLensNormX, it.mainLensNormY, it.irEmitterNormX, it.irEmitterNormY) },
            profiles = EmitterCatalog.profiles.map { PickerEntry(it.id, it.displayName, it.offsetMm?.let { mm -> "${mm.roundToInt()} mm" } ?: "TOP EDGE") },
        )
    }

    /** Hardware verification with an IR receiver (docs/testing/ir-verification.md). */
    private fun fireBenchVector(index: Int) {
        val vector = IrTestBench.vectors[index]
        fire(device = null, label = "BENCH · ${vector.label}", code = vector.code, compiled = null)
        val expect = vector.word?.let { "0x" + it.toString(16).uppercase() } ?: "${vector.bits} BITS"
        say("BENCH ${index + 1}/${IrTestBench.vectors.size} · ${vector.label.uppercase()} · EXPECT $expect")
    }

    // ---- recon ----

    private fun startRecon(category: DeviceCategory, brand: String, allowRemote: Boolean = true) {
        if (brand.isEmpty()) return
        val flow = ReconFlow(category, brand)
        recon = flow
        setNavMode(false)
        lookup(flow, allowRemote)
    }

    private fun lookup(flow: ReconFlow, allowRemote: Boolean) {
        flow.ui = flow.ui.copy(stage = ReconStage.LOOKING_UP, detail = if (allowRemote) "SEARCHING OFFLINE CATALOG, THEN ONLINE" else "SEARCHING OFFLINE CATALOG")
        background {
            val result = deps.lookup.candidates(flow.category, flow.brand, allowRemote)
            inputs.send(Input.LookupDone(flow.category, flow.brand, result))
        }
    }

    private fun onLookupDone(done: Input.LookupDone) {
        val flow = recon?.takeIf { it.category == done.category && it.brand == done.brand } ?: return
        val codesets = done.result.codesets.take(MAX_RECON_CANDIDATES)
        if (codesets.isEmpty()) {
            val unavailable = done.result as? CandidateLookup.Unavailable
            flow.ui = flow.ui.copy(
                stage = ReconStage.UNAVAILABLE,
                detail = when {
                    unavailable?.rateLimited == true -> "GITHUB RATE LIMIT · TRY AGAIN IN AN HOUR"
                    else -> "NO CODE SETS FOUND · ${unavailable?.reason?.uppercase() ?: ""}".trim()
                },
            )
            return
        }
        flow.codesets = codesets
        flow.session = ReconSession(codesets)
        if (done.result is CandidateLookup.Fetched) say("FOUND ${codesets.size} CODE SETS ONLINE · SAVED OFFLINE")
        advanceRecon(flow)
    }

    private fun advanceRecon(flow: ReconFlow) {
        val session = flow.session ?: return
        when (val step = session.next()) {
            is ReconSession.Step.Fire -> {
                flow.current = step
                flow.ui = flow.ui.copy(
                    stage = ReconStage.PROBING,
                    detail = "PROBE ${step.number} · ${step.label} · ${if (topFrameEmitter()) "TOP EDGE AT IT · " else ""}DID IT REACT?",
                    candidates = session.candidateCount,
                    probe = step.number,
                )
                fireProbe(step)
            }
            is ReconSession.Step.Found -> promote(flow, step.codeset)
            ReconSession.Step.NoMatch -> {
                flow.current = null
                flow.ui = flow.ui.copy(
                    stage = ReconStage.NO_MATCH,
                    detail = when {
                        flow.triedOnline && topFrameEmitter() -> "NOTHING REACTED · POINT THE TOP EDGE AT IT"
                        flow.triedOnline -> "NOTHING REACTED · CHECK AIM AND RANGE"
                        else -> "NOTHING REACTED · TRY ONLINE CODE SETS?"
                    },
                )
            }
        }
    }

    private fun fireProbe(step: ReconSession.Step.Fire) {
        fire(device = null, label = "PROBE · ${step.label}", code = step.probe.code, compiled = null)
    }

    private fun onReconAction(action: ReconAction) {
        val flow = recon ?: return
        when (action) {
            ReconAction.YES, ReconAction.NO -> {
                val step = flow.current ?: return
                flow.session?.answer(step.probe, reacted = action == ReconAction.YES)
                deps.haptics.play(if (action == ReconAction.YES) HapticCue.ACK else HapticCue.DETENT)
                advanceRecon(flow)
            }
            ReconAction.REFIRE -> flow.current?.let(::fireProbe)
            ReconAction.SKIP_TEST -> {
                val best = flow.session?.beliefs?.maxByOrNull { it.value }?.key
                val codeset = flow.codesets.firstOrNull { it.id == best } ?: flow.codesets.firstOrNull() ?: return
                promote(flow, codeset)
            }
            ReconAction.LOOK_ONLINE -> {
                flow.triedOnline = true
                background {
                    // Force a Layer 3 fetch even though Layer 2 had candidates that didn't react.
                    val result = deps.lookup.fetchRemote(flow.category, flow.brand)
                    inputs.send(Input.LookupDone(flow.category, flow.brand, result))
                }
                flow.ui = flow.ui.copy(stage = ReconStage.LOOKING_UP, detail = "SEARCHING GITHUB IR DATABASES")
            }
            ReconAction.CANCEL -> recon = null
        }
    }

    private fun promote(flow: ReconFlow, codeset: CatalogCodeset) {
        flow.current = null
        flow.ui = flow.ui.copy(stage = ReconStage.SAVING, detail = "SAVING ${codeset.label.uppercase()}")
        val label = "${flow.brand} ${flow.category.label}"
        background { inputs.send(Input.Promoted(deps.vault.promote(codeset, label, roomContext = null))) }
    }

    private fun onPromoted(device: Device) {
        devices = listOf(device) + devices.filterNot { it.id == device.id }
        recon?.let { it.ui = it.ui.copy(stage = ReconStage.DONE, detail = "${device.compiled.size} COMMANDS READY · LEARNING ITS LOOK") }
        deps.haptics.play(HapticCue.LOCK)
        pickDevice(device.id)
        statusAtMs = SystemClock.uptimeMillis()
    }

    // ---- helpers ----

    private fun say(text: String) {
        status = text
        statusAtMs = SystemClock.uptimeMillis()
    }

    private fun setNavMode(enabled: Boolean) {
        navMode = enabled
        arbiter.navMode = enabled
    }

    private fun lockedDevice(): Device? =
        (targeting.state as? TargetingState.Locked)?.let { locked -> devices.firstOrNull { it.id == locked.device } }

    private fun activeProfile(device: Device): ControlProfile =
        if (navMode) ControlProfiles.nav else ControlProfiles.forCategory(device.category)

    private fun radialLabels(): Map<Direction, String> {
        if (recon?.ui?.stage?.let { it != ReconStage.DONE } == true) return mapOf(Direction.UP to "SKIP TEST", Direction.DOWN to "CANCEL")
        val device = lockedDevice() ?: return mapOf(Direction.DOWN to "DEVICES", Direction.RIGHT to "MY PHONE")
        return activeProfile(device).radial.mapValues { (_, action) ->
            when (action) {
                is RadialAction.Send -> action.label
                RadialAction.EnterNav -> "NAV"
                RadialAction.ExitNav -> "EXIT NAV"
                RadialAction.AllCommands -> "ALL"
            }
        }
    }

    private fun background(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { block() }.onFailure { error ->
                inputs.send(Input.Failed(error.message ?: error.javaClass.simpleName))
            }
        }
    }

    private fun render(): HudUiState {
        val state = targeting.state
        val locked = state as? TargetingState.Locked
        val device = locked?.let { l -> devices.firstOrNull { it.id == l.device } }
        val profile = device?.let(::activeProfile)
        val acquiring = state as? TargetingState.Acquiring
        val aimState = aim
        val direction = aimState?.direction
        return HudUiState(
            phase = when (state) {
                TargetingState.Scanning -> TargetPhase.SCANNING
                is TargetingState.Acquiring -> TargetPhase.ACQUIRING
                is TargetingState.Locked -> TargetPhase.LOCKED
                TargetingState.Unrecognized -> TargetPhase.UNRECOGNIZED
            },
            lockSource = locked?.source,
            targetName = device?.name,
            targetCategory = device?.category?.label?.uppercase(),
            confidence = locked?.confidence ?: acquiring?.progress ?: 0f,
            hint = acquiring?.let { a -> devices.firstOrNull { it.id == a.device }?.name },
            dialLabel = if (device?.climate != null) "TEMP" else profile?.dialLabel,
            stepLabel = if (device?.climate != null) "MODE" else profile?.stepLabel,
            showGestureHints = locked != null && hintLocksSeen < HINT_LOCKS,
            navMode = navMode,
            charge = charge,
            radial = radial,
            status = status,
            fire = fire,
            painting = paintingProgress,
            sheet = sheet,
            recon = recon?.ui,
            climate = device?.takeIf { it.climate != null }?.let { d ->
                val climateState = climateStates[d.id] ?: ClimateController(d.climate!!, d.compiled.keys).initial()
                ClimateUi(climateState.power, climateState.mode, climateState.fan, climateState.tempC)
            },
            lapMode = lapMode && locked != null,
            beamMarker = beamMarker(),
            demoMode = !deps.engine.capabilities.hasEmitter,
            night = night,
            radar = if (direction == null) emptyList() else prior.bearings(direction).mapNotNull { bearing ->
                devices.firstOrNull { it.id == bearing.device }?.let {
                    RadarBlip(it.name.uppercase(), bearing.relativeAzimuthDeg, locked?.device == bearing.device)
                }
            },
            vaultEmpty = vaultLoaded && devices.isEmpty() && recon == null,
            headingConfidence = aimState?.headingConfidence ?: 0f,
            motion = aimState?.motion ?: MotionState.STEADY,
        )
    }

    private fun beamMarker(): BeamMarker? {
        val distance = focusDistanceM?.takeIf { it < Parallax.POINT_BLANK_M } ?: return null
        // A top-frame emitter doesn't fire along the camera axis: there is no beam to mark.
        val profile = currentEmitter()?.profile?.takeIf { it.isRearPlateau } ?: return null
        if (viewWidthPx <= 0f) return null
        val (dx, dy) = Parallax.screenOffsetPx(profile.offsetDxMm, profile.offsetDyMm, distance, deps.viewfinder.horizontalFovDeg, viewWidthPx) ?: return null
        return BeamMarker(dx, dy, distance)
    }

    companion object {
        private const val TICK_MS = 100L
        private const val STATUS_MS = 2_500L
        private const val RECON_DONE_MS = 3_000L
        private const val PRIOR_ONLY_AFTER_MS = 300L
        private const val PAINT_MS = 2_500f
        private const val LAP_HOLD_MS = 8_000L
        private const val ANCHOR_SAVE_MS = 5_000L
        private const val HINT_LOCKS = 5
        private const val MAX_RECON_CANDIDATES = 12
        private const val ENVELOPE_BIN_US = 5_000
        private const val MAX_TEXTURE_BINS = 24
        private const val RECON_TARGET = "recon"

        /** The categories offered when adding a device (the "80/20" set of the catalog). */
        private val CATEGORY_TILES = listOf(
            DeviceCategory.TV to "▭",
            DeviceCategory.AIR_CONDITIONER to "❄",
            DeviceCategory.FAN to "✣",
            DeviceCategory.SOUNDBAR to "▬",
            DeviceCategory.AV_RECEIVER to "◫",
            DeviceCategory.PROJECTOR to "◐",
            DeviceCategory.MONITOR to "▢",
            DeviceCategory.LIGHT to "✺",
            DeviceCategory.STREAMING_DEVICE to "▷",
            DeviceCategory.SPEAKER to "◉",
        )

        fun factory(deps: HudDependencies): ViewModelProvider.Factory = viewModelFactory {
            initializer { HudViewModel(deps) }
        }
    }
}

/** A lock inferred from priors alone (dark room, camera not running yet): it controls, but is styled as a guess. */
internal val HudUiState.isPredicted: Boolean get() = lockSource == LockSource.PREDICTED
