package com.imagipoint.watch.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseDescriptorTest {
    private val bytes = MouseDescriptor.BYTES.map { it.toInt() and 0xFF }

    @Test
    fun declaresOneMouseReportId() {
        assertEquals(1, MouseDescriptor.REPORT_ID)
        assertEquals(4, MouseDescriptor.REPORT_LENGTH)
        assertEquals(listOf(0x85, 0x01), bytes.windowed(2).first { it[0] == 0x85 })
    }

    @Test
    fun exposesThreeButtonsAndFivePaddingBits() {
        assertTrue(contains(0x95, 0x03))
        assertTrue(contains(0x75, 0x01))
        assertTrue(contains(0x95, 0x01))
        assertTrue(contains(0x81, 0x01))
    }

    @Test
    fun usesSignedRelativeAxesFromMinus127To127() {
        assertTrue(contains(0x15, 0x81))
        assertTrue(contains(0x25, 0x7F))
        assertTrue(contains(0x75, 0x08))
        assertTrue(contains(0x95, 0x03))
        assertTrue(contains(0x81, 0x06))
        assertEquals(0xC0, bytes[bytes.size - 1])
        assertEquals(0xC0, bytes[bytes.size - 2])
    }

    @Test
    fun descriptorReportsXyAndWheelUsages() {
        assertTrue(contains(0x09, 0x30))
        assertTrue(contains(0x09, 0x31))
        assertTrue(contains(0x09, 0x38))
    }

    private fun contains(first: Int, second: Int): Boolean =
        bytes.windowed(2).any { it[0] == first && it[1] == second }
}
