package io.github.cocokoniq.fancyir.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import io.github.cocokoniq.fancyir.core.domain.spatial.AimState
import io.github.cocokoniq.fancyir.core.domain.spatial.OrientationFusion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Feeds [OrientationFusion] from the game rotation vector, the magnetometer-referenced rotation
 * vector, the gyroscope and the raw magnetometer (for disturbance detection), on its own thread.
 * Publishes the fused [AimState] at most every [publishIntervalMs].
 */
class AimSensor(context: Context, private val publishIntervalMs: Long = 33) {

    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)

    /** True when the phone has a magnetometer, so headings can be absolute. */
    val isAbsolute: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    fun states(): Flow<AimState> = callbackFlow {
        val manager = sensorManager
        val sensors = listOfNotNull(
            manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
            manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
            manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
        )
        if (manager == null || sensors.none { it.type == Sensor.TYPE_GAME_ROTATION_VECTOR || it.type == Sensor.TYPE_ROTATION_VECTOR }) {
            close()
            return@callbackFlow
        }

        val fusion = OrientationFusion()
        val matrix = FloatArray(9)
        var lastPublishNs = 0L
        val thread = HandlerThread("fancyir-sensors").apply { start() }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(matrix, event.values)
                        fusion.onGameRotation(matrix, event.timestamp)
                    }
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(matrix, event.values)
                        fusion.onAbsoluteRotation(matrix, event.timestamp)
                    }
                    Sensor.TYPE_GYROSCOPE -> fusion.onGyroscope(event.values[0], event.values[1], event.values[2], event.timestamp)
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        fusion.onMagnetometerAccuracy(event.accuracy)
                        fusion.onMagneticField(event.values[0], event.values[1], event.values[2])
                    }
                }
                if (event.timestamp - lastPublishNs >= publishIntervalMs * 1_000_000) {
                    lastPublishNs = event.timestamp
                    trySend(fusion.state)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD || sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    fusion.onMagnetometerAccuracy(accuracy)
                }
            }
        }
        val handler = Handler(thread.looper)
        for (sensor in sensors) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME, handler)
        awaitClose {
            manager.unregisterListener(listener)
            thread.quitSafely()
        }
    }.conflate()
}
