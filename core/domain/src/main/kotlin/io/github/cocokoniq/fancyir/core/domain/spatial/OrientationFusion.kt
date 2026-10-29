package io.github.cocokoniq.fancyir.core.domain.spatial

import kotlin.math.exp
import kotlin.math.sqrt

/** How the phone is moving, from the gyroscope (deg/s), with hysteresis between classes. */
enum class MotionState { STILL, STEADY, PANNING, FLICK }

/** How the phone is held, from the camera axis elevation. */
enum class Posture {
    /** Raised and pointing roughly at the room: the viewfinder is in use. */
    AIMING,

    /** Tilted down onto a lap or table edge: lap mode (DESIGN.md §2.7). */
    LOWERED,

    /** Lying screen-up: the camera sees the table. */
    FACE_UP,

    /** Lying screen-down: the camera sees the ceiling. */
    FACE_DOWN,
}

/** Everything the HUD needs to know about the phone's pose at one instant. */
data class AimState(
    val direction: AimDirection?,
    /** 0..1: how much to trust [direction]'s azimuth (magnetometer health and time since correction). */
    val headingConfidence: Float,
    val motion: MotionState,
    val posture: Posture,
    val angularSpeedDps: Float,
)

data class FusionConfig(
    /** Share of the yaw-offset error corrected per trusted magnetometer sample. */
    val headingGain: Float = 0.02f,
    /** Earth's field is 25–65 µT; outside this band something magnetic is close (speaker, soundbar). */
    val minFieldUt: Float = 22f,
    val maxFieldUt: Float = 70f,
    /** A field that jumps around by more than this (µT std-dev) is being disturbed. */
    val maxFieldJitterUt: Float = 4f,
    /** Gyro-only heading drifts a few degrees per minute; confidence halves on this timescale. */
    val confidenceHalfLifeS: Float = 90f,
    val stillDps: Float = 3f,
    val steadyDps: Float = 20f,
    val panningDps: Float = 150f,
    val stillHoldMs: Long = 400,
)

/**
 * Gyro-stabilised aim (ARCHITECTURE.md §2.2). The game rotation vector (gyro + accelerometer)
 * is smooth and immune to magnetic interference but its yaw drifts; the magnetometer-referenced
 * rotation vector is absolute but noisy and badly bent near speaker magnets — exactly where you
 * aim. So heading = game yaw + a slowly corrected offset, and the offset only learns from the
 * magnetometer while the field looks like the Earth's.
 */
class OrientationFusion(private val config: FusionConfig = FusionConfig()) {

    private var gameAim: AimDirection? = null
    private var absoluteAim: AimDirection? = null
    private var yawOffset: Float? = null
    private var confidence = 0f
    private var lastConfidenceUpdateNs: Long? = null

    private var magAccuracy = 0
    private val fieldWindow = ArrayDeque<Float>()

    private var angularSpeedDps = 0f
    private var lastGyroNs: Long? = null
    private var motion = MotionState.STEADY
    private var stillSinceNs: Long? = null
    private var posture = Posture.AIMING

    /** 3×3 row-major matrix from `TYPE_GAME_ROTATION_VECTOR`. */
    fun onGameRotation(matrix: FloatArray, timestampNs: Long) {
        val aim = AimMath.fromRotationMatrix(matrix)
        gameAim = aim
        posture = nextPosture(aim.elevationDeg)
        decayConfidence(timestampNs)
    }

    /** 3×3 row-major matrix from `TYPE_ROTATION_VECTOR` (magnetometer-referenced). */
    fun onAbsoluteRotation(matrix: FloatArray, timestampNs: Long) {
        val aim = AimMath.fromRotationMatrix(matrix)
        absoluteAim = aim
        val game = gameAim
        if (game == null) {
            // No game rotation sensor: the absolute vector is all we have.
            posture = nextPosture(aim.elevationDeg)
            if (magnetometerTrusted()) bumpConfidence(timestampNs, 0.05f) else decayConfidence(timestampNs)
            return
        }
        val target = wrap180(aim.azimuthDeg - game.azimuthDeg)
        val current = yawOffset
        when {
            !magnetometerTrusted() || motion == MotionState.FLICK -> decayConfidence(timestampNs)
            current == null -> {
                yawOffset = target
                bumpConfidence(timestampNs, 0.3f)
            }
            else -> {
                yawOffset = wrap180(current + config.headingGain * wrap180(target - current))
                bumpConfidence(timestampNs, 0.02f)
            }
        }
    }

    fun onMagneticField(xUt: Float, yUt: Float, zUt: Float) {
        fieldWindow.addLast(sqrt(xUt * xUt + yUt * yUt + zUt * zUt))
        while (fieldWindow.size > FIELD_WINDOW) fieldWindow.removeFirst()
    }

    /** `SensorManager.SENSOR_STATUS_*`: 0 unreliable … 3 high. */
    fun onMagnetometerAccuracy(accuracy: Int) {
        magAccuracy = accuracy
    }

    /** `TYPE_GYROSCOPE` sample in rad/s. */
    fun onGyroscope(xRad: Float, yRad: Float, zRad: Float, timestampNs: Long) {
        val speedDps = sqrt(xRad * xRad + yRad * yRad + zRad * zRad) * DEG_PER_RAD
        val dtS = lastGyroNs?.let { ((timestampNs - it) / 1e9f).coerceIn(0f, 0.2f) } ?: 0.02f
        lastGyroNs = timestampNs
        val alpha = 1f - exp(-dtS / GYRO_TAU_S)
        angularSpeedDps += alpha * (speedDps - angularSpeedDps)
        motion = nextMotion(angularSpeedDps, timestampNs)
    }

    val state: AimState
        get() {
            val game = gameAim
            val offset = yawOffset
            val direction = when {
                game != null && offset != null -> game.copy(azimuthDeg = wrap360(game.azimuthDeg + offset))
                game == null -> absoluteAim
                else -> absoluteAim?.let { game.copy(azimuthDeg = it.azimuthDeg) } ?: game
            }
            return AimState(direction, confidence, motion, posture, angularSpeedDps)
        }

    fun magnetometerTrusted(): Boolean {
        if (magAccuracy < 2 || fieldWindow.size < 3) return false
        val mean = fieldWindow.average().toFloat()
        if (mean < config.minFieldUt || mean > config.maxFieldUt) return false
        val jitter = sqrt(fieldWindow.map { (it - mean) * (it - mean) }.average().toFloat())
        return jitter <= config.maxFieldJitterUt
    }

    private fun bumpConfidence(timestampNs: Long, amount: Float) {
        decayConfidence(timestampNs)
        confidence = (confidence + amount * (1f - confidence)).coerceIn(0f, 1f)
    }

    private fun decayConfidence(timestampNs: Long) {
        val last = lastConfidenceUpdateNs
        lastConfidenceUpdateNs = timestampNs
        if (last == null) return
        val dtS = ((timestampNs - last) / 1e9f).coerceAtLeast(0f)
        confidence *= exp(-dtS * LN2 / config.confidenceHalfLifeS)
    }

    private fun nextMotion(speed: Float, timestampNs: Long): MotionState {
        val margin = 0.85f // hysteresis: leave a class only after clearly crossing its bound
        val coarse = when {
            speed >= config.panningDps || (motion == MotionState.FLICK && speed >= config.panningDps * margin) -> MotionState.FLICK
            speed >= config.steadyDps || (motion == MotionState.PANNING && speed >= config.steadyDps * margin) -> MotionState.PANNING
            else -> MotionState.STEADY
        }
        if (coarse != MotionState.STEADY || speed > config.stillDps) {
            stillSinceNs = null
            return coarse
        }
        val since = stillSinceNs ?: timestampNs.also { stillSinceNs = it }
        return if ((timestampNs - since) / 1_000_000 >= config.stillHoldMs) MotionState.STILL else MotionState.STEADY
    }

    private fun nextPosture(elevationDeg: Float): Posture {
        val h = if (posture == Posture.AIMING) POSTURE_HYSTERESIS_DEG else -POSTURE_HYSTERESIS_DEG
        return when {
            elevationDeg >= 75f + h -> Posture.FACE_DOWN
            elevationDeg <= -80f - h -> Posture.FACE_UP
            elevationDeg <= -45f - h -> Posture.LOWERED
            else -> Posture.AIMING
        }
    }

    private companion object {
        const val DEG_PER_RAD = 57.29578f
        const val LN2 = 0.6931472f
        const val GYRO_TAU_S = 0.08f
        const val FIELD_WINDOW = 25
        const val POSTURE_HYSTERESIS_DEG = 5f

        fun wrap180(deg: Float): Float {
            var d = deg % 360f
            if (d > 180f) d -= 360f
            if (d <= -180f) d += 360f
            return d
        }

        fun wrap360(deg: Float): Float {
            val d = deg % 360f
            return if (d < 0f) d + 360f else d
        }
    }
}

/** Fast enough to smear the camera image: analysis frames are skipped. */
fun MotionState.blursFrames(): Boolean = this == MotionState.FLICK

/** Exemplars for the gallery are only captured while the phone is held steady. */
fun MotionState.isSteady(): Boolean = this == MotionState.STILL || this == MotionState.STEADY

/** Absolute heading error budget: anchors from low-confidence headings get wider kernels. */
fun headingSigmaDeg(baseSigmaDeg: Float, confidence: Float): Float = baseSigmaDeg / confidence.coerceIn(0.3f, 1f)
