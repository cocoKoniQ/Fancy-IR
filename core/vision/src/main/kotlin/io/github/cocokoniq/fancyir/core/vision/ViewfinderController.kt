package io.github.cocokoniq.fancyir.core.vision

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
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
import com.google.common.util.concurrent.ListenableFuture
import io.github.cocokoniq.fancyir.core.domain.hardware.Parallax
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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.roundToInt
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

    private val _focusDistanceM = MutableStateFlow<Float?>(null)

    /** Autofocus distance to the subject, in 5 cm steps; null when unknown or beyond 2 m. */
    val focusDistanceM: StateFlow<Float?> = _focusDistanceM.asStateFlow()

    /** Horizontal field of view of the portrait preview, from the lens and sensor size. */
    @Volatile
    var horizontalFovDeg: Float = DEFAULT_HFOV_DEG
        private set

    private val analysisExecutor = Executors.newSingleThreadExecutor { Thread(it, "fancyir-analysis") }
    private val sampler = TileSampler()

    /** Binds to [lifecycleOwner] and suspends until cancelled, then unbinds. */
    suspend fun bind(lifecycleOwner: LifecycleOwner) {
        val provider = ProcessCameraProvider.getInstance(context).await()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA

        val previewBuilder = Preview.Builder()
        preferredFpsRange(provider, selector)?.let { range -> applyFpsRange(previewBuilder, range) }
        portraitFieldOfView(provider, selector)?.let { horizontalFovDeg = it }
        observeFocusDistance(previewBuilder)
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

    /** Portrait: the sensor's short side runs across the screen. */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun portraitFieldOfView(provider: ProcessCameraProvider, selector: CameraSelector): Float? {
        val info = selector.filter(provider.availableCameraInfos).firstOrNull() ?: return null
        val characteristics = Camera2CameraInfo.from(info)
        val focal = characteristics.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return null
        val sensor = characteristics.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        if (focal <= 0f) return null
        return (2 * atan(minOf(sensor.width, sensor.height) / (2 * focal)) * 180 / PI).toFloat()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun observeFocusDistance(builder: Preview.Builder) {
        Camera2Interop.Extender(builder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                val diopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: return
                val distance = Parallax.distanceFromDiopters(diopters)?.takeIf { it < 2f }
                _focusDistanceM.value = distance?.let { (it * 20).roundToInt() / 20f }
            }
        })
    }

    private companion object {
        const val SALIENT_CONTRAST = 0.06f
        const val DEFAULT_HFOV_DEG = 66f
    }
}

/** Suspends until the future completes; cancelling the caller cancels the future. */
private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (e: ExecutionException) {
                continuation.resumeWithException(e.cause ?: e)
            }
        },
        Runnable::run,
    )
    continuation.invokeOnCancellation { cancel(false) }
}
