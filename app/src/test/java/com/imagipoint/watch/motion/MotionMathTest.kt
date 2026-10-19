package com.imagipoint.watch.motion

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMathTest {
    @Test
    fun identityPointsAlongPositiveY() {
        val p = MotionMath.pointingVector(floatArrayOf(0f, 0f, 0f, 1f))!!
        assertEquals(0f, p[0], 0.0001f)
        assertEquals(1f, p[1], 0.0001f)
        assertEquals(0f, p[2], 0.0001f)
        assertEquals(0f, MotionMath.heading(p), 0.0001f)
        assertEquals(0f, MotionMath.pitch(p), 0.0001f)
    }

    @Test
    fun negativeYawRotationTurnsPointingRight() {
        val angle = -0.4f
        val p = MotionMath.pointingVector(
            floatArrayOf(0f, 0f, kotlin.math.sin(angle / 2f), kotlin.math.cos(angle / 2f)),
        )!!
        assertTrue(MotionMath.heading(p) > 0f)
    }

    @Test
    fun raisingArmIncreasesPitch() {
        val angle = 0.4f
        val p = MotionMath.pointingVector(
            floatArrayOf(kotlin.math.sin(angle / 2f), 0f, 0f, kotlin.math.cos(angle / 2f)),
        )!!
        assertEquals(0.4f, MotionMath.pitch(p), 0.0001f)
    }

    @Test
    fun headingWrapsAcrossMinusPiAndPi() {
        assertEquals(0f, MotionMath.wrapAngle(2 * PI.toFloat()), 0.0001f)
        val wrapped = MotionMath.wrapAngle(4.0f)
        assertTrue(wrapped < 0f)
    }

    @Test
    fun invalidQuaternionReturnsNull() {
        assertNull(MotionMath.normalize(floatArrayOf(Float.NaN, 0f, 0f, 1f)))
        assertNull(MotionMath.normalize(floatArrayOf(0f, 0f, 0f, 0f)))
        assertNull(MotionMath.pointingVector(floatArrayOf(0f, 0f, 0f, 0f)))
    }

    @Test
    fun quaternionIsNormalizedBeforeUse() {
        val normalized = MotionMath.normalize(floatArrayOf(0f, 0f, 0f, 1.2f))!!
        assertEquals(1f, normalized[3], 0.0001f)
        assertNull(MotionMath.normalize(floatArrayOf(0f, 0f, 0f, 2f)))
    }

    @Test
    fun allocationFreeVariantsMatchAllocatingVariantsAndRejectBadData() {
        val quaternion = floatArrayOf(0f, 0f, 0.38268343f, 0.92387953f)
        val normalized = FloatArray(4)
        val pointing = FloatArray(3)
        assertTrue(MotionMath.normalizeInto(quaternion, normalized))
        assertTrue(MotionMath.pointingVectorInto(quaternion, pointing))
        val expected = MotionMath.pointingVector(quaternion)!!
        assertEquals(expected[0], pointing[0], 0.0001f)
        assertEquals(expected[1], pointing[1], 0.0001f)
        assertEquals(expected[2], pointing[2], 0.0001f)
        assertFalse(MotionMath.normalizeInto(floatArrayOf(Float.NaN, 0f, 0f, 1f), normalized))
        assertFalse(MotionMath.pointingVectorInto(floatArrayOf(0f, 0f, 0f, 0f), pointing))
        assertFalse(MotionMath.pointingVectorInto(floatArrayOf(0f, 0f, 0f, 1f), FloatArray(2)))
    }

    @Test
    fun outlierThresholdUsesBothAngleAndSpeed() {
        assertFalse(MotionMath.exceedsOutlierThreshold(1.0f, 0.1f))
        assertTrue(MotionMath.exceedsOutlierThreshold(1.5f, 0.1f))
        assertTrue(MotionMath.exceedsOutlierThreshold(0.7f, 0.01f))
        assertTrue(MotionMath.exceedsOutlierThreshold(0.1f, Float.NaN))
    }
}
