package io.github.cocokoniq.fancyir.core.vision

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import io.github.cocokoniq.fancyir.core.domain.recognition.Fingerprinter
import io.github.cocokoniq.fancyir.core.domain.recognition.LumaChromaTile
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.math.sqrt

/** One analysed frame: the reticle fingerprint plus the scene statistics the HUD needs. */
class FrameObservation(
    /** `SystemClock.uptimeMillis()` when analysed: the same clock as touch events. */
    val timestampMs: Long,
    val fingerprint: FloatArray,
    val meanLuma: Float,
    /** Enough contrast under the reticle to be "a thing" rather than a blank wall. */
    val salient: Boolean,
) {
    val isDark: Boolean get() = meanLuma < DARK_LUMA

    companion object {
        const val DARK_LUMA = 0.12f
    }
}

/**
 * Owns the CameraX session: a SurfaceView-backed preview for the viewfinder and a
 * KEEP_ONLY_LATEST analysis stream at low resolution (ARCHITECTURE.md §4.3). Analysis runs on
 * its own thread and never blocks the preview.
 */
class ViewfinderController(
    private val context: Context,
    private val fingerprinter: Fingerprinter,
) {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _frames = MutableSharedFlow<FrameObservation>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<FrameObservation> = _frames.asSharedFlow()

    private val analysisExecutor = Executors.newSingleThreadExecutor { Thread(it, "fancyir-analysis") }
    private val sampler = TileSampler()

    /** Binds to [lifecycleOwner] and suspends until cancelled, then unbinds. */
    suspend fun bind(lifecycleOwner: LifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val selector = CameraSelector.DEFAULT_BACK_CAMERA

        val previewBuilder = Preview.Builder()
        preferredFpsRange(provider, selector)?.let { range -> applyFpsRange(previewBuilder, range) }
        val preview = previewBuilder.build().apply {
            setSurfaceProvider { request -> _surfaceRequest.value = request }
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .build()
        analysis.setAnalyzer(analysisExecutor, ::analyze)

        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        try {
            awaitCancellation()
        } finally {
            provider.unbind(preview, analysis)
            _surfaceRequest.value = null
        }
    }

    private fun analyze(image: ImageProxy) {
        image.use {
            val tile = sampler.sample(it)
            _frames.tryEmit(
                FrameObservation(
                    timestampMs = SystemClock.uptimeMillis(),
                    fingerprint = fingerprinter.fingerprint(tile),
                    meanLuma = tile.meanLuma,
                    salient = contrast(tile) > SALIENT_CONTRAST,
                ),
            )
        }
    }

    private fun contrast(tile: LumaChromaTile): Float {
        val mean = tile.meanLuma
        var variance = 0f
        for (value in tile.luma) variance += (value - mean) * (value - mean)
        return sqrt(variance / tile.luma.size)
    }

    /**
     * A variable AE range topping out at 60 fps, so the camera may drop to 30 in the dark for
     * longer exposures while the HUD keeps display refresh (ARCHITECTURE.md §1).
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun preferredFpsRange(provider: ProcessCameraProvider, selector: CameraSelector): Range<Int>? {
        val info = selector.filter(provider.availableCameraInfos).firstOrNull() ?: return null
        val ranges = Camera2CameraInfo.from(info)
            .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?: return null
        return ranges
            .filter { it.upper == 60 && it.lower <= 30 }
            .minByOrNull { it.lower }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyFpsRange(builder: Preview.Builder, range: Range<Int>) {
        Camera2Interop.Extender(builder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
    }

    private companion object {
        const val SALIENT_CONTRAST = 0.06f
    }
}
