package com.imagipoint.watch.settings

import com.imagipoint.watch.gestures.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {
    @Test
    fun roundTripPreservesEverySetting() {
        val original = AppSettings(
            sensitivity = 1.75f,
            smoothingMs = 80,
            deadZonePxPerSecond = 12.5f,
            handedness = Handedness.LEFT,
            gesturesEnabled = true,
            gestureConfidence = 0.91f,
            gestureCooldownMs = 3200,
            gestureMappings = mapOf(
                "Index Thumb Tap" to GestureAction.LEFT_CLICK,
                "Thumb Up" to GestureAction.SCROLL_UP,
            ),
            showDiagnostics = true,
            selectedDeviceAddress = "AA:BB:CC:DD:EE:FF",
            selectedDeviceName = "Desktop",
        )
        val decoded = SettingsCodec.decode(SettingsCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun missingMapUsesDefaults() {
        val decoded = SettingsCodec.decode(emptyMap())
        assertEquals(AppSettings().gestureMappings, decoded.gestureMappings)
        assertFalse(decoded.gesturesEnabled)
    }

    @Test
    fun explicitlyEmptyMappingsRemainEmpty() {
        val encoded = SettingsCodec.encode(AppSettings(gestureMappings = emptyMap()))
        assertTrue(encoded.getValue("gesture_mappings").isEmpty())
        val decoded = SettingsCodec.decode(encoded)
        assertTrue(decoded.gestureMappings.isEmpty())
    }

    @Test
    fun corruptValuesFallBackSafely() {
        val decoded = SettingsCodec.decode(
            mapOf(
                "schema_version" to "1",
                "cursor_sensitivity" to "not-a-number",
                "cursor_smoothing_ms" to "wat",
                "handedness" to "SIDEWAYS",
                "gestures_enabled" to "maybe",
                "gesture_confidence" to "NaN",
                "cursor_dead_zone" to "Infinity",
            ),
        )
        assertEquals(AppSettings(), decoded)
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val decoded = SettingsCodec.decode(
            mapOf(
                "schema_version" to "1",
                "cursor_sensitivity" to "99",
                "cursor_smoothing_ms" to "-5",
                "cursor_dead_zone" to "1000",
                "gesture_confidence" to "0.1",
                "gesture_cooldown_ms" to "999999",
            ),
        )
        assertEquals(AppSettings.SENSITIVITY_MAX, decoded.sensitivity)
        assertEquals(AppSettings.SMOOTHING_MIN_MS, decoded.smoothingMs)
        assertEquals(AppSettings.DEAD_ZONE_MAX, decoded.deadZonePxPerSecond)
        assertEquals(AppSettings.CONFIDENCE_MIN, decoded.gestureConfidence)
        assertEquals(AppSettings.COOLDOWN_MAX_MS, decoded.gestureCooldownMs)
    }

    @Test
    fun unknownLabelsAndActionsAreDropped() {
        val decoded = SettingsCodec.decode(
            mapOf(
                "schema_version" to "1",
                "gesture_mappings" to "Made Up=LEFT_CLICK|Thumb Up=FLY|Thumb Up=RIGHT_CLICK",
            ),
        )
        assertEquals(mapOf("Thumb Up" to GestureAction.RIGHT_CLICK), decoded.gestureMappings)
    }

    @Test
    fun blankSelectedDeviceBecomesNull() {
        val decoded = SettingsCodec.decode(
            mapOf(
                "schema_version" to "1",
                "selected_device_address" to " ",
                "selected_device_name" to "",
            ),
        )
        assertNull(decoded.selectedDeviceAddress)
        assertNull(decoded.selectedDeviceName)
    }

    @Test
    fun futureSchemaStillReadsKnownSafeFields() {
        val decoded = SettingsCodec.decode(
            mapOf(
                "schema_version" to "99",
                "cursor_sensitivity" to "1.5",
            ),
        )
        assertEquals(1.5f, decoded.sensitivity)
    }
}
