package com.imagipoint.watch.hid

/**
 * Pure HID mouse descriptor constants, separated from Android Bluetooth classes so the
 * descriptor and report format can be validated by fast JVM tests.
 *
 * The descriptor is adapted from WearMouse (Apache License 2.0); see THIRD_PARTY_NOTICES.md.
 */
object MouseDescriptor {
    const val REPORT_ID: Int = 1
    const val REPORT_LENGTH: Int = 4

    /**
     * HID mouse: three absolute button bits + five padding bits, then three signed
     * relative bytes (X, Y, wheel) with logical range -127..127.
     */
    val BYTES: ByteArray = bytes(
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x02, // Usage (Mouse)
        0xA1, 0x01, // Collection (Application)
        0x85, REPORT_ID, //   Report ID (1)
        0x09, 0x01, //   Usage (Pointer)
        0xA1, 0x00, //   Collection (Physical)
        0x05, 0x09, //     Usage Page (Button)
        0x19, 0x01, //     Usage Minimum (1)
        0x29, 0x03, //     Usage Maximum (3)
        0x15, 0x00, //     Logical Minimum (0)
        0x25, 0x01, //     Logical Maximum (1)
        0x75, 0x01, //     Report Size (1)
        0x95, 0x03, //     Report Count (3)
        0x81, 0x02, //     Input (Data, Variable, Absolute)
        0x75, 0x05, //     Report Size (5)
        0x95, 0x01, //     Report Count (1)
        0x81, 0x01, //     Input (Constant) ; padding
        0x05, 0x01, //     Usage Page (Generic Desktop)
        0x09, 0x30, //     Usage (X)
        0x09, 0x31, //     Usage (Y)
        0x09, 0x38, //     Usage (Wheel)
        0x15, 0x81, //     Logical Minimum (-127)
        0x25, 0x7F, //     Logical Maximum (127)
        0x75, 0x08, //     Report Size (8)
        0x95, 0x03, //     Report Count (3)
        0x81, 0x06, //     Input (Data, Variable, Relative)
        0xC0, //   End Collection
        0xC0, // End Collection
    )

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
