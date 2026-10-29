package io.github.cocokoniq.fancyir.feature.hud

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.cocokoniq.fancyir.core.domain.control.ControlIntent
import io.github.cocokoniq.fancyir.core.domain.control.ControlMapper
import io.github.cocokoniq.fancyir.core.domain.control.ControlProfile
import io.github.cocokoniq.fancyir.core.domain.control.ControlProfiles
import io.github.cocokoniq.fancyir.core.domain.control.RadialAction
import io.github.cocokoniq.fancyir.core.domain.device.Device
import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import io.github.cocokoniq.fancyir.core.domain.device.DeviceRepository
import io.github.cocokoniq.fancyir.core.domain.gesture.Direction
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureArbiter
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureConfig
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureEvent
import io.github.cocokoniq.fancyir.core.domain.gesture.PointerInput
import io.github.cocokoniq.fancyir.core.domain.recognition.Gallery
import io.github.cocokoniq.fancyir.core.domain.recognition.ScoreFusion
import io.github.cocokoniq.fancyir.core.domain.recognition.Similarity
import io.github.cocokoniq.fancyir.core.domain.spatial.AimDirection
import io.github.cocokoniq.fancyir.core.domain.spatial.SpatialPrior
import io.github.cocokoniq.fancyir.core.domain.targeting.LockSource
import io.github.cocokoniq.fancyir.core.domain.targeting.TargetObservation
import io.github.cocokoniq.fancyir.core.domain.targeting.TargetingMachine
import io.github.cocokoniq.fancyir.core.domain.targeting.TargetingState
import io.github.cocokoniq.fancyir.core.haptics.HapticCue
import io.github.cocokoniq.fancyir.core.haptics.HapticEngine
import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.PulseEnvelope
import io.github.cocokoniq.fancyir.core.ir.engine.FireOutcome
import io.github.cocokoniq.fancyir.core.ir.engine.FireRequest
import io.github.cocokoniq.fancyir.core.ir.engine.IrEngine
import io.github.cocokoniq.fancyir.core.irdb.Command
import io.github.cocokoniq.fancyir.core.sensors.AimSensor
import io.github.cocokoniq.fancyir.core.vision.FrameObservation
import io.github.cocokoniq.fancyir.core.vision.ViewfinderController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the HUD needs from the app graph. */
class HudDependencies(
    val engine: IrEngine,
    val devices: DeviceRepository,
    val haptics: HapticEngine,
    val aimSensor: AimSensor,
    val viewfinder: ViewfinderController,
    /** Display density, to size gesture detents in dp. */
    val density: Float,
)

/**
 * Single writer for the HUD (ARCHITECTURE.md §4.3): every input — touch, keys, camera frames,
 * sensor ticks, engine outcomes — goes through one channel and is reduced in order on one
 * thread, then a fresh [HudUiState] is published.
 */
class HudViewModel(private val deps: HudDependencies) : ViewModel() {

    private sealed interface Input {
        data class Pointer(val input: PointerInput) : Input
        data class Frame(val frame: FrameObservation) : Input
        data class Aim(val direction: AimDirection) : Input
        data class Engine(val outcome: FireOutcome) : Input
        data class VolumeKey(val up: Boolean) : Input
        data object NavBack : Input
        data class PickEntry(val id: String) : Input
        data object ClosePicker : Input
        data class Tick(val nowMs: Long) : Input
    }

    private enum class PickerKind { DEVICES, COMMANDS }

    private val inputs = Channel<Input>(Channel.UNLIMITED)
    private val _ui = MutableStateFlow(HudUiState(demoMode = !deps.engine.capabilities.hasEmitter))
    val ui: StateFlow<HudUiState> = _ui.asStateFlow()

    private val arbiter = GestureArbiter(GestureConfig.forDensity(deps.density))
    private val targeting = TargetingMachine()
    private val gallery = Gallery()
    private val prior = SpatialPrior()

    private var aim: AimDirection? = null
    private var recent: DeviceId? = null
    private var lastFrameMs = 0L
    private var night = false
    private var navMode = false
    private var charge = 0f
    private var radial: RadialUi? = null
    private var picker: PickerUi? = null
    private var pickerKind = PickerKind.DEVICES
    private var status: String? = null
    private var statusAtMs = 0L
    private var fire: FireUi? = null
    private var fireSerial = 0
    private var painting: Pair<DeviceId, Long>? = null
    private var paintingProgress: Float? = null
    private var acquireLevel = 0

    init {
        viewModelScope.launch(Dispatchers.Default) {
            for (input in inputs) {
                if (handle(input)) _ui.value = render()
            }
        }
        viewModelScope.launch { deps.viewfinder.frames.collect { inputs.send(Input.Frame(it)) } }
        viewModelScope.launch { deps.aimSensor.directions().collect { inputs.send(Input.Aim(it)) } }
        viewModelScope.launch { deps.engine.events.collect { inputs.send(Input.Engine(it)) } }
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                inputs.send(Input.Tick(SystemClock.uptimeMillis()))
            }
        }
    }

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

    fun onPick(entryId: String) {
        inputs.trySend(Input.PickEntry(entryId))
    }

    fun onClosePicker() {
        inputs.trySend(Input.ClosePicker)
    }

    // ---- reduction (domain thread only) ----

    /** Returns true when the UI state may have changed. */
    private fun handle(input: Input): Boolean {
        when (input) {
            is Input.Pointer -> arbiter.onInput(input.input).forEach(::onGesture)
            is Input.Frame -> onFrame(input.frame)
            is Input.Aim -> {
                aim = input.direction
                return false
            }
            is Input.Engine -> onEngine(input.outcome)
            is Input.VolumeKey -> {
                val profile = lockedDevice()?.let { ControlProfiles.forCategory(it.category) } ?: return false
                deps.haptics.play(HapticCue.DETENT)
                send(ControlMapper.volumeKey(input.up, profile))
            }
            Input.NavBack -> send(ControlMapper.navBack())
            is Input.PickEntry -> pickEntry(input.id)
            Input.ClosePicker -> picker = null
            is Input.Tick -> onTick(input.nowMs)
        }
        return true
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

        val device = lockedDevice()
        if (device == null) {
            onGestureWithoutTarget(event)
            return
        }
        when (val intent = ControlMapper.map(event, activeProfile(device))) {
            is ControlIntent.Send -> send(intent)
            ControlIntent.EnterNav -> setNavMode(true)
            ControlIntent.ExitNav -> setNavMode(false)
            ControlIntent.OpenAllCommands -> openCommandPicker(device)
            null -> Unit
        }
    }

    private fun onGestureWithoutTarget(event: GestureEvent) {
        when (event) {
            GestureEvent.Tap, GestureEvent.PowerReleased, is GestureEvent.Dial, is GestureEvent.Step ->
                say("NO TARGET · HOLD ▼ FOR DEVICES")
            is GestureEvent.RadialSelected -> if (event.slot == Direction.DOWN) openDevicePicker()
            else -> Unit
        }
    }

    private fun onFrame(frame: FrameObservation) {
        lastFrameMs = frame.timestampMs
        night = frame.isDark

        painting?.let { (device, startedAt) ->
            gallery.add(device, frame.fingerprint)
            val progress = (frame.timestampMs - startedAt).toFloat() / PAINT_MS
            paintingProgress = progress.coerceAtMost(1f)
            if (progress >= 1f) {
                painting = null
                paintingProgress = null
                aim?.let { prior.record(device, it) }
                // Hand the manual lock over to recognition: it re-locks visually while aimed.
                targeting.release()
                say("LEARNED · ${gallery.size(device)} VIEWS")
                deps.haptics.play(HapticCue.ACK)
            }
        }

        val visual = gallery.similarities(frame.fingerprint).mapValues { Similarity.toScore(it.value) }
        observe(frame.timestampMs, visual, frame.salient, isVisual = !frame.isDark)
    }

    private fun onTick(nowMs: Long) {
        if (status != null && nowMs - statusAtMs > STATUS_MS) status = null
        // No camera frames (not started yet, or permission denied): run on priors alone.
        if (nowMs - lastFrameMs > PRIOR_ONLY_AFTER_MS) observe(nowMs, emptyMap(), salient = false, isVisual = false)
    }

    private fun observe(timeMs: Long, visual: Map<DeviceId, Float>, salient: Boolean, isVisual: Boolean) {
        val spatial = aim?.let(prior::scores).orEmpty()
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

    private fun onEngine(outcome: FireOutcome) {
        when (outcome) {
            is FireOutcome.Started -> {
                val envelope = PulseEnvelope.of(outcome.pattern.timingsUs, binUs = ENVELOPE_BIN_US)
                fire = FireUi(++fireSerial, outcome.request.label, envelope)
                say(outcome.request.label + if (outcome.request.presses > 1) " ×${outcome.request.presses}" else "")
                deps.haptics.playTexture(envelope.take(MAX_TEXTURE_BINS).toFloatArray(), ENVELOPE_BIN_US / 1_000L)
                val device = lockedDevice()
                val direction = aim
                if (device != null && direction != null) prior.record(device.id, direction)
            }
            is FireOutcome.Failed -> {
                say("FAILED · ${outcome.error.message ?: outcome.request.label}")
                deps.haptics.play(HapticCue.ERROR)
            }
            is FireOutcome.Dropped -> Unit
        }
    }

    private fun send(intent: ControlIntent.Send) {
        val device = lockedDevice() ?: run {
            say("NO TARGET")
            return
        }
        val (command, code) = device.codeset.firstAvailable(intent.chain) ?: run {
            say("${intent.label} · NOT IN CODESET")
            return
        }
        fire(device, command.name.replace('_', ' '), code, intent.presses, intent.urgent)
    }

    private fun fire(device: Device, label: String, code: IrCode, presses: Int = 1, urgent: Boolean = false) {
        deps.engine.fire(
            FireRequest(
                targetKey = device.id.value,
                label = label,
                code = code,
                presses = presses,
                priority = if (urgent) FireRequest.Priority.URGENT else FireRequest.Priority.NORMAL,
                issuedAtMs = SystemClock.uptimeMillis(),
            ),
        )
    }

    private fun pickEntry(id: String) {
        when (pickerKind) {
            PickerKind.DEVICES -> {
                val device = deps.devices.get(DeviceId(id)) ?: return
                targeting.lockManually(device.id)
                recent = device.id
                painting = device.id to SystemClock.uptimeMillis()
                paintingProgress = 0f
                say("HOLD STEADY · LEARNING ${device.name.uppercase()}")
                deps.haptics.play(HapticCue.LOCK)
                picker = null
            }
            PickerKind.COMMANDS -> {
                val device = lockedDevice() ?: return
                val command = Command.entries.firstOrNull { it.name == id }
                val code = command?.let { device.codeset[it] } ?: device.codeset.custom[id] ?: return
                fire(device, (command?.name ?: id).replace('_', ' '), code)
            }
        }
    }

    private fun openDevicePicker() {
        pickerKind = PickerKind.DEVICES
        picker = PickerUi(
            title = "DEVICES",
            entries = deps.devices.devices.value.map { PickerEntry(it.id.value, it.name.uppercase(), it.category.label.uppercase()) },
        )
    }

    private fun openCommandPicker(device: Device) {
        pickerKind = PickerKind.COMMANDS
        val known = device.codeset.codes.keys.map { PickerEntry(it.name, it.name.replace('_', ' ')) }
        val custom = device.codeset.custom.keys.map { PickerEntry(it, it.uppercase(), "CUSTOM") }
        picker = PickerUi(title = "${device.name.uppercase()} · ALL", entries = known + custom)
    }

    private fun say(text: String) {
        status = text
        statusAtMs = SystemClock.uptimeMillis()
    }

    private fun setNavMode(enabled: Boolean) {
        navMode = enabled
        arbiter.navMode = enabled
    }

    private fun lockedDevice(): Device? =
        (targeting.state as? TargetingState.Locked)?.let { deps.devices.get(it.device) }

    private fun activeProfile(device: Device): ControlProfile =
        if (navMode) ControlProfiles.nav else ControlProfiles.forCategory(device.category)

    private fun radialLabels(): Map<Direction, String> {
        val device = lockedDevice() ?: return mapOf(Direction.DOWN to "DEVICES")
        return activeProfile(device).radial.mapValues { (_, action) ->
            when (action) {
                is RadialAction.Send -> action.label
                RadialAction.EnterNav -> "NAV"
                RadialAction.ExitNav -> "EXIT NAV"
                RadialAction.AllCommands -> "ALL"
            }
        }
    }

    private fun render(): HudUiState {
        val state = targeting.state
        val devices = deps.devices
        val locked = state as? TargetingState.Locked
        val device = locked?.let { devices.get(it.device) }
        val profile = device?.let(::activeProfile)
        val acquiring = state as? TargetingState.Acquiring
        val direction = aim
        return HudUiState(
            phase = when (state) {
                TargetingState.Scanning -> TargetPhase.SCANNING
                is TargetingState.Acquiring -> TargetPhase.ACQUIRING
                is TargetingState.Locked -> TargetPhase.LOCKED
                TargetingState.Unrecognized -> TargetPhase.UNRECOGNIZED
            },
            lockSource = locked?.source,
            targetName = device?.name?.uppercase(),
            targetCategory = device?.category?.label?.uppercase(),
            confidence = locked?.confidence ?: acquiring?.progress ?: 0f,
            hint = acquiring?.let { devices.get(it.device)?.name?.uppercase() },
            dialLabel = profile?.dialLabel,
            stepLabel = profile?.stepLabel,
            navMode = navMode,
            charge = charge,
            radial = radial,
            status = status,
            fire = fire,
            painting = paintingProgress,
            picker = picker,
            demoMode = !deps.engine.capabilities.hasEmitter,
            night = night,
            radar = if (direction == null) emptyList() else prior.bearings(direction).mapNotNull { bearing ->
                devices.get(bearing.device)?.let {
                    RadarBlip(it.name.uppercase(), bearing.relativeAzimuthDeg, locked?.device == bearing.device)
                }
            },
        )
    }

    companion object {
        private const val TICK_MS = 100L
        private const val STATUS_MS = 2_500L
        private const val PRIOR_ONLY_AFTER_MS = 300L
        private const val PAINT_MS = 2_500f
        private const val ENVELOPE_BIN_US = 5_000
        private const val MAX_TEXTURE_BINS = 24

        fun factory(deps: HudDependencies): ViewModelProvider.Factory = viewModelFactory {
            initializer { HudViewModel(deps) }
        }
    }
}

/** A lock inferred from priors alone (dark room, camera not running yet): it controls, but is styled as a guess. */
internal val HudUiState.isPredicted: Boolean get() = lockSource == LockSource.PREDICTED
