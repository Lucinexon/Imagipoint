package com.imagipoint.watch.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MouseReportTest {
    @Test
    fun buttonsAreMaskedToThreeSupportedButtons() {
        val bytes = MouseReport(0xFF, 0, 0, 0).toByteArray()
        assertEquals(MouseButtons.MASK, bytes[0].toInt())
    }

    @Test
    fun axesAreClampedToSignedByteLogicalRange() {
        val bytes = MouseReport(-1, 1000, -1000, 999).toByteArray()
        assertEquals(MouseButtons.MASK, bytes[0].toInt())
        assertEquals(MouseReport.MAX_AXIS, bytes[1].toInt())
        assertEquals(MouseReport.MIN_AXIS, bytes[2].toInt())
        assertEquals(MouseReport.MAX_AXIS, bytes[3].toInt())
    }

    @Test
    fun releaseAllIsAnAllZeroFourByteReport() {
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), MouseReport.RELEASE_ALL.toByteArray())
        assertEquals(4, MouseReport.RELEASE_ALL.toByteArray().size)
    }

    @Test
    fun floatsRoundAndClamp() {
        val report = MouseReport.fromFloats(
            MouseButtons.LEFT or MouseButtons.RIGHT,
            12.6f,
            -12.6f,
            200f,
        )
        assertEquals(13, report.dx)
        assertEquals(-13, report.dy)
        assertEquals(127, report.wheel)
        assertEquals(3, report.buttons)
    }

    @Test
    fun clampedPreservesButtonsAndAxes() {
        val report = MouseReport.clamped(MouseButtons.MIDDLE or 0xF0, 5, -5, 0)
        assertEquals(MouseButtons.MIDDLE, report.buttons)
        assertEquals(5, report.dx)
        assertEquals(-5, report.dy)
    }
}
