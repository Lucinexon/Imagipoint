package com.imagipoint.watch.gestures

import kotlin.math.min

/**
 * Builds the exact six-axis, 200-sample window expected by the supplied TFLite model.
 *
 * - Accelerometer is converted from m/s^2 to g; gyroscope is already rad/s.
 * - Channels are accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z.
 * - 1.0 s windows are resampled to 200 points (5 ms spacing).
 * - The documented HGAG 30-90 Hz fourth-order bandpass is applied.
 * - Mean/std normalization is embedded in the model and is deliberately not repeated.
 *
 * The returned FloatArray is reused by the caller; it is valid until the next buildWindow.
 */
class GesturePreprocessor(
    private val applyBandpass: Boolean = true,
    private val accelToG: Float = 1f / 9.80665f,
) {
    private val accel = SensorRingBuffer(ACCEL_CAPACITY)
    private val gyro = SensorRingBuffer(GYRO_CAPACITY)
    private val filter = if (applyBandpass) ButterworthBandpass.create() else null
    private val window = FloatArray(WINDOW_SAMPLES * CHANNELS)
    private val scratch = FloatArray(3)
    private var lastEndNs = 0L
    private var windowsBuilt = 0L
    private var windowsRejected = 0L

    val accelRateHz: Float get() = accel.rateHz()
    val gyroRateHz: Float get() = gyro.rateHz()
    val acceptedSamples: Long get() = windowsBuilt
    val rejectedWindows: Long get() = windowsRejected
    val rejectedSamples: Long get() = accel.rejectedSamples + gyro.rejectedSamples

    fun addAccel(timestampNs: Long, x: Float, y: Float, z: Float): Boolean =
        accel.add(timestampNs, x, y, z)

    fun addGyro(timestampNs: Long, x: Float, y: Float, z: Float): Boolean =
        gyro.add(timestampNs, x, y, z)

    fun reset() {
        accel.clear()
        gyro.clear()
        lastEndNs = 0L
    }

    /** Latest time at which both sensor streams have data, or null. */
    fun latestCommonTimestampNs(): Long? {
        val a = accel.latestTimestampNs() ?: return null
        val g = gyro.latestTimestampNs() ?: return null
        return min(a, g)
    }

    fun hasCoverage(): Boolean {
        val end = latestCommonTimestampNs() ?: return false
        val start = end - WINDOW_SPAN_NS
        val accelOldest = accel.oldestTimestampNs() ?: return false
        val gyroOldest = gyro.oldestTimestampNs() ?: return false
        return accelOldest <= start && gyroOldest <= start
    }

    /**
     * Builds a window ending at the latest common timestamp. Returns null until one second
     * of both sensor streams is available, or if interpolation/filtering produces invalid data.
     */
    fun buildWindow(): FloatArray? {
        val endNs = latestCommonTimestampNs() ?: return null
        if (!hasCoverage()) {
            windowsRejected++
            return null
        }
        val startNs = endNs - WINDOW_SPAN_NS
        for (i in 0 until WINDOW_SAMPLES) {
            val target = startNs + i * SAMPLE_INTERVAL_NS
            val offset = i * CHANNELS
            if (!accel.interpolateInto(target, window, offset, accelToG)) {
                windowsRejected++
                return null
            }
            if (!gyro.interpolateInto(target, window, offset + 3, 1f)) {
                windowsRejected++
                return null
            }
        }
        filter?.filterInPlace(window, WINDOW_SAMPLES, CHANNELS)
        for (value in window) {
            if (!value.isFinite()) {
                windowsRejected++
                return null
            }
        }
        lastEndNs = endNs
        windowsBuilt++
        return window
    }

    val lastWindowEndNs: Long get() = lastEndNs

    companion object {
        const val WINDOW_SAMPLES = 200
        const val CHANNELS = 6
        const val SAMPLE_INTERVAL_NS = 5_000_000L
        const val WINDOW_SPAN_NS = (WINDOW_SAMPLES - 1) * SAMPLE_INTERVAL_NS
        const val HOP_MS = 250L
        private const val ACCEL_CAPACITY = 512
        private const val GYRO_CAPACITY = 512
    }
}
