package com.imagipoint.watch.motion

import com.imagipoint.watch.settings.Handedness
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Converts angular deltas into signed, relative HID mouse movement.
 *
 * This class is pure and deterministic so axes, handedness, dead zone, smoothing, clamping
 * and overflow behavior can be unit-tested without Android sensors or Bluetooth.
 */
class CursorMapper(
    private val basePixelsPerRadian: Float = DEFAULT_BASE_PIXELS_PER_RADIAN,
) {
    data class Output(
        val dx: Int,
        val dy: Int,
        val yawRateRadPerSec: Float,
        val pitchRateRadPerSec: Float,
    ) {
        val hasMovement: Boolean get() = dx != 0 || dy != 0

        companion object {
            val ZERO = Output(0, 0, 0f, 0f)
        }
    }

    private var filteredYawRate = 0f
    private var filteredPitchRate = 0f
    private var accumulatedX = 0f
    private var accumulatedY = 0f

    fun reset() {
        filteredYawRate = 0f
        filteredPitchRate = 0f
        accumulatedX = 0f
        accumulatedY = 0f
    }

    fun process(
        dtSeconds: Float,
        headingDeltaRad: Float,
        pitchDeltaRad: Float,
        sensitivity: Float,
        smoothingMs: Int,
        deadZonePxPerSecond: Float,
        handedness: Handedness,
    ): Output {
        if (!dtSeconds.isFinite() || dtSeconds <= 0f ||
            !headingDeltaRad.isFinite() || !pitchDeltaRad.isFinite()
        ) {
            reset()
            return Output.ZERO
        }
        val safeDt = dtSeconds.coerceAtMost(MAX_DT_SECONDS)
        val instantYaw = headingDeltaRad / safeDt
        val instantPitch = pitchDeltaRad / safeDt
        if (!instantYaw.isFinite() || !instantPitch.isFinite()) {
            reset()
            return Output.ZERO
        }

        val tauSeconds = smoothingMs.coerceIn(0, 150) / 1000f
        val alpha = if (tauSeconds <= 0.0001f) 1f else 1f - exp(-safeDt / tauSeconds)
        filteredYawRate += alpha * (instantYaw - filteredYawRate)
        filteredPitchRate += alpha * (instantPitch - filteredPitchRate)

        val safeSensitivity = sensitivity.coerceIn(0.1f, 5f)
        val pixelsPerRadian = basePixelsPerRadian * safeSensitivity
        val deadZoneRadPerSec = (deadZonePxPerSecond.coerceAtLeast(0f) / pixelsPerRadian)
            .coerceAtMost(10f)

        // Radial dead zone avoids the axis-dependent stair-step of per-axis thresholds.
        val speed = hypot(filteredYawRate, filteredPitchRate)
        val scale = if (speed <= deadZoneRadPerSec || speed == 0f) {
            0f
        } else {
            (speed - deadZoneRadPerSec) / speed
        }
        val yawRate = filteredYawRate * scale
        val pitchRate = filteredPitchRate * scale

        val horizontalSign = if (handedness == Handedness.LEFT) -1f else 1f
        val pixelsPerSecondX = yawRate * pixelsPerRadian * horizontalSign
        // HID Y grows downward; raising the arm (positive pitch) should move the cursor up.
        val pixelsPerSecondY = -pitchRate * pixelsPerRadian

        accumulatedX = (accumulatedX + pixelsPerSecondX * safeDt)
            .coerceIn(-MAX_REPORT_DELTA.toFloat(), MAX_REPORT_DELTA.toFloat())
        accumulatedY = (accumulatedY + pixelsPerSecondY * safeDt)
            .coerceIn(-MAX_REPORT_DELTA.toFloat(), MAX_REPORT_DELTA.toFloat())

        val dx = accumulatedX.roundToInt().coerceIn(-MAX_REPORT_DELTA, MAX_REPORT_DELTA)
        val dy = accumulatedY.roundToInt().coerceIn(-MAX_REPORT_DELTA, MAX_REPORT_DELTA)
        // Keep sub-pixel motion for the next report, but deliberately discard the excess
        // beyond the HID range instead of replaying a giant movement later.
        accumulatedX -= dx
        accumulatedY -= dy

        return Output(dx, dy, yawRate, pitchRate)
    }

    companion object {
        const val MAX_REPORT_DELTA = 127
        const val DEFAULT_BASE_PIXELS_PER_RADIAN = 900f
        private const val MAX_DT_SECONDS = 0.15f
    }
}
