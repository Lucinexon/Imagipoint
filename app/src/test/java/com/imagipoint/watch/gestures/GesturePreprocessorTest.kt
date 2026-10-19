package com.imagipoint.watch.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturePreprocessorTest {
    private val startNs = 1_000_000_000L

    private fun fill(
        preprocessor: GesturePreprocessor,
        samples: Int,
        accel: FloatArray = floatArrayOf(0f, 0f, 9.80665f),
        gyro: FloatArray = floatArrayOf(0f, 0f, 0f),
        stepNs: Long = 5_000_000L,
    ) {
        for (i in 0 until samples) {
            val t = startNs + i * stepNs
            preprocessor.addAccel(t, accel[0], accel[1], accel[2])
            preprocessor.addGyro(t, gyro[0], gyro[1], gyro[2])
        }
    }

    @Test
    fun requiresOneSecondOfBothStreams() {
        val preprocessor = GesturePreprocessor(applyBandpass = false)
        fill(preprocessor, samples = 100)
        assertNull(preprocessor.buildWindow())
        assertTrue(preprocessor.rejectedWindows > 0)
    }

    @Test
    fun buildsExactlyTwoHundredSixAxisSamples() {
        val preprocessor = GesturePreprocessor(applyBandpass = false)
        fill(preprocessor, samples = 250)
        val window = preprocessor.buildWindow()
        assertNotNull(window)
        assertEquals(GesturePreprocessor.WINDOW_SAMPLES * GesturePreprocessor.CHANNELS, window!!.size)
        assertTrue(preprocessor.hasCoverage())
        assertEquals(200f, preprocessor.accelRateHz, 0.5f)
        assertEquals(200f, preprocessor.gyroRateHz, 0.5f)
    }

    @Test
    fun convertsAccelerationToGAndKeepsAccelThenGyroChannelOrder() {
        val preprocessor = GesturePreprocessor(applyBandpass = false)
        val accel = floatArrayOf(9.80665f, 19.6133f, 4.903325f)
        val gyro = floatArrayOf(0.1f, 0.2f, 0.3f)
        fill(preprocessor, 250, accel, gyro)
        val window = preprocessor.buildWindow()!!
        val offset = 100 * GesturePreprocessor.CHANNELS
        assertEquals(1f, window[offset], 0.0001f)
        assertEquals(2f, window[offset + 1], 0.0001f)
        assertEquals(0.5f, window[offset + 2], 0.0001f)
        assertEquals(0.1f, window[offset + 3], 0.0001f)
        assertEquals(0.2f, window[offset + 4], 0.0001f)
        assertEquals(0.3f, window[offset + 5], 0.0001f)
    }

    @Test
    fun bandpassRemovesConstantOffsetAndStaysFinite() {
        val preprocessor = GesturePreprocessor(applyBandpass = true)
        fill(preprocessor, 250)
        val window = preprocessor.buildWindow()!!
        assertTrue(window.all { it.isFinite() })
        val maxAbs = window.maxOf { kotlin.math.abs(it) }
        assertTrue("maxAbs=$maxAbs", maxAbs < 0.01f)
    }

    @Test
    fun nonFiniteSensorValuesAreRejected() {
        val preprocessor = GesturePreprocessor(applyBandpass = false)
        assertTrue(preprocessor.addAccel(startNs, Float.NaN, 0f, 0f).not())
        assertTrue(preprocessor.addGyro(startNs, 0f, Float.POSITIVE_INFINITY, 0f).not())
        assertEquals(2L, preprocessor.rejectedSamples)
    }

    @Test
    fun resetDropsCoverage() {
        val preprocessor = GesturePreprocessor(applyBandpass = false)
        fill(preprocessor, 250)
        preprocessor.reset()
        assertTrue(preprocessor.hasCoverage().not())
        assertNull(preprocessor.buildWindow())
    }
}
