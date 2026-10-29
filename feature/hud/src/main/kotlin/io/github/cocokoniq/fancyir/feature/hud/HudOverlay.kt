package io.github.cocokoniq.fancyir.feature.hud

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.cocokoniq.fancyir.core.designsystem.HudPalette
import io.github.cocokoniq.fancyir.core.designsystem.HudType
import io.github.cocokoniq.fancyir.core.designsystem.LocalHudPalette
import io.github.cocokoniq.fancyir.core.designsystem.ScanSweep
import io.github.cocokoniq.fancyir.core.domain.gesture.Direction
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The HUD layer (DESIGN.md §2). Fast-changing values (breathing, beam, sweep) are read only
 * inside draw lambdas, so they animate at display refresh without recomposition.
 */
@Composable
internal fun HudOverlay(ui: HudUiState, modifier: Modifier = Modifier) {
    val palette = LocalHudPalette.current

    BoxWithConstraints(modifier) {
        val scanning = ui.phase == TargetPhase.SCANNING || ui.phase == TargetPhase.ACQUIRING
        if (scanning) ScanSweep(color = palette.ink, modifier = Modifier.fillMaxSize())

        Reticle(ui, palette, Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = maxWidth * 0.19f + 28.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TargetLabel(ui, palette)
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Radar(ui.radar, palette, Modifier.fillMaxWidth().height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.navMode) Capsule("NAV", palette.signal)
                if (ui.demoMode) Capsule("DEMO · NO IR EMITTER", palette.amber)
            }
        }

        ui.status?.let {
            Text(
                text = it,
                style = HudType.Status,
                color = palette.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 32.dp),
            )
        }

        ui.radial?.let { RadialDeck(it, ui.charge, palette) }
    }
}

@Composable
private fun Reticle(ui: HudUiState, palette: HudPalette, modifier: Modifier) {
    val locked = ui.phase == TargetPhase.LOCKED
    val scale by animateFloatAsState(
        targetValue = when (ui.phase) {
            TargetPhase.LOCKED -> 0.30f
            TargetPhase.UNRECOGNIZED -> 0.42f
            else -> 0.38f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "reticleScale",
    )
    val color by animateColorAsState(
        targetValue = when (ui.phase) {
            TargetPhase.LOCKED -> palette.signal
            TargetPhase.UNRECOGNIZED -> palette.amber
            TargetPhase.ACQUIRING -> palette.ink.copy(alpha = 0.9f)
            TargetPhase.SCANNING -> palette.ink.copy(alpha = 0.7f)
        },
        label = "reticleColor",
    )
    val breath = rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Reverse),
        label = "breathScale",
    )
    val beam = remember { Animatable(1f) }
    LaunchedEffect(ui.fire?.serial) {
        if (ui.fire != null) {
            beam.snapTo(0f)
            beam.animateTo(1f, tween(durationMillis = 220, easing = LinearOutSlowInEasing))
        }
    }
    val density = LocalDensity.current
    val stroke = with(density) { 2.5.dp.toPx() }
    val effect = when {
        ui.phase == TargetPhase.UNRECOGNIZED -> PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
        ui.isPredicted -> PathEffect.dashPathEffect(floatArrayOf(2f, 10f))
        else -> null
    }
    val ringGap = 0.08f + 0.12f * (ui.fire?.envelope?.average()?.toFloat() ?: 0f)

    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val side = size.width * scale * (if (locked) 1f else breath.value)
        drawBrackets(center, side, color, stroke, effect)

        if (ui.confidence > 0f) {
            val radius = side * 0.62f
            drawArc(
                color = color.copy(alpha = 0.5f),
                startAngle = -90f,
                sweepAngle = 360f * ui.confidence.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = center - Offset(radius, radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke * 0.6f),
            )
        }
        ui.painting?.let { progress ->
            val radius = side * 0.70f
            drawArc(
                color = palette.signal,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = center - Offset(radius, radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round),
            )
        }

        // The beam travels away from you, into the scene: rings converge on the target.
        val t = beam.value
        if (t < 1f) {
            val maxRadius = hypot(size.width, size.height) / 2
            for (i in 0 until 4) {
                val radius = maxRadius * (1f - t) * (1f + i * ringGap) + side * 0.2f
                val alpha = ((1f - t) * (0.9f - i * 0.18f)).coerceIn(0f, 1f)
                drawCircle(FRINGE.copy(alpha = alpha * 0.35f), radius + 3f, center + Offset(2f, 0f), style = Stroke(stroke))
                drawCircle(palette.infrared.copy(alpha = alpha), radius, center, style = Stroke(stroke * 1.4f))
            }
        }
    }
}

private val FRINGE = Color(0xFF3DA0FF)

private fun DrawScope.drawBrackets(center: Offset, side: Float, color: Color, stroke: Float, effect: PathEffect?) {
    val half = side / 2
    val arm = side * 0.18f
    for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
        val corner = center + Offset(sx * half, sy * half)
        drawLine(color, corner, corner - Offset(sx * arm, 0f), stroke, StrokeCap.Round, effect)
        drawLine(color, corner, corner - Offset(0f, sy * arm), stroke, StrokeCap.Round, effect)
    }
    drawLine(color.copy(alpha = color.alpha * 0.5f), center - Offset(8f, 0f), center + Offset(8f, 0f), 1.5f)
    drawLine(color.copy(alpha = color.alpha * 0.5f), center - Offset(0f, 8f), center + Offset(0f, 8f), 1.5f)
}

@Composable
private fun TargetLabel(ui: HudUiState, palette: HudPalette) {
    when (ui.phase) {
        TargetPhase.LOCKED -> {
            Text(ui.targetName ?: "", style = HudType.Title, color = palette.signal, textAlign = TextAlign.Center)
            val detail = listOfNotNull(
                ui.targetCategory,
                if (ui.isPredicted) "PREDICTED" else null,
                ui.dialLabel?.let { "↕ $it" },
                ui.stepLabel?.let { "↔ $it" },
            ).joinToString("  ·  ")
            Text(detail, style = HudType.Caption, color = palette.ink.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            if (ui.painting != null) Text("LEARNING · HOLD STEADY", style = HudType.Caption, color = palette.signal)
        }
        TargetPhase.ACQUIRING -> ui.hint?.let { Text("$it ?", style = HudType.Status, color = palette.ink.copy(alpha = 0.6f)) }
        TargetPhase.UNRECOGNIZED ->
            Text("UNKNOWN DEVICE · HOLD ▼ TO PICK", style = HudType.Status, color = palette.amber, textAlign = TextAlign.Center)
        TargetPhase.SCANNING -> Text("SCANNING", style = HudType.Caption, color = palette.ink.copy(alpha = 0.5f))
    }
}

@Composable
private fun Radar(blips: List<RadarBlip>, palette: HudPalette, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val y = size.height * 0.35f
        drawLine(palette.ink.copy(alpha = 0.25f), Offset(size.width * 0.08f, y), Offset(size.width * 0.92f, y), 1f)
        drawLine(palette.ink.copy(alpha = 0.6f), Offset(size.width / 2, y - 6f), Offset(size.width / 2, y + 6f), 1.5f)
        for (blip in blips) {
            val clamped = blip.relativeAzimuthDeg.coerceIn(-90f, 90f)
            val x = size.width / 2 + clamped / 90f * size.width * 0.42f
            val color = if (blip.locked) palette.signal else palette.ink.copy(alpha = if (abs(blip.relativeAzimuthDeg) > 90f) 0.4f else 0.8f)
            drawCircle(color, radius = 5f, center = Offset(x, y))
            val layout = measurer.measure(blip.label, HudType.Caption.copy(color = color))
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y + 8f))
        }
    }
}

@Composable
private fun Capsule(text: String, color: Color) {
    Text(
        text = text,
        style = HudType.Caption,
        color = color,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Marking-menu deck around the thumb, with the POWER charge ring in its centre. */
@Composable
private fun RadialDeck(radial: RadialUi, charge: Float, palette: HudPalette) {
    val density = LocalDensity.current
    val reach = with(density) { 104.dp.toPx() }
    val slotWidth = with(density) { 120.dp.toPx() }
    val slotHeight = with(density) { 32.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(radial.x, radial.y)
            val ring = 34.dp.toPx()
            drawCircle(palette.ink.copy(alpha = 0.25f), ring, center, style = Stroke(2.dp.toPx()))
            if (charge > 0f) {
                drawArc(
                    color = palette.infrared,
                    startAngle = -90f,
                    sweepAngle = 360f * charge,
                    useCenter = false,
                    topLeft = center - Offset(ring, ring),
                    size = Size(ring * 2, ring * 2),
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        for ((direction, label) in radial.labels) {
            val (dx, dy) = when (direction) {
                Direction.UP -> 0f to -reach
                Direction.DOWN -> 0f to reach
                Direction.LEFT -> -reach to 0f
                Direction.RIGHT -> reach to 0f
            }
            val hovered = radial.hover == direction
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset((radial.x + dx - slotWidth / 2).roundToInt(), (radial.y + dy - slotHeight / 2).roundToInt()) }
                    .size(120.dp, 32.dp)
                    .background(if (hovered) palette.signal.copy(alpha = 0.25f) else palette.scrim, RoundedCornerShape(50)),
            ) {
                Text(label, style = HudType.Caption, color = if (hovered) palette.signal else palette.ink)
            }
        }
        Text(
            text = if (charge >= 1f) "RELEASE · POWER" else "HOLD · POWER",
            style = HudType.Caption,
            color = if (charge >= 1f) palette.infrared else palette.ink.copy(alpha = 0.7f),
            modifier = Modifier.offset {
                IntOffset((radial.x - slotWidth / 2).roundToInt(), (radial.y + with(density) { 44.dp.toPx() }).roundToInt())
            }.size(120.dp, 20.dp),
            textAlign = TextAlign.Center,
        )
    }
}
