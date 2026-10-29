package io.github.cocokoniq.fancyir.core.designsystem

import android.graphics.RuntimeShader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * The radial scan sweep (DESIGN.md §2.2) as an AGSL shader. The time uniform is read only in
 * the draw phase, so the sweep animates without recomposition.
 */
@Composable
fun ScanSweep(color: Color, modifier: Modifier = Modifier, periodMs: Int = 1_600) {
    val shader = remember { RuntimeShader(SWEEP_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    val phase = rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "sweepPhase",
    )
    Canvas(modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("phase", phase.value)
        shader.setFloatUniform("tint", color.red, color.green, color.blue, color.alpha)
        drawRect(brush)
    }
}

private const val SWEEP_AGSL = """
uniform float2 resolution;
uniform float phase;
uniform float4 tint;

half4 main(float2 coord) {
    float2 d = coord - resolution * 0.5;
    float angle = atan(d.y, d.x);
    float sweep = phase * 6.2831853 - 3.1415927;
    float behind = mod(angle - sweep + 6.2831853, 6.2831853);
    float trail = exp(-behind * 2.5);
    float r = length(d) / max(resolution.x, resolution.y);
    float fade = 1.0 - smoothstep(0.05, 0.7, r);
    float a = tint.a * trail * fade * 0.12;
    return half4(tint.rgb * a, a);
}
"""
