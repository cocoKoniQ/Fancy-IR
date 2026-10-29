package io.github.cocokoniq.fancyir.feature.hud

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cocokoniq.fancyir.core.designsystem.HudPalette
import io.github.cocokoniq.fancyir.core.designsystem.LocalHudPalette
import io.github.cocokoniq.fancyir.core.domain.gesture.PointerInput
import io.github.cocokoniq.fancyir.core.vision.ViewfinderController
import kotlinx.coroutines.CancellationException

/** The whole app: live viewfinder underneath, HUD on top, the entire screen as the touch target. */
@Composable
fun HudScreen(viewModel: HudViewModel, viewfinder: ViewfinderController) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) {
        if (!hasCamera) permission.launch(Manifest.permission.CAMERA)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    if (hasCamera) {
        LaunchedEffect(lifecycleOwner) {
            try {
                viewfinder.bind(lifecycleOwner)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Wand mode keeps working on priors alone (DESIGN.md §2.7).
                Log.e("FancyIR", "Camera unavailable", e)
            }
        }
    }

    val surfaceRequest by viewfinder.surfaceRequest.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    BackHandler(enabled = ui.picker != null) { viewModel.onClosePicker() }
    BackHandler(enabled = ui.picker == null && ui.navMode) { viewModel.onNavBack() }

    CompositionLocalProvider(LocalHudPalette provides if (ui.night) HudPalette.Night else HudPalette.Day) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
            if (ui.night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            GestureSurface(onInput = viewModel::onPointer, modifier = Modifier.fillMaxSize()) {
                HudOverlay(ui, Modifier.fillMaxSize())
            }
            ui.picker?.let { PickerPanel(it, onPick = viewModel::onPick, onDismiss = viewModel::onClosePicker) }
        }
    }
}

/**
 * Forwards the first pointer as raw [PointerInput]s to the pure `GestureArbiter`, plus a
 * display-frame clock while a finger is down so holds charge even when perfectly still.
 */
@Composable
private fun GestureSurface(
    onInput: (PointerInput) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val emit by rememberUpdatedState(onInput)

    LaunchedEffect(pressed) {
        if (pressed) while (true) withFrameMillis { emit(PointerInput.Frame(SystemClock.uptimeMillis())) }
    }

    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                emit(PointerInput.Down(down.position.x, down.position.y, down.uptimeMillis))
                var finished = false
                try {
                    while (!finished) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        when {
                            change == null || event.changes.count { it.pressed } > 1 -> {
                                emit(PointerInput.Cancel(SystemClock.uptimeMillis()))
                                finished = true
                            }
                            !change.pressed -> {
                                emit(PointerInput.Up(change.uptimeMillis))
                                finished = true
                            }
                            change.positionChanged() -> {
                                emit(PointerInput.Move(change.position.x, change.position.y, change.uptimeMillis))
                                change.consume()
                            }
                        }
                    }
                } finally {
                    if (!finished) emit(PointerInput.Cancel(SystemClock.uptimeMillis()))
                    pressed = false
                }
            }
        },
    ) {
        content()
    }
}
