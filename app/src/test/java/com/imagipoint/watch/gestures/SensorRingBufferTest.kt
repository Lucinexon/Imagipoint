package com.imagipoint.watch.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorRingBufferTest {
    @Test
    fun interpolationIsLinear() {
        val buffer = SensorRingBuffer(8)
        buffer.add(1_000L, 0f, 10f, 100f)
        buffer.add(2_000L, 2f, 20f, 200f)
        val out = FloatArray(3)
        assertTrue(buffer.interpolateInto(1_500L, out, 0))
        assertEquals(1f, out[0], 0.0001f)
        assertEquals(15f, out[1], 0.0001f)
        assertEquals(150f, out[2], 0.0001f)
    }

    @Test
    fun interpolationScalesForAccelUnitConversion() {
        val buffer = SensorRingBuffer(8)
        buffer.add(1_000L, 9.80665f, 0f, 0f)
        val out = FloatArray(3)
        assertTrue(buffer.interpolateInto(1_000L, out, 0, 1f / 9.80665f))
        assertEquals(1f, out[0], 0.0001f)
    }

    @Test
    fun outsideRangeIsRejected() {
        val buffer = SensorRingBuffer(8)
        buffer.add(1_000L, 0f, 0f, 0f)
        buffer.add(2_000L, 1f, 1f, 1f)
        assertFalse(buffer.interpolateInto(999L, FloatArray(3), 0))
        assertFalse(buffer.interpolateInto(2_001L, FloatArray(3), 0))
    }

    @Test
    fun nonFiniteAndNonMonotonicSamplesAreRejected() {
        val buffer = SensorRingBuffer(8)
        assertFalse(buffer.add(1_000L, Float.NaN, 0f, 0f))
        assertTrue(buffer.add(1_000L, 0f, 0f, 0f))
        assertFalse(buffer.add(1_000L, 1f, 1f, 1f))
        assertFalse(buffer.add(999L, 1f, 1f, 1f))
        assertEquals(3L, buffer.rejectedSamples)
    }

    @Test
    fun ringWrapsWithoutBreakingChronologicalOrder() {
        val buffer = SensorRingBuffer(8)
        for (i in 1..12) {
            assertTrue(buffer.add(i * 1_000L, i.toFloat(), 0f, 0f))
        }
        assertEquals(5_000L, buffer.oldestTimestampNs())
        assertEquals(12_000L, buffer.latestTimestampNs())
        val out = FloatArray(3)
        assertTrue(buffer.interpolateInto(5_500L, out, 0))
        assertEquals(5.5f, out[0], 0.0001f)
    }

    @Test
    fun rateIsComputedFromRecentTimestamps() {
        val buffer = SensorRingBuffer(16)
        for (i in 0..10) buffer.add(1_000_000L + i * 5_000_000L, 0f, 0f, 0f)
        assertEquals(200f, buffer.rateHz(), 0.01f)
    }

    @Test
    fun clearResetsCountAndRate() {
        val buffer = SensorRingBuffer(8)
        buffer.add(1_000L, 0f, 0f, 0f)
        buffer.add(2_000L, 0f, 0f, 0f)
        buffer.clear()
        assertEquals(0, buffer.count)
        assertNull(buffer.latestTimestampNs())
        assertEquals(0f, buffer.rateHz())
    }
}
