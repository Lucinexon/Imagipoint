package com.imagipoint.watch.motion

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Quaternion and pointing-vector mathematics for a wrist-worn air mouse.
 *
 * The watch's +Y axis is the 12 o'clock direction, which is approximately the forearm /
 * pointing direction. Deriving the direction of that vector in the world frame avoids
 * unreliable double integration of acceleration and is insensitive to rolling the wrist
 * around the forearm.
 *
 * Android rotation-vector quaternions are (x, y, z, w) and rotate device coordinates into
 * the world frame. The world frame has +Z up; its X/Y heading is not a compass and may be
 * reset by the platform between sessions, which is why no absolute neutral orientation is
 * persisted.
 */
object MotionMath {
    /** Minimum horizontal length of the pointing vector before heading is meaningful. */
    const val MIN_HORIZONTAL = 0.20f

    fun normalize(values: FloatArray): FloatArray? {
        val out = FloatArray(4)
        return if (normalizeInto(values, out)) out else null
    }

    /** Allocation-free variant used on the 100 Hz pointer path. */
    fun normalizeInto(values: FloatArray, out: FloatArray): Boolean {
        if (values.size < 4 || out.size < 4) return false
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val w = values[3]
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || !w.isFinite()) return false
        val normSq = x * x + y * y + z * z + w * w
        if (!normSq.isFinite() || normSq < MIN_NORM_SQ || normSq > MAX_NORM_SQ) return false
        val inv = 1f / sqrt(normSq)
        out[0] = x * inv
        out[1] = y * inv
        out[2] = z * inv
        out[3] = w * inv
        return true
    }

    /**
     * World-space direction of the watch's +Y (12 o'clock) axis.
     * Returns null for an invalid or grossly non-unit quaternion.
     */
    fun pointingVector(q: FloatArray): FloatArray? {
        val out = FloatArray(3)
        return if (pointingVectorInto(q, out)) out else null
    }

    /**
     * Allocation-free world-space pointing vector. [out] is overwritten on success.
     */
    fun pointingVectorInto(q: FloatArray, out: FloatArray): Boolean {
        if (q.size < 4 || out.size < 3) return false
        val rawX = q[0]
        val rawY = q[1]
        val rawZ = q[2]
        val rawW = q[3]
        if (!rawX.isFinite() || !rawY.isFinite() || !rawZ.isFinite() || !rawW.isFinite()) {
            return false
        }
        val normSq = rawX * rawX + rawY * rawY + rawZ * rawZ + rawW * rawW
        if (!normSq.isFinite() || normSq < MIN_NORM_SQ || normSq > MAX_NORM_SQ) return false
        val inv = 1f / sqrt(normSq)
        val x = rawX * inv
        val y = rawY * inv
        val z = rawZ * inv
        val w = rawW * inv
        // Second column of the rotation matrix.
        val px = 2f * (x * y - w * z)
        val py = 1f - 2f * (x * x + z * z)
        val pz = 2f * (y * z + w * x)
        if (!px.isFinite() || !py.isFinite() || !pz.isFinite()) return false
        out[0] = px
        out[1] = py
        out[2] = pz
        return true
    }

    /** Clockwise-positive heading in radians, measured from world +Y. */
    fun heading(p: FloatArray): Float = atan2(p[0], p[1])

    /** Elevation in radians above the horizontal plane. */
    fun pitch(p: FloatArray): Float = asin(p[2].coerceIn(-1f, 1f))

    fun horizontalLength(p: FloatArray): Float = hypot(p[0], p[1])

    /** Wrap an angle into (-pi, pi]. */
    fun wrapAngle(angle: Float): Float {
        if (!angle.isFinite()) return 0f
        var a = angle % (2.0 * Math.PI).toFloat()
        if (a <= -Math.PI) a += (2.0 * Math.PI).toFloat()
        if (a > Math.PI) a -= (2.0 * Math.PI).toFloat()
        return a
    }

    fun isFinite(vararg values: Float): Boolean = values.all { it.isFinite() }

    fun exceedsOutlierThreshold(angleDelta: Float, dtSeconds: Float): Boolean {
        if (!angleDelta.isFinite() || !dtSeconds.isFinite() || dtSeconds <= 0f) return true
        return abs(angleDelta) > MAX_SAMPLE_ANGLE_RAD ||
            abs(angleDelta) / dtSeconds > MAX_ANGULAR_SPEED_RAD_PER_SEC
    }

    /** A single-sample jump larger than this is treated as a sensor glitch. */
    const val MAX_SAMPLE_ANGLE_RAD = 1.2f
    /** A sustained angular speed above this is not useful pointing motion. */
    const val MAX_ANGULAR_SPEED_RAD_PER_SEC = 25f

    private const val MIN_NORM_SQ = 0.25f
    private const val MAX_NORM_SQ = 2.25f
}
