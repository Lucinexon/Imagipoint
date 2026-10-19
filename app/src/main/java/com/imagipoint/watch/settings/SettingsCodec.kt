package com.imagipoint.watch.settings

import com.imagipoint.watch.gestures.GestureAction
import com.imagipoint.watch.gestures.GestureLabels

/**
 * Versioned, defensive string codec for [AppSettings].
 *
 * Keeping this pure makes persistence and corrupt-data handling testable on the JVM.
 * Unknown values fall back to defaults instead of crashing the app.
 */
object SettingsCodec {
    const val VERSION = "1"

    fun encode(settings: AppSettings): Map<String, String> {
        val s = settings.sanitized()
        return mapOf(
            Keys.VERSION to VERSION,
            Keys.SENSITIVITY to s.sensitivity.toString(),
            Keys.SMOOTHING to s.smoothingMs.toString(),
            Keys.DEAD_ZONE to s.deadZonePxPerSecond.toString(),
            Keys.HANDEDNESS to s.handedness.name,
            Keys.GESTURES_ENABLED to s.gesturesEnabled.toString(),
            Keys.GESTURE_CONFIDENCE to s.gestureConfidence.toString(),
            Keys.GESTURE_COOLDOWN to s.gestureCooldownMs.toString(),
            Keys.GESTURE_MAPPINGS to s.gestureMappings.entries.joinToString("|") { (label, action) ->
                "$label=${action.name}"
            },
            Keys.SHOW_DIAGNOSTICS to s.showDiagnostics.toString(),
            Keys.DEVICE_ADDRESS to (s.selectedDeviceAddress ?: ""),
            Keys.DEVICE_NAME to (s.selectedDeviceName ?: ""),
        )
    }

    fun decode(values: Map<String, String>): AppSettings {
        val defaults = AppSettings()
        if (values[Keys.VERSION] != VERSION) {
            // A future or missing version is read on a best-effort basis, but never trusted.
            return decodeValues(values, defaults)
        }
        return decodeValues(values, defaults)
    }

    private fun decodeValues(values: Map<String, String>, defaults: AppSettings): AppSettings {
        // A missing key means "first run" and receives the conservative default. A present
        // but empty value is an explicit choice to disable every gesture action.
        val encodedMappings = values[Keys.GESTURE_MAPPINGS]
        val mappings = if (encodedMappings == null) {
            defaults.gestureMappings
        } else {
            parseMappings(encodedMappings)
        }
        return AppSettings(
            sensitivity = values[Keys.SENSITIVITY]?.toFloatOrNull() ?: defaults.sensitivity,
            smoothingMs = values[Keys.SMOOTHING]?.toIntOrNull() ?: defaults.smoothingMs,
            deadZonePxPerSecond = values[Keys.DEAD_ZONE]?.toFloatOrNull() ?: defaults.deadZonePxPerSecond,
            handedness = values[Keys.HANDEDNESS]
                ?.let { runCatching { Handedness.valueOf(it) }.getOrNull() }
                ?: defaults.handedness,
            gesturesEnabled = values[Keys.GESTURES_ENABLED]?.toBooleanStrictOrNull()
                ?: defaults.gesturesEnabled,
            gestureConfidence = values[Keys.GESTURE_CONFIDENCE]?.toFloatOrNull()
                ?: defaults.gestureConfidence,
            gestureCooldownMs = values[Keys.GESTURE_COOLDOWN]?.toIntOrNull()
                ?: defaults.gestureCooldownMs,
            gestureMappings = mappings,
            showDiagnostics = values[Keys.SHOW_DIAGNOSTICS]?.toBooleanStrictOrNull()
                ?: defaults.showDiagnostics,
            selectedDeviceAddress = values[Keys.DEVICE_ADDRESS]?.takeIf { it.isNotBlank() },
            selectedDeviceName = values[Keys.DEVICE_NAME]?.takeIf { it.isNotBlank() },
        ).sanitized()
    }

    private fun parseMappings(raw: String?): Map<String, GestureAction> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, GestureAction>()
        for (entry in raw.split('|')) {
            val label = entry.substringBefore('=', "").trim()
            val actionName = entry.substringAfter('=', "").trim()
            if (label !in GestureLabels.VERIFIED) continue
            val action = runCatching { GestureAction.valueOf(actionName) }.getOrNull() ?: continue
            if (action != GestureAction.NONE) result[label] = action
        }
        return result
    }

    private object Keys {
        const val VERSION = "schema_version"
        const val SENSITIVITY = "cursor_sensitivity"
        const val SMOOTHING = "cursor_smoothing_ms"
        const val DEAD_ZONE = "cursor_dead_zone"
        const val HANDEDNESS = "handedness"
        const val GESTURES_ENABLED = "gestures_enabled"
        const val GESTURE_CONFIDENCE = "gesture_confidence"
        const val GESTURE_COOLDOWN = "gesture_cooldown_ms"
        const val GESTURE_MAPPINGS = "gesture_mappings"
        const val SHOW_DIAGNOSTICS = "show_diagnostics"
        const val DEVICE_ADDRESS = "selected_device_address"
        const val DEVICE_NAME = "selected_device_name"
    }
}
