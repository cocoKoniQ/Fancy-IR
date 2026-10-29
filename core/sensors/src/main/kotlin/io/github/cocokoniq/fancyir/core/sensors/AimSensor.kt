package io.github.cocokoniq.fancyir.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.cocokoniq.fancyir.core.domain.spatial.AimDirection
import io.github.cocokoniq.fancyir.core.domain.spatial.AimMath
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Where the back camera points, from the rotation-vector sensor (magnetometer-referenced, so
 * azimuths are absolute; falls back to the game rotation vector, which drifts in yaw).
 */
class AimSensor(context: Context) {

    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)

    val isAbsolute: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    fun directions(): Flow<AimDirection> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }
        val matrix = FloatArray(9)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                trySend(AimMath.fromRotationMatrix(matrix))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()
}
