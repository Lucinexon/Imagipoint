package com.imagipoint.watch.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process

/**
 * Single owner of the orientation sensor registration. Creates and tears down its own
 * handler thread so repeated start/stop cycles cannot accumulate listeners or threads.
 *
 * TYPE_GAME_ROTATION_VECTOR is preferred because it is not affected by magnetic
 * disturbances; TYPE_ROTATION_VECTOR is the fallback when the watch lacks it.
 */
class MotionSensor(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onOrientationSample(quaternion: FloatArray, timestampNanos: Long)
        fun onSensorReliabilityChanged(reliable: Boolean)
        fun onSensorError(message: String)
    }

    private val sensorManager = context.applicationContext.getSystemService(SensorManager::class.java)
    private val lock = Any()
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var sensor: Sensor? = null
    private var started = false
    private var lastAccuracy = -1

    val sensorName: String? get() = sensor?.name

    fun start(): Boolean {
        synchronized(lock) {
            if (started) return true
            val manager = sensorManager
            if (manager == null) {
                listener.onSensorError("Sensor service is unavailable on this watch.")
                return false
            }
            val selected = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (selected == null) {
                listener.onSensorError("This watch does not provide a rotation-vector sensor.")
                return false
            }
            sensor = selected
            val sensorThread = HandlerThread(
                "ImagiPoint-motion",
                Process.THREAD_PRIORITY_DISPLAY,
            )
            sensorThread.start()
            thread = sensorThread
            handler = Handler(sensorThread.looper)
            val registered = try {
                manager.registerListener(sensorEventListener, selected, SAMPLE_PERIOD_US, 0, handler)
            } catch (_: Throwable) {
                false
            }
            if (!registered) {
                sensorThread.quitSafely()
                thread = null
                handler = null
                sensor = null
                listener.onSensorError("The orientation sensor could not be started.")
                return false
            }
            started = true
            lastAccuracy = -1
            return true
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!started) {
                thread?.quitSafely()
                thread = null
                handler = null
                sensor = null
                return
            }
            started = false
            runCatching { sensorManager?.unregisterListener(sensorEventListener) }
            thread?.quitSafely()
            thread = null
            handler = null
            sensor = null
            lastAccuracy = -1
        }
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!started) return
            if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR &&
                event.sensor.type != Sensor.TYPE_ROTATION_VECTOR
            ) {
                return
            }
            if (event.values.size >= 4) {
                listener.onOrientationSample(event.values, event.timestamp)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            if (!started) return
            if (accuracy == lastAccuracy) return
            lastAccuracy = accuracy
            // LOW is common at startup and still usable. UNRELIABLE is not.
            listener.onSensorReliabilityChanged(accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE)
        }
    }

    private companion object {
        const val SAMPLE_PERIOD_US = 10_000
    }
}
