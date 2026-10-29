package io.github.cocokoniq.fancyir

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.cocokoniq.fancyir.core.designsystem.FancyIrTheme
import io.github.cocokoniq.fancyir.feature.hud.HudScreen
import io.github.cocokoniq.fancyir.feature.hud.HudViewModel
import io.github.cocokoniq.fancyir.feature.hud.TargetPhase

class MainActivity : ComponentActivity() {

    private val graph by lazy { (application as FancyIrApplication).graph }
    private val hud: HudViewModel by viewModels {
        HudViewModel.factory(graph.hudDependencies(resources.displayMetrics.density))
    }
    private var lastVolumeRepeatMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            FancyIrTheme {
                HudScreen(viewModel = hud, viewfinder = graph.viewfinder)
            }
        }
        // Immersive: the viewfinder owns the whole screen; bars return on a swipe.
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Volume keys dial the locked device (DESIGN.md §3.2) and fall back to normal volume when
     * nothing is targeted. Held keys auto-repeat at about 8 Hz.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val up = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return super.dispatchKeyEvent(event)
        }
        if (hud.ui.value.phase != TargetPhase.LOCKED) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = SystemClock.uptimeMillis()
            if (event.repeatCount == 0 || now - lastVolumeRepeatMs >= VOLUME_REPEAT_MS) {
                lastVolumeRepeatMs = now
                hud.onVolumeKey(up)
            }
        }
        return true
    }

    private companion object {
        const val VOLUME_REPEAT_MS = 125L
    }
}
