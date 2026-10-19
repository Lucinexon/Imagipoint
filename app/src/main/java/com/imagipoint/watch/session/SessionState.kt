package com.imagipoint.watch.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SessionPhase {
    IDLE,
    STARTING,
    CONNECTING,
    CALIBRATING,
    ACTIVE,
    CALIBRATION_FAILED,
    ERROR,
}

enum class FaultKind {
    BLUETOOTH,
    PERMISSION,
    CONNECTION,
    SENSOR,
    HID,
    CALIBRATION,
    INTERNAL,
}

data class SessionFault(
    val kind: FaultKind,
    val message: String,
)

/**
 * Low-frequency diagnostics. The service publishes this at roughly 4 Hz; it never updates
 * the UI for every sensor sample.
 */
data class SessionMetrics(
    val sensorHz: Float = 0f,
    val reportHz: Float = 0f,
    val yawRateRadPerSec: Float = 0f,
    val pitchRateRadPerSec: Float = 0f,
    val reportsSent: Long = 0L,
    val reportsFailed: Long = 0L,
    val sensorWarning: String? = null,
    val gesturesEnabled: Boolean = false,
    val modelReady: Boolean = false,
    val modelReason: String? = null,
    val modelSha256: String? = null,
    val gestureLabel: String? = null,
    val gestureConfidence: Float = 0f,
    val inferenceMs: Float = 0f,
    val accelHz: Float = 0f,
    val gyroHz: Float = 0f,
    val gestureWindows: Long = 0L,
    val gestureRejectedWindows: Long = 0L,
    val gesturesFired: Long = 0L,
    val lastAction: String? = null,
)

data class SessionState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val fault: SessionFault? = null,
    val calibrationProgress: Float = 0f,
    val calibrationSuccess: Boolean = false,
    val metrics: SessionMetrics = SessionMetrics(),
) {
    val isRunning: Boolean
        get() = phase == SessionPhase.STARTING ||
            phase == SessionPhase.CONNECTING ||
            phase == SessionPhase.CALIBRATING ||
            phase == SessionPhase.ACTIVE

    val isActive: Boolean get() = phase == SessionPhase.ACTIVE
}

/**
 * Single source of truth shared by the foreground service and the UI. Held by AppGraph,
 * so it survives Activity recreation while the service is alive.
 */
class SessionRuntime {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun update(transform: (SessionState) -> SessionState) {
        _state.update(transform)
    }

    fun reset() {
        _state.value = SessionState()
    }
}
