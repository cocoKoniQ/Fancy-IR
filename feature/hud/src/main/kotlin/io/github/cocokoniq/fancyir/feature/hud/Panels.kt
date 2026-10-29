package io.github.cocokoniq.fancyir.feature.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.cocokoniq.fancyir.core.designsystem.HudShapes
import io.github.cocokoniq.fancyir.core.designsystem.HudSpace
import io.github.cocokoniq.fancyir.core.designsystem.HudTokens
import io.github.cocokoniq.fancyir.core.designsystem.HudType
import io.github.cocokoniq.fancyir.core.designsystem.LocalHudPalette
import io.github.cocokoniq.fancyir.feature.hud.HudViewModel.ReconAction

/** Status in a scrim pill, above the system gesture area. */
@Composable
internal fun StatusLine(status: String?, modifier: Modifier = Modifier) {
    val palette = LocalHudPalette.current
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = HudSpace.xxl), contentAlignment = Alignment.Center) {
        status?.let {
            Text(
                it,
                style = HudType.Status,
                color = palette.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), HudShapes.pill)
                    .padding(horizontal = HudSpace.l, vertical = HudSpace.s),
            )
        }
    }
}

/** Primary actions: 48 dp tall, full pill; the accent fill is reserved for the one primary choice. */
@Composable
internal fun HudButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, color: Color? = null) {
    val palette = LocalHudPalette.current
    val accent = color ?: palette.signal
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(HudSpace.touch)
            .then(
                if (primary) Modifier.background(accent, HudShapes.pill)
                else Modifier.border(1.dp, palette.ink.copy(alpha = 0.25f), HudShapes.pill),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = HudSpace.l),
    ) {
        Text(text, style = HudType.Status, color = if (primary) Color(0xFF041A0E) else palette.ink)
    }
}

/** Empty state as an opportunity (no devices yet): one sentence and one obvious action. */
@Composable
internal fun FirstRunCard(onAdd: () -> Unit, onPhone: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalHudPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(HudSpace.l)
            .background(HudTokens.Surface, HudShapes.card)
            .padding(HudSpace.xl),
        verticalArrangement = Arrangement.spacedBy(HudSpace.m),
    ) {
        Text("Point. Lock. Blast.", style = HudType.Title, color = palette.ink)
        Text(
            "Add your first device: pick its type and brand, aim at it, and tell Fancy-IR when it reacts to a test signal. " +
                "After that, just point the camera at it.",
            style = HudType.Body,
            color = palette.ink.copy(alpha = 0.8f),
        )
        HudButton("ADD A DEVICE", onAdd, Modifier.fillMaxWidth(), primary = true)
        HudButton("WHERE IS MY IR BLASTER?", onPhone, Modifier.fillMaxWidth())
    }
}

/**
 * First-time recon in the thumb zone (DESIGN.md §2.6). Every step can also be answered with a
 * swipe — right for "it reacted", left for "nothing" — so you never have to look away.
 */
@Composable
internal fun ReconPanel(recon: ReconUi, onAction: (ReconAction) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalHudPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(HudSpace.l)
            .background(HudTokens.Surface, HudShapes.card)
            .padding(HudSpace.xl),
        verticalArrangement = Arrangement.spacedBy(HudSpace.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(recon.title, style = HudType.Caption, color = palette.ink.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            if (recon.candidates > 0) Text("${recon.candidates} CODE SETS", style = HudType.Caption, color = palette.ink.copy(alpha = 0.7f))
        }
        when (recon.stage) {
            ReconStage.LOOKING_UP, ReconStage.SAVING -> {
                Text(recon.detail, style = HudType.Status, color = palette.ink)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.signal, trackColor = palette.ink.copy(alpha = 0.12f))
                HudButton("CANCEL", { onAction(ReconAction.CANCEL) }, Modifier.fillMaxWidth())
            }
            ReconStage.PROBING -> {
                Text(recon.detail, style = HudType.Status, color = palette.ink)
                ProbeDots(fired = recon.probe, total = MAX_PROBE_DOTS)
                Row(horizontalArrangement = Arrangement.spacedBy(HudSpace.m)) {
                    HudButton("NOTHING", { onAction(ReconAction.NO) }, Modifier.weight(1f))
                    HudButton("IT REACTED", { onAction(ReconAction.YES) }, Modifier.weight(1f), primary = true)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextAction("FIRE AGAIN") { onAction(ReconAction.REFIRE) }
                    TextAction("SKIP TEST") { onAction(ReconAction.SKIP_TEST) }
                    TextAction("CANCEL") { onAction(ReconAction.CANCEL) }
                }
                Text("or swipe →  it reacted   ·   ←  nothing", style = HudType.Caption, color = palette.ink.copy(alpha = 0.5f))
            }
            ReconStage.DONE -> {
                SuccessMark()
                Text(recon.detail, style = HudType.Status, color = palette.signal)
            }
            ReconStage.NO_MATCH -> {
                Text(recon.detail, style = HudType.Status, color = palette.amber)
                Row(horizontalArrangement = Arrangement.spacedBy(HudSpace.m)) {
                    HudButton("CANCEL", { onAction(ReconAction.CANCEL) }, Modifier.weight(1f))
                    HudButton("TRY ONLINE", { onAction(ReconAction.LOOK_ONLINE) }, Modifier.weight(1f), primary = true)
                }
            }
            ReconStage.UNAVAILABLE -> {
                Text(recon.detail, style = HudType.Status, color = palette.amber)
                HudButton("CLOSE", { onAction(ReconAction.CANCEL) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    val palette = LocalHudPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(HudSpace.touch).clickable(role = Role.Button, onClick = onClick).padding(horizontal = HudSpace.s),
    ) {
        Text(text, style = HudType.Caption, color = palette.ink.copy(alpha = 0.8f))
    }
}

@Composable
private fun ProbeDots(fired: Int, total: Int) {
    val palette = LocalHudPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(HudSpace.s)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .size(HudSpace.s)
                    .background(if (index < fired) palette.infrared else palette.ink.copy(alpha = 0.2f), CircleShape),
            )
        }
    }
}

/** The peak moment (peak-end rule): the code set worked. A springy check, then the summary. */
@Composable
private fun SuccessMark() {
    val palette = LocalHudPalette.current
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(HudSpace.xxxl)
            .scale(scale.value)
            .background(palette.signal.copy(alpha = 0.18f), CircleShape),
    ) {
        Text("✓", style = HudType.Display, color = palette.signal)
    }
}

private const val MAX_PROBE_DOTS = 8
