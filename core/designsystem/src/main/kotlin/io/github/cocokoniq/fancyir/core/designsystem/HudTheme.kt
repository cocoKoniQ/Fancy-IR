package io.github.cocokoniq.fancyir.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Colour tokens (DESIGN.md §4.1), applied 60/30/10: dark neutral surfaces and the camera image
 * are the 60, ink is the 30, and the single accent ([Signal]) is the 10. [Infrared] is reserved
 * for emission and POWER, [Amber] for unknown or assumed states.
 */
object HudTokens {
    val Ink = Color(0xFFF2F5F7)
    val Signal = Color(0xFF5CF2A6)
    val Amber = Color(0xFFFFB547)
    val Infrared = Color(0xFFFF3D6E)
    val Fault = Color(0xFFFF5A5A)
    val Night = Color(0xFFB3122E)
    val Scrim = Color(0x59000000)
    val Surface = Color(0xF20E1113)
    val SurfaceRaised = Color(0xFF161B1E)
    val Outline = Color(0x1FF2F5F7)
}

/** 8-point spacing (4 for fine adjustments): related things sit closer than unrelated ones. */
object HudSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Minimum touch target. */
    val touch = 48.dp
}

object HudShapes {
    val pill = RoundedCornerShape(50)
    val card = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val tile = RoundedCornerShape(16.dp)
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

/**
 * Four sizes, two weights, two families (DESIGN.md §4.2): monospace caps for instrument data
 * and readouts, the system sans for names and sentences in sheets.
 */
object HudType {
    private val mono = FontFamily.Monospace
    private val sans = FontFamily.SansSerif

    /** 11 sp: captions, chips, radar labels. */
    val Caption = TextStyle(fontFamily = mono, fontSize = 11.sp, letterSpacing = 0.06.em)

    /** 13 sp: status lines, HUD data. */
    val Status = TextStyle(fontFamily = mono, fontSize = 13.sp, letterSpacing = 0.06.em)

    /** 13 sp sans: sentences in sheets and cards. */
    val Body = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 18.sp)

    /** 16 sp medium: device names, sheet titles. */
    val Title = TextStyle(fontFamily = sans, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.01.em)

    /** 28 sp mono: the one big number (set-point, probe count). */
    val Display = TextStyle(fontFamily = mono, fontSize = 28.sp, fontWeight = FontWeight.Medium)
}

val LocalHudPalette = staticCompositionLocalOf { HudPalette.Day }

@Composable
fun FancyIrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HudTokens.Signal,
            onPrimary = Color(0xFF00210F),
            secondary = HudTokens.Amber,
            tertiary = HudTokens.Infrared,
            background = Color.Black,
            surface = HudTokens.SurfaceRaised,
            onBackground = HudTokens.Ink,
            onSurface = HudTokens.Ink,
            outline = HudTokens.Outline,
            error = HudTokens.Fault,
        ),
        content = content,
    )
}
