package io.github.cocokoniq.fancyir.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Colour tokens from DESIGN.md §4.1. */
object HudTokens {
    val Ink = Color(0xFFF2F5F7)
    val Signal = Color(0xFF5CF2A6)
    val Amber = Color(0xFFFFB547)
    val Infrared = Color(0xFFFF3D6E)
    val Fault = Color(0xFFFF5A5A)
    val Night = Color(0xFFB3122E)
    val Scrim = Color(0x59000000)
}

/** The HUD's live palette: in the night palette every ink collapses to a dim red. */
@Immutable
data class HudPalette(
    val ink: Color,
    val signal: Color,
    val amber: Color,
    val infrared: Color,
    val fault: Color,
    val scrim: Color,
) {
    companion object {
        val Day = HudPalette(HudTokens.Ink, HudTokens.Signal, HudTokens.Amber, HudTokens.Infrared, HudTokens.Fault, HudTokens.Scrim)
        val Night = HudPalette(
            ink = HudTokens.Night.copy(alpha = 0.85f),
            signal = HudTokens.Night,
            amber = HudTokens.Night.copy(alpha = 0.7f),
            infrared = HudTokens.Night,
            fault = HudTokens.Night,
            scrim = HudTokens.Scrim,
        )
    }
}

/** Monospace caps with +6% tracking (DESIGN.md §4.2). */
object HudType {
    private val mono = FontFamily.Monospace
    val Caption = TextStyle(fontFamily = mono, fontSize = 11.sp, letterSpacing = 0.06.em)
    val Status = TextStyle(fontFamily = mono, fontSize = 13.sp, letterSpacing = 0.06.em)
    val Title = TextStyle(fontFamily = mono, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.06.em)
}

val LocalHudPalette = staticCompositionLocalOf { HudPalette.Day }

@Composable
fun FancyIrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HudTokens.Signal,
            secondary = HudTokens.Amber,
            tertiary = HudTokens.Infrared,
            background = Color.Black,
            surface = Color(0xFF0B0D0F),
            onBackground = HudTokens.Ink,
            onSurface = HudTokens.Ink,
            error = HudTokens.Fault,
        ),
        content = content,
    )
}
