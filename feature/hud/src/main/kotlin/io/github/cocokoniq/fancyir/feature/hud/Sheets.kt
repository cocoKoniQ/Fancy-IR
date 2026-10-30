package io.github.cocokoniq.fancyir.feature.hud

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.cocokoniq.fancyir.core.designsystem.HudShapes
import io.github.cocokoniq.fancyir.core.designsystem.HudSpace
import io.github.cocokoniq.fancyir.core.designsystem.HudTokens
import io.github.cocokoniq.fancyir.core.designsystem.HudType
import io.github.cocokoniq.fancyir.core.designsystem.LocalHudPalette
import io.github.cocokoniq.fancyir.core.domain.hardware.IslandShape
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.feature.hud.HudViewModel.SheetAction
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Bottom sheet host: scrim to dismiss, sheet in the thumb zone, content by type. */
@Composable
internal fun HudSheet(sheet: SheetUi, onAction: (SheetAction) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAction(SheetAction.Close) },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .background(HudTokens.Surface, HudShapes.sheet)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .imePadding()
                .padding(top = HudSpace.m),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(HudSpace.xxl)
                    .height(HudSpace.xs)
                    .background(HudTokens.Outline, HudShapes.pill),
            )
            when (sheet) {
                is SheetUi.Devices -> DevicesSheet(sheet, onAction)
                is SheetUi.Brands -> BrandsSheet(sheet, onAction)
                is SheetUi.Commands -> CommandsSheet(sheet, onAction)
                is SheetUi.Phone -> PhoneSheet(sheet, onAction)
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String? = null) {
    val palette = LocalHudPalette.current
    Column(Modifier.padding(horizontal = HudSpace.xl, vertical = HudSpace.l), verticalArrangement = Arrangement.spacedBy(HudSpace.xs)) {
        Text(title, style = HudType.Title, color = palette.ink)
        subtitle?.let { Text(it, style = HudType.Body, color = palette.ink.copy(alpha = 0.6f)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val palette = LocalHudPalette.current
    Text(text, style = HudType.Caption, color = palette.ink.copy(alpha = 0.6f), modifier = Modifier.padding(top = HudSpace.l, bottom = HudSpace.s))
}

@Composable
private fun EntryRow(entry: PickerEntry, onClick: () -> Unit) {
    val palette = LocalHudPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = HudSpace.xl),
    ) {
        Text(entry.label, style = HudType.Body, color = palette.ink, modifier = Modifier.weight(1f))
        entry.detail?.let { Text(it.uppercase(), style = HudType.Caption, color = palette.ink.copy(alpha = 0.5f)) }
    }
}

/** Your devices first (returning users), then color-coded categories to add one (new users). */
@Composable
private fun DevicesSheet(sheet: SheetUi.Devices, onAction: (SheetAction) -> Unit) {
    val palette = LocalHudPalette.current
    SheetHeader("Devices", if (sheet.pinned.isEmpty()) "Pick what you want to control" else null)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(HudSpace.m),
        verticalArrangement = Arrangement.spacedBy(HudSpace.m),
        modifier = Modifier.padding(horizontal = HudSpace.l),
    ) {
        if (sheet.pinned.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("YOUR DEVICES · TAP TO RE-AIM AND RE-LEARN") }
            items(sheet.pinned, span = { GridItemSpan(maxLineSpan) }, key = { it.id }) { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(HudTokens.SurfaceRaised, HudShapes.tile)
                        .clickable(role = Role.Button) { onAction(SheetAction.PickDevice(entry.id)) }
                        .padding(horizontal = HudSpace.l),
                ) {
                    Box(Modifier.size(HudSpace.s).background(palette.signal, HudShapes.pill))
                    Text(entry.label, style = HudType.Body, color = palette.ink, modifier = Modifier.weight(1f).padding(start = HudSpace.m))
                    entry.detail?.let { Text(it.uppercase(), style = HudType.Caption, color = palette.ink.copy(alpha = 0.5f)) }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("ADD A DEVICE") }
        items(sheet.categories, key = { it.category.name }) { tile ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HudSpace.xs),
                modifier = Modifier
                    .height(96.dp)
                    .background(categoryTint(tile.category), HudShapes.tile)
                    .clickable(role = Role.Button) { onAction(SheetAction.PickCategory(tile.category)) }
                    .padding(HudSpace.s),
            ) {
                Text(tile.glyph, style = HudType.Display, color = palette.ink)
                Text(tile.label.uppercase(), style = HudType.Caption, color = palette.ink, maxLines = 1)
                if (tile.codesets > 0) Text("${tile.codesets} SETS", style = HudType.Caption, color = palette.ink.copy(alpha = 0.5f))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.height(HudSpace.xl)) }
    }
}

/** Soft, consistent category colours (5–12 % tints) so tiles scan as a rhythm, not a rainbow. */
private fun categoryTint(category: DeviceCategory): Color = when (category) {
    DeviceCategory.TV, DeviceCategory.MONITOR -> Color(0x1F5CF2A6)
    DeviceCategory.AIR_CONDITIONER, DeviceCategory.HEATER -> Color(0x1F5CC8F2)
    DeviceCategory.FAN, DeviceCategory.AIR_PURIFIER -> Color(0x1F5CF2E0)
    DeviceCategory.SOUNDBAR, DeviceCategory.AV_RECEIVER, DeviceCategory.SPEAKER -> Color(0x1F9D8CF2)
    DeviceCategory.PROJECTOR -> Color(0x1FFFB547)
    DeviceCategory.LIGHT -> Color(0x1FFFE08A)
    DeviceCategory.STREAMING_DEVICE, DeviceCategory.SET_TOP_BOX, DeviceCategory.MEDIA_PLAYER -> Color(0x1FFF7AB8)
    DeviceCategory.HUMIDIFIER, DeviceCategory.OTHER -> Color(0x14F2F5F7)
}

/** Smarter search: the catalog's brands, filtered as you type; an unknown brand goes to GitHub. */
@Composable
private fun BrandsSheet(sheet: SheetUi.Brands, onAction: (SheetAction) -> Unit) {
    val palette = LocalHudPalette.current
    var query by remember(sheet.category) { mutableStateOf("") }
    SheetHeader("${sheet.title} brand", "Popular first. Not listed? Type it — Fancy-IR checks open IR databases on GitHub.")
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        placeholder = { Text("Brand, e.g. LG", style = HudType.Body) },
        textStyle = HudType.Body.copy(color = palette.ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onAction(SheetAction.PickBrand(sheet.category, query)) }),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.signal, unfocusedBorderColor = HudTokens.Outline, cursorColor = palette.signal),
        shape = HudShapes.tile,
        modifier = Modifier.fillMaxWidth().padding(horizontal = HudSpace.xl),
    )
    if (sheet.loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(HudSpace.xl), color = palette.signal, trackColor = HudTokens.Outline)
    }
    val filtered = sheet.brands.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    LazyColumn(Modifier.padding(top = HudSpace.s)) {
        items(filtered, key = { it.id }) { entry -> EntryRow(entry) { onAction(SheetAction.PickBrand(sheet.category, entry.id)) } }
        if (query.isNotBlank() && filtered.none { it.label.equals(query.trim(), ignoreCase = true) }) {
            item {
                EntryRow(PickerEntry(query, "Look up “${query.trim()}”", "Online")) { onAction(SheetAction.PickBrand(sheet.category, query)) }
            }
        }
        if (!sheet.loading && sheet.brands.isEmpty() && query.isBlank()) {
            item {
                Text(
                    "Nothing bundled for this category yet. Type the brand above to search online.",
                    style = HudType.Body,
                    color = palette.ink.copy(alpha = 0.6f),
                    modifier = Modifier.padding(HudSpace.xl),
                )
            }
        }
    }
}

@Composable
private fun CommandsSheet(sheet: SheetUi.Commands, onAction: (SheetAction) -> Unit) {
    SheetHeader(sheet.title, "Every button this device's code set knows")
    LazyColumn {
        items(sheet.entries, key = { it.id }) { entry -> EntryRow(entry) { onAction(SheetAction.PickCommand(entry.id)) } }
    }
}

/** "Here is where your invisible laser fires from" (docs/hardware/rear-camera-ir-blaster-spec.md §4). */
@Composable
private fun PhoneSheet(sheet: SheetUi.Phone, onAction: (SheetAction) -> Unit) {
    val palette = LocalHudPalette.current
    SheetHeader(
        sheet.profileName ?: "Unknown phone",
        when {
            sheet.profileName == null -> "Not in the emitter registry yet. Choose your phone below."
            sheet.bestGuess -> "Best guess from a partial name or model code. Check it against your phone."
            sheet.matchedAutomatically -> "Detected automatically"
            else -> "Chosen by you"
        },
    )
    LazyColumn {
        if (sheet.profileName != null) item {
            Row(Modifier.padding(horizontal = HudSpace.xl), horizontalArrangement = Arrangement.spacedBy(HudSpace.xl), verticalAlignment = Alignment.CenterVertically) {
                val diagramSize = Modifier.width(120.dp).height(168.dp)
                if (sheet.diagram != null) PhoneBackDiagram(sheet.diagram, diagramSize) else TopEdgeDiagram(diagramSize)
                Column(verticalArrangement = Arrangement.spacedBy(HudSpace.s), modifier = Modifier.weight(1f)) {
                    if (sheet.topFrame) TopEdgeBadge() else Text("REAR CAMERA PLATEAU", style = HudType.Caption, color = palette.signal)
                    sheet.description?.let { Text(it, style = HudType.Body, color = palette.ink) }
                    sheet.offsetMm?.let { (dx, dy) ->
                        Text("${offsetPhrase(dx, dy)} Beyond 1 m the reticle centre is the beam centre; up close Fancy-IR marks where the beam lands.",
                            style = HudType.Body, color = palette.ink.copy(alpha = 0.6f))
                    }
                    if (sheet.diagram != null) Row(horizontalArrangement = Arrangement.spacedBy(HudSpace.m)) {
                        Text("○ 1× LENS", style = HudType.Caption, color = palette.ink.copy(alpha = 0.6f))
                        Text("● IR", style = HudType.Caption, color = palette.infrared)
                    }
                }
            }
        }
        item { Box(Modifier.padding(horizontal = HudSpace.xl)) { SectionLabel("VERIFY WITH AN IR RECEIVER") } }
        item {
            EntryRow(PickerEntry("bench", "Run IR test bench", "9 frames · 1.5 s apart")) { onAction(SheetAction.RunTestBench) }
        }
        item { Box(Modifier.padding(horizontal = HudSpace.xl)) { SectionLabel("NOT RIGHT? CHOOSE YOUR PHONE") } }
        item { EntryRow(PickerEntry("auto", "Detect automatically", null)) { onAction(SheetAction.PickEmitterProfile(null)) } }
        items(sheet.profiles, key = { it.id }) { entry -> EntryRow(entry) { onAction(SheetAction.PickEmitterProfile(entry.id)) } }
    }
}

/** "10 mm right and 20 mm down from the 1× lens (≈22 mm)." Back view, as the spec measures it. */
private fun offsetPhrase(dxMm: Float, dyMm: Float): String {
    val parts = listOfNotNull(
        dxMm.takeIf { it != 0f }?.let { "${abs(it).roundToInt()} mm ${if (it > 0) "right" else "left"}" },
        dyMm.takeIf { it != 0f }?.let { "${abs(it).roundToInt()} mm ${if (it > 0) "down" else "up"}" },
    )
    return "${parts.joinToString(" and ")} from the 1× lens, seen from the back (≈${hypot(dxMm, dyMm).roundToInt()} mm)."
}

@Composable
private fun TopEdgeBadge() {
    val palette = LocalHudPalette.current
    Text(
        "Top-Edge IR Blaster (Tilt phone to fire)",
        style = HudType.Caption,
        color = palette.infrared,
        modifier = Modifier
            .background(palette.infrared.copy(alpha = 0.14f), HudShapes.pill)
            .padding(horizontal = HudSpace.m, vertical = HudSpace.xs),
    )
}

/**
 * The back of a portrait phone with its camera island drawn by shape, and the 1× lens and the IR
 * emitter at their normalised positions inside the island's box. v1 placed every lens bottom-left
 * and pushed emitters off the island; now both points come from the profile.
 */
@Composable
private fun PhoneBackDiagram(diagram: PlateauDiagram, modifier: Modifier) {
    val palette = LocalHudPalette.current
    val glow by rememberInfiniteTransition(label = "emitter").animateFloat(
        initialValue = 0.15f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow",
    )
    Canvas(modifier) {
        val mm = size.width / PHONE_WIDTH_MM
        drawRoundRect(palette.ink.copy(alpha = 0.25f), size = size, cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        val island = islandBox(diagram.shape, mm)
        drawIsland(diagram.shape, island, palette.ink.copy(alpha = 0.12f))
        fun at(x: Float, y: Float) = Offset(island.left + x * island.width, island.top + y * island.height)
        val lens = at(diagram.lensX, diagram.lensY)
        val emitter = at(diagram.emitterX, diagram.emitterY)
        drawLine(palette.infrared.copy(alpha = 0.5f), lens, emitter, 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        val lensRadius = minOf(5 * mm, minOf(island.width, island.height) * 0.2f)
        drawCircle(palette.ink.copy(alpha = 0.7f), lensRadius, lens, style = Stroke(1.5.dp.toPx()))
        drawCircle(palette.ink.copy(alpha = 0.85f), lensRadius * 0.45f, lens)
        drawCircle(palette.infrared.copy(alpha = glow), 4.5f * mm, emitter)
        drawCircle(palette.infrared, 1.8f * mm, emitter)
    }
}

/** Island bounding boxes in phone millimetres. Some boxes reach past the outline, where flash and IR sit on the glass. */
private fun islandBox(shape: IslandShape, mm: Float): Rect = when (shape) {
    IslandShape.SQUIRCLE_TOP_LEFT -> Rect(Offset(6 * mm, 6 * mm), Size(38 * mm, 38 * mm))
    IslandShape.CIRCLE_CENTER -> Rect(Offset((PHONE_WIDTH_MM - 50) / 2 * mm, 8 * mm), Size(50 * mm, 50 * mm))
    IslandShape.CIRCLE_TOP_LEFT -> Rect(Offset(6 * mm, 6 * mm), Size(46 * mm, 36 * mm))
    IslandShape.PILL_TOP_LEFT -> Rect(Offset(6 * mm, 6 * mm), Size(32 * mm, 40 * mm))
    IslandShape.VISOR_TOP -> Rect(Offset(6 * mm, 6 * mm), Size(60 * mm, 22 * mm))
}

private fun DrawScope.drawIsland(shape: IslandShape, box: Rect, color: Color) {
    when (shape) {
        IslandShape.SQUIRCLE_TOP_LEFT -> drawRoundRect(color, box.topLeft, box.size, CornerRadius(box.width * 0.26f))
        IslandShape.CIRCLE_CENTER -> drawCircle(color, box.width / 2, box.center)
        // The ring fills the left of the box; the strip to its right holds the flash.
        IslandShape.CIRCLE_TOP_LEFT -> drawCircle(color, box.height / 2, Offset(box.left + box.height / 2, box.center.y))
        IslandShape.PILL_TOP_LEFT -> {
            val width = box.width * 0.58f
            drawRoundRect(color, Offset(box.left + box.width * 0.06f, box.top), Size(width, box.height), CornerRadius(width / 2))
        }
        IslandShape.VISOR_TOP -> drawRoundRect(color, box.topLeft, box.size, CornerRadius(box.height / 2))
    }
}

/** A top-frame phone: the emitter is on the top edge and the beam leaves upwards, away from the camera axis. */
@Composable
private fun TopEdgeDiagram(modifier: Modifier) {
    val palette = LocalHudPalette.current
    Canvas(modifier) {
        val mm = size.width / PHONE_WIDTH_MM
        val top = 24 * mm
        drawRoundRect(palette.ink.copy(alpha = 0.25f), Offset(0f, top), Size(size.width, size.height - top), CornerRadius(14.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        drawRoundRect(palette.ink.copy(alpha = 0.12f), Offset(6 * mm, top + 6 * mm), Size(30 * mm, 30 * mm), CornerRadius(8 * mm))
        val emitter = Offset(size.width / 2, top)
        for (angle in listOf(-25f, 0f, 25f)) {
            val radians = Math.toRadians(angle.toDouble())
            val end = emitter + Offset((sin(radians) * 20 * mm).toFloat(), (-cos(radians) * 20 * mm).toFloat())
            drawLine(palette.infrared.copy(alpha = 0.6f), emitter, end, 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        }
        drawCircle(palette.infrared.copy(alpha = 0.3f), 4.5f * mm, emitter)
        drawCircle(palette.infrared, 1.8f * mm, emitter)
    }
}

private const val PHONE_WIDTH_MM = 72f
