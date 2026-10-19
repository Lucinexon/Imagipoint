package com.imagipoint.watch.motion

import com.imagipoint.watch.settings.AppSettings
import kotlin.math.abs

/**
 * Owns the pointing baseline and converts consecutive orientation samples into cursor output.
 *
 * A short warm-up after start/recenter/gap and a hard outlier guard prevent a cursor jump
 * from the first sample, a stale sensor event, or an orientation glitch. Calibration can
 * consume the raw heading/pitch while movement output is suppressed.
 */
class MotionController(private val mapper: CursorMapper = CursorMapper()) {
    data class Frame(
        val valid: Boolean,
        val headingRad: Float,
        val pitchRad: Float,
        val angularSpeedRadPerSec: Float,
        val output: CursorMapper.Output,
    )

    private val lock = Any()
    private var lastHeading: Float? = null
    private var lastPitch = 0f
    private var lastTimestampNs = 0L
    private var suppressUntilNs = 0L
    private var calibrating = false
    private var paused = false
    private var recenterRequested = true
    private var lastAngularSpeed = 0f
    private val pointing = FloatArray(3)

    fun setCalibrating(value: Boolean) {
        synchronized(lock) {
            if (calibrating != value) {
                calibrating = value
                mapper.reset()
                lastHeading = null
                suppressUntilNs = 0L
            }
        }
    }

    fun setPaused(value: Boolean) {
        synchronized(lock) {
            if (paused != value) {
                paused = value
                mapper.reset()
                lastHeading = null
                if (!value) suppressUntilNs = 0L
            }
        }
    }

    fun recenter() {
        synchronized(lock) {
            mapper.reset()
            lastHeading = null
            recenterRequested = true
            suppressUntilNs = 0L
        }
    }

    fun reset() {
        synchronized(lock) {
            mapper.reset()
            lastHeading = null
            lastTimestampNs = 0L
            suppressUntilNs = 0L
            recenterRequested = true
        }
    }

    fun process(
        quaternion: FloatArray,
        timestampNanos: Long,
        settings: AppSettings,
    ): Frame {
        synchronized(lock) {
            if (!MotionMath.pointingVectorInto(quaternion, pointing)) return invalidFrame()
            if (MotionMath.horizontalLength(pointing) < MotionMath.MIN_HORIZONTAL) {
                // Near-vertical pointing makes heading ill-conditioned. Drop the baseline so
                // returning to a usable posture cannot create a spurious jump.
                lastHeading = null
                return invalidFrame()
            }
            val heading = MotionMath.heading(pointing)
            val pitch = MotionMath.pitch(pointing)
            val previousHeading = lastHeading
            if (previousHeading == null || recenterRequested || lastTimestampNs == 0L) {
                beginBaseline(timestampNanos, heading, pitch)
                return Frame(true, heading, pitch, 0f, CursorMapper.Output.ZERO)
            }

            val dtNs = timestampNanos - lastTimestampNs
            if (dtNs <= 0L || dtNs > MAX_GAP_NS) {
                beginBaseline(timestampNanos, heading, pitch)
                return Frame(true, heading, pitch, 0f, CursorMapper.Output.ZERO)
            }
            val dt = dtNs / 1_000_000_000f
            val headingDelta = MotionMath.wrapAngle(heading - previousHeading)
            val pitchDelta = pitch - lastPitch
            lastAngularSpeed = maxOf(
                abs(headingDelta) / dt,
                abs(pitchDelta) / dt,
            ).coerceFinite()

            if (MotionMath.exceedsOutlierThreshold(headingDelta, dt) ||
                MotionMath.exceedsOutlierThreshold(pitchDelta, dt)
            ) {
                beginBaseline(timestampNanos, heading, pitch)
                return Frame(true, heading, pitch, lastAngularSpeed, CursorMapper.Output.ZERO)
            }

            lastHeading = heading
            lastPitch = pitch
            lastTimestampNs = timestampNanos

            if (calibrating || paused || timestampNanos < suppressUntilNs) {
                return Frame(true, heading, pitch, lastAngularSpeed, CursorMapper.Output.ZERO)
            }

            val output = mapper.process(
                dtSeconds = dt,
                headingDeltaRad = headingDelta,
                pitchDeltaRad = pitchDelta,
                sensitivity = settings.sensitivity,
                smoothingMs = settings.smoothingMs,
                deadZonePxPerSecond = settings.deadZonePxPerSecond,
                handedness = settings.handedness,
            )
            return Frame(true, heading, pitch, lastAngularSpeed, output)
        }
    }

    private fun beginBaseline(timestampNanos: Long, heading: Float, pitch: Float) {
        mapper.reset()
        lastHeading = heading
        lastPitch = pitch
        lastTimestampNs = timestampNanos
        recenterRequested = false
        suppressUntilNs = timestampNanos + WARMUP_NS
    }

    private fun invalidFrame(): Frame =
        Frame(false, 0f, 0f, 0f, CursorMapper.Output.ZERO)

    private fun Float.coerceFinite(): Float = if (isFinite()) this else 0f

    companion object {
        const val WARMUP_NS = 320_000_000L
        const val MAX_GAP_NS = 150_000_000L
    }
}
