/*
 * Mouse report descriptor and SDP structure adapted from WearMouse
 * (https://github.com/ginkage/wearmouse), Copyright 2018 Google LLC,
 * licensed under the Apache License, Version 2.0.
 *
 * ImagiPoint uses a mouse-only descriptor with report ID 1, its own SDP name,
 * and independent Kotlin report/lifecycle code. See THIRD_PARTY_NOTICES.md.
 */
package com.imagipoint.watch.hid

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings

object HidConstants {
    const val REPORT_ID: Int = MouseDescriptor.REPORT_ID
    const val REPORT_LENGTH = MouseDescriptor.REPORT_LENGTH

    val MOUSE_REPORT_DESCRIPTOR: ByteArray = MouseDescriptor.BYTES

    val SDP_SETTINGS: BluetoothHidDeviceAppSdpSettings = BluetoothHidDeviceAppSdpSettings(
        "ImagiPoint",
        "Wear OS air mouse",
        "ImagiPoint",
        BluetoothHidDevice.SUBCLASS1_MOUSE,
        MOUSE_REPORT_DESCRIPTOR,
    )

    val QOS_SETTINGS: BluetoothHidDeviceAppQosSettings = BluetoothHidDeviceAppQosSettings(
        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
        800,
        9,
        0,
        11_250,
        BluetoothHidDeviceAppQosSettings.MAX,
    )
}
