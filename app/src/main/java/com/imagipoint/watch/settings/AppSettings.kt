package com.imagipoint.watch.settings

import com.imagipoint.watch.gestures.GestureAction
import com.imagipoint.watch.gestures.GestureLabels

/** Which wrist / orientation convention the user prefers for horizontal movement. */
enum class Handedness {
    RIGHT,
    LEFT,
}

/**
 * All user-visible preferences. Defaults are deliberately conservative.
 *
 * Gesture recognition is opt-in: it is experimental on watch hardware and it keeps the
 * accelerometer/gyroscope active, so it must never be silently enabled.
 */
data class AppSettings(
    val sensitivity: Float = 1.0f,
    val smoothingMs: Int = 35,
    val deadZonePxPerSecond: Float = 5f,
    val handedness: Handedness = Handedness.RIGHT,
    val gesturesEnabled: Boolean = false,
    val gestureConfidence: Float = 0.85f,
    val gestureCooldownMs: Int = 2000,
    val gestureMappings: Map<String, GestureAction> = mapOf(
        "Index Thumb Tap" to GestureAction.LEFT_CLICK,
    ),
    val showDiagnostics: Boolean = false,
    val selectedDeviceAddress: String? = null,
    val selectedDeviceName: String? = null,
) {
    fun sanitized(): AppSettings = copy(
        sensitivity = sensitivity.finiteOr(1f).coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX),
        smoothingMs = smoothingMs.coerceIn(SMOOTHING_MIN_MS, SMOOTHING_MAX_MS),
        deadZonePxPerSecond = deadZonePxPerSecond.finiteOr(5f)
            .coerceIn(DEAD_ZONE_MIN, DEAD_ZONE_MAX),
        gestureConfidence = gestureConfidence.finiteOr(0.85f)
            .coerceIn(CONFIDENCE_MIN, CONFIDENCE_MAX),
        gestureCooldownMs = gestureCooldownMs.coerceIn(COOLDOWN_MIN_MS, COOLDOWN_MAX_MS),
        gestureMappings = gestureMappings
            .filterKeys { it in GestureLabels.VERIFIED }
            .mapValues { (_, action) -> action }
            .filterValues { it != GestureAction.NONE },
        selectedDeviceAddress = selectedDeviceAddress?.takeIf { it.isNotBlank() },
        selectedDeviceName = selectedDeviceName?.takeIf { it.isNotBlank() },
    )

    private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

    companion object {
        const val SENSITIVITY_MIN = 0.35f
        const val SENSITIVITY_MAX = 2.5f
        const val SMOOTHING_MIN_MS = 0
        const val SMOOTHING_MAX_MS = 150
        const val DEAD_ZONE_MIN = 0f
        const val DEAD_ZONE_MAX = 30f
        const val CONFIDENCE_MIN = 0.50f
        const val CONFIDENCE_MAX = 0.99f
        const val COOLDOWN_MIN_MS = 500
        const val COOLDOWN_MAX_MS = 5000
    }
}
