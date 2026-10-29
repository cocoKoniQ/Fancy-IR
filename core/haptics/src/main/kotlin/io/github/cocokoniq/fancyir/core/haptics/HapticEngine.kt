package io.github.cocokoniq.fancyir.core.haptics

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager

/** The haptic vocabulary of DESIGN.md §4.4. */
enum class HapticCue {
    CANDIDATE_LOW, CANDIDATE_HIGH, LOCK, SWITCH, LOCK_LOST, DETENT, CHARGE_TICK, CHARGED, FIZZLE, ACK, ERROR
}

/**
 * Plays [HapticCue]s with composition primitives where the actuator supports them, falling
 * back to predefined effects, and renders an IR pulse envelope as a waveform ("IR texture").
 */
class HapticEngine(context: Context) {

    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    private val primitives = intArrayOf(
        Composition.PRIMITIVE_CLICK,
        Composition.PRIMITIVE_TICK,
        Composition.PRIMITIVE_LOW_TICK,
        Composition.PRIMITIVE_THUD,
        Composition.PRIMITIVE_QUICK_FALL,
    )
    private val supportsPrimitives = vibrator.hasVibrator() && vibrator.areAllPrimitivesSupported(*primitives)

    fun play(cue: HapticCue) {
        if (!vibrator.hasVibrator()) return
        val effect = if (supportsPrimitives) composed(cue) else predefined(cue)
        vibrator.vibrate(effect, attributes)
    }

    /**
     * [envelope] is the marked fraction per bin of an IR pattern (`PulseEnvelope`), [binMs] the
     * bin width. Falls back to a click on actuators without amplitude control.
     */
    fun playTexture(envelope: FloatArray, binMs: Long) {
        if (!vibrator.hasVibrator() || envelope.isEmpty()) return
        if (!vibrator.hasAmplitudeControl()) {
            play(HapticCue.LOCK)
            return
        }
        val (timings, amplitudes) = HapticWaveforms.fromEnvelope(envelope, binMs)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), attributes)
    }

    private fun composed(cue: HapticCue): VibrationEffect {
        val c = VibrationEffect.startComposition()
        when (cue) {
            HapticCue.CANDIDATE_LOW -> c.addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.3f)
            HapticCue.CANDIDATE_HIGH -> c.addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.5f)
            HapticCue.LOCK -> c.addPrimitive(Composition.PRIMITIVE_CLICK, 1f)
                .addPrimitive(Composition.PRIMITIVE_TICK, 0.4f, 40)
            HapticCue.SWITCH -> c.addPrimitive(Composition.PRIMITIVE_TICK, 0.6f)
            HapticCue.LOCK_LOST -> c.addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.4f)
            HapticCue.DETENT -> c.addPrimitive(Composition.PRIMITIVE_TICK, 0.5f)
            HapticCue.CHARGE_TICK -> c.addPrimitive(Composition.PRIMITIVE_TICK, 0.35f)
            HapticCue.CHARGED -> c.addPrimitive(Composition.PRIMITIVE_THUD, 1f)
            HapticCue.FIZZLE -> c.addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.5f)
                .addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.5f, 60)
            HapticCue.ACK -> c.addPrimitive(Composition.PRIMITIVE_TICK, 0.7f)
                .addPrimitive(Composition.PRIMITIVE_TICK, 0.7f, 50)
            HapticCue.ERROR -> c.addPrimitive(Composition.PRIMITIVE_QUICK_FALL, 1f)
        }
        return c.compose()
    }

    private fun predefined(cue: HapticCue): VibrationEffect = VibrationEffect.createPredefined(
        when (cue) {
            HapticCue.LOCK, HapticCue.CHARGED -> VibrationEffect.EFFECT_HEAVY_CLICK
            HapticCue.FIZZLE, HapticCue.ACK -> VibrationEffect.EFFECT_DOUBLE_CLICK
            HapticCue.ERROR -> VibrationEffect.EFFECT_HEAVY_CLICK
            else -> VibrationEffect.EFFECT_TICK
        },
    )
}

internal object HapticWaveforms {

    private const val MIN_SEGMENT_MS = 10L
    private const val MAX_SEGMENTS = 16

    /**
     * Resamples the envelope into at most [MAX_SEGMENTS] segments of at least [MIN_SEGMENT_MS]
     * (shorter segments are lost on most actuators) and maps marked fraction to amplitude.
     */
    fun fromEnvelope(envelope: FloatArray, binMs: Long): Pair<LongArray, IntArray> {
        val totalMs = (envelope.size * binMs).coerceAtLeast(MIN_SEGMENT_MS)
        val segments = (totalMs / MIN_SEGMENT_MS).toInt().coerceIn(1, MAX_SEGMENTS)
        val segmentMs = totalMs / segments
        val timings = LongArray(segments) { segmentMs }
        val amplitudes = IntArray(segments) { s ->
            val from = s * envelope.size / segments
            val until = maxOf(from + 1, (s + 1) * envelope.size / segments)
            var sum = 0f
            for (i in from until until) sum += envelope[i]
            val level = sum / (until - from)
            if (level < 0.05f) 0 else (70 + 185 * level).toInt().coerceAtMost(255)
        }
        return timings to amplitudes
    }
}
