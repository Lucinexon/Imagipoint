package com.imagipoint.watch.gestures

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.imagipoint.watch.settings.AppSettings
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GestureStatus(
    val modelReady: Boolean = false,
    val unavailableReason: String? = null,
    val sha256: String? = null,
    val filter30to90Hz: Boolean = true,
    val windowSamples: Int = GesturePreprocessor.WINDOW_SAMPLES,
    val samplingRateHz: Int = 200,
)

data class GesturePrediction(
    val label: String?,
    val confidence: Float,
    val inferenceMs: Float,
    val accelRateHz: Float,
    val gyroRateHz: Float,
    val windowsRun: Long,
    val rejectedWindows: Long,
    val errorCount: Long,
    val timestampMs: Long,
)

/**
 * Independent gesture inference pipeline. Runs on its own background thread, never touches
 * the cursor path, and only emits a gesture after confidence, stability, re-arm and cooldown
 * checks. Model/sensor failures degrade the gesture feature only.
 */
class GestureEngine(
    context: Context,
    private val settingsProvider: () -> AppSettings,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onStatus(status: GestureStatus)
        fun onPrediction(prediction: GesturePrediction)
        fun onGesture(label: String, confidence: Float)
    }

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val thread = HandlerThread("ImagiPoint-gesture", android.os.Process.THREAD_PRIORITY_BACKGROUND)
    private val handler: Handler
    private val preprocessor = GesturePreprocessor()
    private val stabilizer = GestureStabilizer()
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        GesturePreprocessor.WINDOW_SAMPLES * GesturePreprocessor.CHANNELS * 4,
    ).order(ByteOrder.nativeOrder())
    private val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(GestureLabels.VERIFIED.size * 4)
        .order(ByteOrder.nativeOrder())
    private val inputFloats: java.nio.FloatBuffer = inputBuffer.asFloatBuffer()
    private val outputFloats: java.nio.FloatBuffer = outputBuffer.asFloatBuffer()
    private val outputValues = FloatArray(GestureLabels.VERIFIED.size)

    @Volatile
    private var running = true
    @Volatile
    private var stopped = false
    private var interpreter: Interpreter? = null
    private var model: VerifiedGestureModel? = null
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var lastAttemptNs = 0L
    private var windowsRun = 0L
    private var errorCount = 0L
    private var consecutiveErrors = 0

    init {
        thread.start()
        handler = Handler(thread.looper)
        handler.post { loadAndStart() }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        running = false
        handler.post {
            sensorManager?.unregisterListener(sensorListener)
            model?.close()
            model = null
            interpreter = null
            thread.quitSafely()
        }
    }

    private fun loadAndStart() {
        if (!running) return
        when (val result = GestureModelLoader.load(appContext)) {
            is ModelLoadResult.Unavailable -> {
                fail(result.reason)
                return
            }
            is ModelLoadResult.Ready -> {
                if (!running) {
                    result.model.close()
                    return
                }
                model = result.model
                interpreter = result.model.interpreter
                val manager = sensorManager
                if (manager == null) {
                    fail("Sensor manager is unavailable.")
                    return
                }
                val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                if (accel == null || gyro == null) {
                    fail("This watch does not expose the accelerometer and gyroscope required for gestures.")
                    return
                }
                accelSensor = accel
                gyroSensor = gyro
                val accelOk = try {
                    manager.registerListener(sensorListener, accel, 5_000, 0, handler)
                } catch (_: Throwable) {
                    false
                }
                val gyroOk = try {
                    manager.registerListener(sensorListener, gyro, 5_000, 0, handler)
                } catch (_: Throwable) {
                    false
                }
                if (!accelOk || !gyroOk) {
                    fail("Gesture sensors could not be started.")
                    return
                }
                callbacks.onStatus(
                    GestureStatus(
                        modelReady = true,
                        sha256 = result.model.sha256,
                        filter30to90Hz = true,
                    ),
                )
            }
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!running) return
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    if (event.values.size < 3) return
                    preprocessor.addAccel(
                        event.timestamp,
                        event.values[0],
                        event.values[1],
                        event.values[2],
                    )
                }
                Sensor.TYPE_GYROSCOPE -> {
                    if (event.values.size < 3) return
                    preprocessor.addGyro(
                        event.timestamp,
                        event.values[0],
                        event.values[1],
                        event.values[2],
                    )
                }
                else -> return
            }
            maybeInfer(event.timestamp)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun maybeInfer(sensorTimestampNs: Long) {
        if (!running || interpreter == null) return
        if (!settingsProvider().gesturesEnabled) return
        if (lastAttemptNs != 0L && sensorTimestampNs - lastAttemptNs < HOP_NS) return
        val window = preprocessor.buildWindow()
        if (window == null) {
            lastAttemptNs = sensorTimestampNs
            return
        }
        lastAttemptNs = preprocessor.lastWindowEndNs
        val started = SystemClock.elapsedRealtimeNanos()
        val output = try {
            inputFloats.clear()
            inputFloats.put(window)
            inputBuffer.rewind()
            outputBuffer.clear()
            interpreter?.run(inputBuffer, outputBuffer)
            outputFloats.rewind()
            outputFloats.get(outputValues)
            outputValues
        } catch (t: Throwable) {
            consecutiveErrors++
            errorCount++
            preprocessor.reset()
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                fail("Gesture inference stopped after repeated errors.")
            } else {
                callbacks.onStatus(
                    GestureStatus(
                        modelReady = true,
                        unavailableReason = "Gesture inference error; retrying.",
                        sha256 = model?.sha256,
                    ),
                )
            }
            return
        }
        consecutiveErrors = 0
        windowsRun++
        val inferenceMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        val predicted = validateAndArgmax(output)
        if (predicted == null) {
            callbacks.onPrediction(
                GesturePrediction(
                    label = null,
                    confidence = 0f,
                    inferenceMs = inferenceMs,
                    accelRateHz = preprocessor.accelRateHz,
                    gyroRateHz = preprocessor.gyroRateHz,
                    windowsRun = windowsRun,
                    rejectedWindows = preprocessor.rejectedWindows,
                    errorCount = errorCount,
                    timestampMs = SystemClock.elapsedRealtime(),
                ),
            )
            return
        }
        val settings = settingsProvider()
        val prediction = GesturePrediction(
            label = predicted.first,
            confidence = predicted.second,
            inferenceMs = inferenceMs,
            accelRateHz = preprocessor.accelRateHz,
            gyroRateHz = preprocessor.gyroRateHz,
            windowsRun = windowsRun,
            rejectedWindows = preprocessor.rejectedWindows,
            errorCount = errorCount,
            timestampMs = SystemClock.elapsedRealtime(),
        )
        callbacks.onPrediction(prediction)
        val decision = stabilizer.update(
            label = predicted.first,
            confidence = predicted.second,
            nowMs = SystemClock.elapsedRealtime(),
            threshold = settings.gestureConfidence,
            cooldownMs = settings.gestureCooldownMs.toLong(),
        )
        if (decision != null) {
            callbacks.onGesture(decision.label, decision.confidence)
        }
    }

    private fun validateAndArgmax(output: FloatArray): Pair<String, Float>? {
        var sum = 0f
        var bestIndex = -1
        var best = 0f
        for (i in output.indices) {
            val value = output[i]
            if (!value.isFinite() || value < 0f || value > 1.0001f) return null
            sum += value
            if (value > best) {
                best = value
                bestIndex = i
            }
        }
        if (bestIndex !in GestureLabels.VERIFIED.indices) return null
        if (sum < 0.95f || sum > 1.05f) return null
        return GestureLabels.VERIFIED[bestIndex] to best
    }

    private fun fail(reason: String) {
        running = false
        stopped = true
        sensorManager?.unregisterListener(sensorListener)
        model?.close()
        model = null
        interpreter = null
        callbacks.onStatus(GestureStatus(modelReady = false, unavailableReason = reason))
        thread.quitSafely()
    }

    private companion object {
        const val HOP_NS = 250_000_000L
        const val MAX_CONSECUTIVE_ERRORS = 5
    }
}
