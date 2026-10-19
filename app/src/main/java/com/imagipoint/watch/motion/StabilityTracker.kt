package com.imagipoint.watch.motion

import kotlin.math.abs

/**
 * Determines whether the wrist is being held still enough to establish a neutral reference.
 * Uses a sliding time window rather than a fixed sample count so it works across sensor rates.
 *
 * Allocation-free: samples live in fixed ring buffers sized for well above realistic sensor
 * rates, so calibration adds zero garbage while it runs at full sensor rate.
 */
class CalibrationTracker(
    private val stableWindowNs: Long = 900_000_000L,
    private val maxCalibrationNs: Long = 4_500_000_000L,
    private val maxHeadingDriftRad: Float = 0.055f,
    private val maxPitchDriftRad: Float = 0.055f,
) {
    enum class Result { COLLECTING, STABLE, TIMED_OUT }

    private val timestampsNs = LongArray(CAPACITY)
    private val headings = FloatArray(CAPACITY)
    private val pitches = FloatArray(CAPACITY)
    private var head = 0
    private var count = 0
    private var startedNs = 0L
    private var lastTimestampNs = 0L

    /** Progress toward a full stable window, not toward the timeout. */
    val progress: Float
        get() {
            if (startedNs == 0L || lastTimestampNs <= startedNs) return 0f
            val elapsed = lastTimestampNs - startedNs
            return (elapsed.toDouble() / stableWindowNs).toFloat().coerceIn(0f, 1f)
        }

    fun reset() {
        head = 0
        count = 0
        startedNs = 0L
        lastTimestampNs = 0L
    }

    fun add(headingRad: Float, pitchRad: Float, timestampNs: Long): Result {
        if (!headingRad.isFinite() || !pitchRad.isFinite() || timestampNs <= 0L) {
            return Result.COLLECTING
        }
        if (startedNs == 0L) startedNs = timestampNs
        lastTimestampNs = timestampNs
        if (count == CAPACITY) {
            head = (head + 1) % CAPACITY
            count--
        }
        val tail = (head + count) % CAPACITY
        timestampsNs[tail] = timestampNs
        headings[tail] = headingRad
        pitches[tail] = pitchRad
        count++
        while (count > 0 && timestampNs - timestampsNs[head] > stableWindowNs) {
            head = (head + 1) % CAPACITY
            count--
        }
        if (timestampNs - startedNs >= maxCalibrationNs) return Result.TIMED_OUT
        if (count < MIN_SAMPLES) return Result.COLLECTING

        var minHeading = Float.POSITIVE_INFINITY
        var maxHeading = Float.NEGATIVE_INFINITY
        var minPitch = Float.POSITIVE_INFINITY
        var maxPitch = Float.NEGATIVE_INFINITY
        for (i in 0 until count) {
            val index = (head + i) % CAPACITY
            val heading = headings[index]
            val pitch = pitches[index]
            if (heading < minHeading) minHeading = heading
            if (heading > maxHeading) maxHeading = heading
            if (pitch < minPitch) minPitch = pitch
            if (pitch > maxPitch) maxPitch = pitch
        }
        val headingDrift = MotionMath.wrapAngle(maxHeading - minHeading)
        val pitchDrift = abs(maxPitch - minPitch)
        return if (headingDrift <= maxHeadingDriftRad && pitchDrift <= maxPitchDriftRad) {
            Result.STABLE
        } else {
            Result.COLLECTING
        }
    }

    companion object {
        const val MIN_SAMPLES = 30

        /** 512 samples is several seconds at realistic watch sensor rates. */
        private const val CAPACITY = 512
    }
}
