package com.imagipoint.watch.motion

import com.imagipoint.watch.settings.Handedness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorMapperTest {
    private fun process(
        mapper: CursorMapper,
        dt: Float = 0.1f,
        heading: Float = 0f,
        pitch: Float = 0f,
        sensitivity: Float = 1f,
        smoothingMs: Int = 0,
        deadZone: Float = 0f,
        handedness: Handedness = Handedness.RIGHT,
    ) = mapper.process(dt, heading, pitch, sensitivity, smoothingMs, deadZone, handedness)

    @Test
    fun rightwardHeadingMovesCursorRight() {
        val out = process(CursorMapper(), heading = 0.1f)
        assertEquals(90, out.dx)
        assertEquals(0, out.dy)
    }

    @Test
    fun raisingArmMovesCursorUpInHidCoordinates() {
        val out = process(CursorMapper(), pitch = 0.1f)
        assertEquals(0, out.dx)
        assertEquals(-90, out.dy)
    }

    @Test
    fun leftHandednessFlipsOnlyHorizontalAxis() {
        val out = process(CursorMapper(), heading = 0.1f, pitch = 0.1f, handedness = Handedness.LEFT)
        assertEquals(-90, out.dx)
        assertEquals(-90, out.dy)
    }

    @Test
    fun sensitivityScalesMovement() {
        val low = process(CursorMapper(), heading = 0.1f, sensitivity = 0.5f)
        val high = process(CursorMapper(), heading = 0.1f, sensitivity = 2f)
        assertEquals(45, low.dx)
        assertEquals(180.coerceAtMost(CursorMapper.MAX_REPORT_DELTA), high.dx)
    }

    @Test
    fun deadZoneSuppressesSmallRates() {
        val out = process(CursorMapper(), heading = 0.01f, deadZone = 1800f)
        assertEquals(0, out.dx)
        assertEquals(0, out.dy)
    }

    @Test
    fun smoothingReducesInitialResponse() {
        val smoothed = process(CursorMapper(), heading = 0.1f, smoothingMs = 100)
        val raw = process(CursorMapper(), heading = 0.1f, smoothingMs = 0)
        assertTrue("smoothed=${smoothed.dx} raw=${raw.dx}", smoothed.dx in 1 until raw.dx)
    }

    @Test
    fun largeFlickClampsAndDropsOverflow() {
        val mapper = CursorMapper()
        val first = process(mapper, dt = 0.01f, heading = 5f)
        assertEquals(CursorMapper.MAX_REPORT_DELTA, first.dx)
        val second = process(mapper, dt = 0.01f, heading = 0f)
        assertEquals(0, second.dx)
    }

    @Test
    fun subPixelMovementAccumulates() {
        val mapper = CursorMapper()
        var moved = 0
        repeat(10) {
            moved += process(mapper, dt = 0.01f, heading = 0.0005f).dx
        }
        assertTrue("Sub-pixel movement should accumulate, moved=$moved", moved > 0)
    }

    @Test
    fun invalidDeltaResetsStateAndReturnsZero() {
        val mapper = CursorMapper()
        assertTrue(process(mapper, heading = 0.1f).hasMovement)
        val out = process(mapper, heading = Float.NaN)
        assertEquals(0, out.dx)
        assertEquals(0, out.dy)
    }

    @Test
    fun nonPositiveDtIsRejected() {
        assertEquals(0, process(CursorMapper(), dt = 0f).dx)
        assertEquals(0, process(CursorMapper(), dt = -0.1f).dy)
    }

    @Test
    fun extremeSensitivityAndDeadZoneAreBounded() {
        val out = process(CursorMapper(), heading = 0.1f, sensitivity = 500f, deadZone = -10f)
        assertTrue(out.dx <= CursorMapper.MAX_REPORT_DELTA)
    }
}
