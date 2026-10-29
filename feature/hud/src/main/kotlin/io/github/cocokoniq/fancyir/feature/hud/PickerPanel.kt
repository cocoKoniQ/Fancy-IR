package io.github.cocokoniq.fancyir.feature.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.cocokoniq.fancyir.core.designsystem.HudType
import io.github.cocokoniq.fancyir.core.designsystem.LocalHudPalette

/**
 * The long tail (DESIGN.md §3.5) and, until onboarding lands, the manual device picker.
 * Big rows, thumb reach, no small targets.
 */
@Composable
internal fun PickerPanel(picker: PickerUi, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalHudPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .background(Color(0xF00B0D0F), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(top = 20.dp),
        ) {
            Text(picker.title, style = HudType.Title, color = palette.signal, modifier = Modifier.padding(horizontal = 24.dp))
            LazyColumn(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(picker.entries, key = { it.id }) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { onPick(entry.id) }
                            .padding(horizontal = 24.dp),
                    ) {
                        Text(entry.label, style = HudType.Status, color = palette.ink)
                        entry.detail?.let { Text(it, style = HudType.Caption, color = palette.ink.copy(alpha = 0.5f)) }
                    }
                }
            }
        }
    }
}
