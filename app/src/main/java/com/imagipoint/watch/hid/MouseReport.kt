package com.imagipoint.watch.hid

import kotlin.math.roundToInt

/** Mouse button bit masks used by the HID report descriptor. */
object MouseButtons {
    const val LEFT = 1
    const val RIGHT = 2
    const val MIDDLE = 4
    const val MASK = LEFT or RIGHT or MIDDLE
}

/**
 * Four-byte mouse report: buttons, relative X, relative Y, relative wheel.
 * All axes use the signed byte range -127..127 from the report descriptor.
 */
data class MouseReport(
    val buttons: Int,
    val dx: Int,
    val dy: Int,
    val wheel: Int,
) {
    /** Writes this report into [target] without allocating. */
    fun writeTo(target: ByteArray, offset: Int = 0) {
        writeReport(target, offset, buttons, dx, dy, wheel)
    }

    fun toByteArray(): ByteArray {
        val target = ByteArray(REPORT_SIZE)
        writeTo(target)
        return target
    }

    companion object {
        const val REPORT_SIZE = 4
        const val MIN_AXIS = -127
        const val MAX_AXIS = 127

        /** Allocation-free report encoding used on the HID hot path. */
        fun writeReport(
            target: ByteArray,
            offset: Int = 0,
            buttons: Int,
            dx: Int,
            dy: Int,
            wheel: Int,
        ) {
            target[offset] = (buttons and MouseButtons.MASK).toByte()
            target[offset + 1] = dx.coerceIn(MIN_AXIS, MAX_AXIS).toByte()
            target[offset + 2] = dy.coerceIn(MIN_AXIS, MAX_AXIS).toByte()
            target[offset + 3] = wheel.coerceIn(MIN_AXIS, MAX_AXIS).toByte()
        }

        val RELEASE_ALL = MouseReport(0, 0, 0, 0)

        fun clamped(buttons: Int, dx: Int, dy: Int, wheel: Int): MouseReport = MouseReport(
            buttons = buttons and MouseButtons.MASK,
            dx = dx.coerceIn(MIN_AXIS, MAX_AXIS),
            dy = dy.coerceIn(MIN_AXIS, MAX_AXIS),
            wheel = wheel.coerceIn(MIN_AXIS, MAX_AXIS),
        )

        fun fromFloats(buttons: Int, dx: Float, dy: Float, wheel: Float): MouseReport = clamped(
            buttons,
            dx.roundToInt(),
            dy.roundToInt(),
            wheel.roundToInt(),
        )
    }
}
