package io.github.cocokoniq.fancyir.core.domain.targeting

import io.github.cocokoniq.fancyir.core.domain.device.DeviceId
import kotlin.math.exp
import kotlin.math.max

/** One analysis tick: fused per-device scores in [0, 1] (see `ScoreFusion`). */
data class TargetObservation(
    val timeMs: Long,
    val scores: Map<DeviceId, Float>,
    /** Something object-like is under the reticle (edges/contrast), not a blank wall. */
    val salient: Boolean = false,
    /** False when the scores come from priors only (dark room, camera not yet running). */
    val visual: Boolean = true,
)

enum class LockSource { VISUAL, PREDICTED, MANUAL }

sealed interface TargetingState {
    data object Scanning : TargetingState
    data class Acquiring(val device: DeviceId, val progress: Float) : TargetingState
    data class Locked(val device: DeviceId, val confidence: Float, val source: LockSource) : TargetingState
    data object Unrecognized : TargetingState
}

data class TargetingConfig(
    val tauMs: Float = 150f,
    val nominalFrameMs: Long = 66,
    val lockThreshold: Float = 0.75f,
    val lockMargin: Float = 0.15f,
    val lockDwellMs: Long = 100,
    val releaseThreshold: Float = 0.45f,
    val releaseDwellMs: Long = 700,
    val hintFloor: Float = 0.30f,
    val unknownDwellMs: Long = 600,
)

/**
 * The TARGETING region of the HUD statechart (DESIGN.md §2): exponential evidence per device,
 * hysteresis on lock/release/switch, and a frozen lock while a finger is down.
 */
class TargetingMachine(private val config: TargetingConfig = TargetingConfig()) {

    private val evidence = HashMap<DeviceId, Float>()
    private var lastTimeMs: Long? = null
    private var candidate: DeviceId? = null
    private var candidateSince = 0L
    private var belowSince: Long? = null
    private var unknownSince: Long? = null

    var state: TargetingState = TargetingState.Scanning
        private set

    /** Set while a finger is on the screen: the lock never changes mid-gesture. */
    var interactionActive: Boolean = false

    val evidenceSnapshot: Map<DeviceId, Float> get() = evidence.toMap()

    fun onObservation(observation: TargetObservation): TargetingState {
        integrate(observation)
        state = next(observation)
        return state
    }

    /** Lock chosen by the user (deck or debug picker); survives low evidence, yields to a clear visual winner. */
    fun lockManually(device: DeviceId) {
        resetTrackers()
        state = TargetingState.Locked(device, confidence = 1f, source = LockSource.MANUAL)
    }

    fun release() {
        resetTrackers()
        state = TargetingState.Scanning
    }

    private fun integrate(observation: TargetObservation) {
        val dt = lastTimeMs?.let { (observation.timeMs - it).coerceAtLeast(0) } ?: config.nominalFrameMs
        lastTimeMs = observation.timeMs
        val alpha = exp(-dt / config.tauMs)
        for (id in evidence.keys + observation.scores.keys) {
            val e = alpha * (evidence[id] ?: 0f) + (1 - alpha) * (observation.scores[id] ?: 0f)
            if (e < 0.01f) evidence.remove(id) else evidence[id] = e
        }
    }

    private fun next(observation: TargetObservation): TargetingState {
        val ranked = evidence.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull()?.key
        val bestE = ranked.firstOrNull()?.value ?: 0f
        val secondE = ranked.getOrNull(1)?.value ?: 0f
        val t = observation.timeMs
        val source = if (observation.visual) LockSource.VISUAL else LockSource.PREDICTED

        val current = state
        if (current is TargetingState.Locked) return whileLocked(current, observation, best, bestE, source)

        if (best != null && bestE >= config.lockThreshold && bestE - secondE >= config.lockMargin) {
            if (candidate != best) {
                candidate = best
                candidateSince = t
            }
            if (t - candidateSince >= config.lockDwellMs) {
                resetTrackers()
                return TargetingState.Locked(best, bestE, source)
            }
            return TargetingState.Acquiring(best, 1f)
        }
        candidate = null

        if (best != null && bestE >= config.hintFloor) {
            unknownSince = null
            val progress = (bestE - config.hintFloor) / (config.lockThreshold - config.hintFloor)
            return TargetingState.Acquiring(best, progress.coerceIn(0f, 1f))
        }
        if (!observation.salient) {
            unknownSince = null
            return TargetingState.Scanning
        }
        val since = unknownSince ?: t.also { unknownSince = it }
        return if (t - since >= config.unknownDwellMs) TargetingState.Unrecognized else TargetingState.Scanning
    }

    private fun whileLocked(
        current: TargetingState.Locked,
        observation: TargetObservation,
        best: DeviceId?,
        bestE: Float,
        source: LockSource,
    ): TargetingState {
        val t = observation.timeMs
        val currentE = evidence[current.device] ?: 0f
        val manual = current.source == LockSource.MANUAL
        if (interactionActive) return if (manual) current else current.copy(confidence = currentE)

        val challenger = best?.takeIf {
            it != current.device && bestE >= config.lockThreshold && bestE >= currentE + config.lockMargin
        }
        if (challenger != null) {
            if (candidate != challenger) {
                candidate = challenger
                candidateSince = t
            }
            if (t - candidateSince >= config.lockDwellMs) {
                resetTrackers()
                return TargetingState.Locked(challenger, bestE, source)
            }
        } else {
            candidate = null
        }

        if (manual) return current
        if (currentE < config.releaseThreshold) {
            val since = belowSince ?: t.also { belowSince = it }
            if (t - since >= config.releaseDwellMs) {
                resetTrackers()
                return TargetingState.Scanning
            }
        } else {
            belowSince = null
        }
        return current.copy(confidence = max(currentE, 0f), source = source)
    }

    private fun resetTrackers() {
        candidate = null
        belowSince = null
        unknownSince = null
    }
}
