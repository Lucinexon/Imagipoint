package com.imagipoint.watch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.imagipoint.watch.AppGraph
import com.imagipoint.watch.ImagiPointApplication
import com.imagipoint.watch.MainActivity
import com.imagipoint.watch.R
import com.imagipoint.watch.gestures.GestureEngine
import com.imagipoint.watch.gestures.GesturePrediction
import com.imagipoint.watch.gestures.GestureStatus
import com.imagipoint.watch.gestures.GestureAction
import com.imagipoint.watch.hid.HidManager
import com.imagipoint.watch.hid.MouseButtons
import com.imagipoint.watch.motion.CalibrationTracker
import com.imagipoint.watch.motion.MotionController
import com.imagipoint.watch.motion.MotionSensor
import com.imagipoint.watch.session.FaultKind
import com.imagipoint.watch.session.SessionFault
import com.imagipoint.watch.session.SessionPhase
import com.imagipoint.watch.session.SessionState
import com.imagipoint.watch.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-initiated foreground session.
 *
 * The pointer path is deliberately tiny: sensor callback -> MotionController -> HidManager.
 * Gesture inference lives on its own thread and can fail without affecting the pointer.
 * Everything this service owns is torn down on stop, including the wake lock and HID link.
 */
class MouseSessionService : Service(), HidManager.Listener {
    private lateinit var graph: AppGraph
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var settingsJob: Job? = null
    private var motionSensor: MotionSensor? = null
    private val motionController = MotionController()
    private val calibrationTracker = CalibrationTracker()
    private var gestureEngine: GestureEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopping = false
    private var sensorReliable = true

    private var gestureStatus: GestureStatus? = null
    private var lastPrediction: GesturePrediction? = null
    private var gesturesFired = 0L
    private var lastAction: String? = null
    private var gestureGeneration = 0L
    @Volatile private var latestGestureGeneration = 0L

    @Volatile private var lastSensorSampleNs = 0L
    @Volatile private var sensorIntervalNs = 0.0
    @Volatile private var latestYawRate = 0f
    @Volatile private var latestPitchRate = 0f
    @Volatile private var lastMetricsRequestMs = 0L
    private var lastMetricsPublishMs = 0L
    private var lastReportCount = 0L
    private val metricsScheduled = AtomicBoolean(false)

    private val calibrationTimeout = Runnable {
        if (graph.session.state.value.phase == SessionPhase.CALIBRATING) {
            failCalibration("We could not find a steady starting position. Hold your wrist still and retry.")
        }
    }

    private val connectWatchdog = Runnable {
        val phase = graph.session.state.value.phase
        if (phase == SessionPhase.STARTING || phase == SessionPhase.CONNECTING) {
            failSession(
                FaultKind.CONNECTION,
                "The computer did not accept the connection. Keep it awake and try again.",
            )
        }
    }

    private val clearCalibrationSuccess = Runnable {
        if (graph.session.state.value.phase == SessionPhase.ACTIVE) {
            graph.session.update { it.copy(calibrationSuccess = false) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        graph = (application as ImagiPointApplication).graph
        createNotificationChannel()
        settingsJob = serviceScope.launch {
            graph.settings.settings.collect { settings ->
                handleSettingsChanged(settings)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (graph.session.state.value.isRunning) return START_NOT_STICKY
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                val name = intent.getStringExtra(EXTRA_NAME)
                if (address.isNullOrBlank()) {
                    publishStartFailure("Select a computer before starting.")
                    return START_NOT_STICKY
                }
                if (!startForegroundSafely("Starting session")) {
                    return START_NOT_STICKY
                }
                beginSession(address, name)
            }
            ACTION_STOP -> {
                stopSession()
            }
            ACTION_RETRY_CALIBRATION -> {
                if (graph.session.state.value.phase != SessionPhase.CALIBRATION_FAILED) {
                    stopSelf()
                } else if (startForegroundSafely("Calibrating")) {
                    retryCalibration()
                }
            }
            ACTION_CALIBRATE -> {
                if (!graph.session.state.value.isRunning) {
                    stopSelf()
                } else if (graph.session.state.value.phase == SessionPhase.ACTIVE) {
                    if (startForegroundSafely("Calibrating")) beginCalibration()
                }
            }
            else -> {
                if (!graph.session.state.value.isRunning) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun beginSession(address: String, name: String?) {
        stopping = false
        sensorReliable = true
        acquireWakeLock()
        graph.session.update {
            SessionState(
                phase = SessionPhase.STARTING,
                deviceName = name,
                deviceAddress = address,
            )
        }
        updateNotification("Connecting to ${name ?: "computer"}")
        graph.hid.setListener(this)
        graph.hid.initialize()
        graph.hid.connect(address)
        mainHandler.removeCallbacks(connectWatchdog)
        mainHandler.postDelayed(connectWatchdog, CONNECT_WATCHDOG_MS)
        if (graph.session.state.value.phase == SessionPhase.STARTING) {
            graph.session.update { it.copy(phase = SessionPhase.CONNECTING) }
            updateNotification("Connecting to ${name ?: "computer"}")
        }
    }

    override fun onConnected(deviceName: String, address: String) {
        mainHandler.post {
            if (stopping) return@post
            val phase = graph.session.state.value.phase
            if (phase != SessionPhase.STARTING && phase != SessionPhase.CONNECTING) return@post
            mainHandler.removeCallbacks(connectWatchdog)
            graph.session.update {
                it.copy(
                    phase = SessionPhase.CALIBRATING,
                    deviceName = deviceName,
                    deviceAddress = address,
                    fault = null,
                    calibrationProgress = 0f,
                    calibrationSuccess = false,
                )
            }
            beginCalibration()
        }
    }

    override fun onDisconnected(reason: String?) {
        mainHandler.post {
            if (stopping) return@post
            val phase = graph.session.state.value.phase
            if (phase == SessionPhase.IDLE || phase == SessionPhase.ERROR) return@post
            failSession(
                FaultKind.CONNECTION,
                reason ?: "The connection to the computer ended.",
                disconnectHid = false,
            )
        }
    }

    override fun onError(message: String) {
        mainHandler.post {
            if (stopping) return@post
            val phase = graph.session.state.value.phase
            if (phase == SessionPhase.IDLE || phase == SessionPhase.ERROR) return@post
            failSession(FaultKind.CONNECTION, message, disconnectHid = false)
        }
    }

    private fun beginCalibration() {
        if (stopping) return
        if (graph.hid.state.value.connectedAddress == null) {
            failSession(FaultKind.CONNECTION, "The computer is no longer connected.")
            return
        }
        acquireWakeLock()
        stopGestureEngine()
        graph.hid.releaseAllButtons()
        graph.hid.resumeReports()
        motionController.reset()
        motionController.setPaused(false)
        motionController.setCalibrating(true)
        calibrationTracker.reset()
        sensorReliable = true
        graph.session.update {
            it.copy(
                phase = SessionPhase.CALIBRATING,
                calibrationProgress = 0f,
                calibrationSuccess = false,
                fault = null,
                metrics = it.metrics.copy(sensorWarning = null),
            )
        }
        updateNotification("Hold your wrist steady")
        if (!startMotionSensor()) return
        mainHandler.removeCallbacks(calibrationTimeout)
        mainHandler.postDelayed(calibrationTimeout, CALIBRATION_TIMEOUT_MS)
    }

    private fun startMotionSensor(): Boolean {
        if (motionSensor != null) return true
        val sensor = MotionSensor(this, motionListener)
        if (!sensor.start()) {
            motionSensor = null
            failSession(FaultKind.SENSOR, "The wrist sensor could not be started.")
            return false
        }
        motionSensor = sensor
        return true
    }

    private fun stopMotionSensor() {
        motionSensor?.stop()
        motionSensor = null
        lastSensorSampleNs = 0L
        sensorIntervalNs = 0.0
    }

    private val motionListener = object : MotionSensor.Listener {
        override fun onOrientationSample(quaternion: FloatArray, timestampNanos: Long) {
            if (stopping) return
            val state = graph.session.state.value
            if (state.phase != SessionPhase.ACTIVE && state.phase != SessionPhase.CALIBRATING) return

            val settings = graph.settings.current
            if (lastSensorSampleNs != 0L) {
                val interval = timestampNanos - lastSensorSampleNs
                if (interval in 1..500_000_000L) {
                    sensorIntervalNs = if (sensorIntervalNs == 0.0) {
                        interval.toDouble()
                    } else {
                        sensorIntervalNs * 0.9 + interval * 0.1
                    }
                }
            }
            lastSensorSampleNs = timestampNanos

            val frame = motionController.process(quaternion, timestampNanos, settings)
            latestYawRate = frame.output.yawRateRadPerSec
            latestPitchRate = frame.output.pitchRateRadPerSec

            if (state.phase == SessionPhase.CALIBRATING) {
                if (frame.valid) {
                    when (calibrationTracker.add(frame.headingRad, frame.pitchRad, timestampNanos)) {
                        CalibrationTracker.Result.STABLE -> mainHandler.post { completeCalibration() }
                        CalibrationTracker.Result.TIMED_OUT -> mainHandler.post {
                            failCalibration(
                                "We could not find a steady starting position. Hold your wrist still and retry.",
                            )
                        }
                        CalibrationTracker.Result.COLLECTING -> {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastCalibrationUiMs >= CALIBRATION_UI_INTERVAL_MS) {
                                lastCalibrationUiMs = now
                                val progress = calibrationTracker.progress
                                mainHandler.post {
                                    if (graph.session.state.value.phase == SessionPhase.CALIBRATING) {
                                        graph.session.update { it.copy(calibrationProgress = progress) }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (sensorReliable && frame.output.hasMovement) {
                graph.hid.move(frame.output.dx, frame.output.dy)
            }
            maybePublishMetrics()
        }

        override fun onSensorReliabilityChanged(reliable: Boolean) {
            mainHandler.post { handleSensorReliability(reliable) }
        }

        override fun onSensorError(message: String) {
            mainHandler.post {
                if (stopping) return@post
                failSession(FaultKind.SENSOR, message)
            }
        }
    }

    private var lastCalibrationUiMs = 0L

    private fun completeCalibration() {
        if (stopping) return
        if (graph.session.state.value.phase != SessionPhase.CALIBRATING) return
        mainHandler.removeCallbacks(calibrationTimeout)
        motionController.setCalibrating(false)
        motionController.recenter()
        motionController.setPaused(false)
        graph.hid.resumeReports()
        graph.session.update {
            it.copy(
                phase = SessionPhase.ACTIVE,
                calibrationProgress = 1f,
                calibrationSuccess = true,
                fault = null,
                metrics = it.metrics.copy(sensorWarning = null),
            )
        }
        updateNotification("Mouse active")
        startGestureEngineIfEnabled()
        publishMetrics()
        mainHandler.removeCallbacks(clearCalibrationSuccess)
        mainHandler.postDelayed(clearCalibrationSuccess, CALIBRATION_SUCCESS_MS)
    }

    private fun failCalibration(message: String) {
        if (stopping) return
        if (graph.session.state.value.phase != SessionPhase.CALIBRATING) return
        mainHandler.removeCallbacks(calibrationTimeout)
        stopGestureEngine()
        stopMotionSensor()
        releaseWakeLock()
        motionController.setCalibrating(false)
        motionController.setPaused(true)
        graph.hid.releaseAllButtons()
        graph.session.update {
            it.copy(
                phase = SessionPhase.CALIBRATION_FAILED,
                calibrationProgress = 0f,
                calibrationSuccess = false,
                fault = SessionFault(FaultKind.CALIBRATION, message),
                metrics = it.metrics.copy(
                    sensorHz = 0f,
                    reportHz = 0f,
                    yawRateRadPerSec = 0f,
                    pitchRateRadPerSec = 0f,
                    sensorWarning = null,
                ),
            )
        }
        updateNotification("Calibration needed")
        publishMetrics()
    }

    private fun retryCalibration() {
        if (stopping) return
        if (graph.session.state.value.phase != SessionPhase.CALIBRATION_FAILED) return
        if (graph.hid.state.value.connectedAddress == null) {
            failSession(FaultKind.CONNECTION, "The computer is no longer connected.")
            return
        }
        beginCalibration()
    }

    private fun handleSensorReliability(reliable: Boolean) {
        if (stopping) return
        val phase = graph.session.state.value.phase
        if (phase != SessionPhase.ACTIVE && phase != SessionPhase.CALIBRATING) return
        if (reliable == sensorReliable) return
        sensorReliable = reliable
        if (!reliable) {
            if (phase == SessionPhase.CALIBRATING) {
                failCalibration("Wrist sensor accuracy is low. Hold still and retry in a moment.")
                return
            }
            stopGestureEngine()
            graph.hid.releaseAllButtons()
            motionController.setPaused(true)
            graph.session.update {
                it.copy(
                    metrics = it.metrics.copy(
                        sensorWarning = "Sensor accuracy is low. Keep the watch still, then move again.",
                    ),
                )
            }
            updateNotification("Paused — sensor accuracy low")
            publishMetrics()
        } else {
            if (phase != SessionPhase.ACTIVE) return
            motionController.setPaused(false)
            motionController.recenter()
            graph.hid.resumeReports()
            graph.session.update {
                it.copy(metrics = it.metrics.copy(sensorWarning = null))
            }
            updateNotification("Mouse active")
            startGestureEngineIfEnabled()
            publishMetrics()
        }
    }

    private fun startGestureEngineIfEnabled() {
        val settings = graph.settings.current
        if (!settings.gesturesEnabled) return
        if (gestureEngine != null) return
        if (graph.session.state.value.phase != SessionPhase.ACTIVE) return
        val generation = ++gestureGeneration
        latestGestureGeneration = generation
        gestureStatus = null
        lastPrediction = null
        gestureEngine = GestureEngine(
            context = this,
            settingsProvider = { graph.settings.current },
            callbacks = object : GestureEngine.Callbacks {
                override fun onStatus(status: GestureStatus) {
                    mainHandler.post {
                        if (generation != latestGestureGeneration) return@post
                        gestureStatus = status
                        maybePublishMetrics()
                    }
                }

                override fun onPrediction(prediction: GesturePrediction) {
                    mainHandler.post {
                        if (generation != latestGestureGeneration) return@post
                        lastPrediction = prediction
                        maybePublishMetrics()
                    }
                }

                override fun onGesture(label: String, confidence: Float) {
                    mainHandler.post {
                        if (generation != latestGestureGeneration) return@post
                        performGesture(label, confidence)
                    }
                }
            },
        )
        publishMetrics()
    }

    private fun stopGestureEngine() {
        latestGestureGeneration = ++gestureGeneration
        gestureEngine?.stop()
        gestureEngine = null
        gestureStatus = null
        lastPrediction = null
    }

    private fun performGesture(label: String, confidence: Float) {
        if (stopping) return
        if (graph.session.state.value.phase != SessionPhase.ACTIVE) return
        if (!sensorReliable) return
        val settings = graph.settings.current
        if (!settings.gesturesEnabled) return
        val action = settings.gestureMappings[label] ?: GestureAction.NONE
        if (action == GestureAction.NONE) return
        when (action) {
            GestureAction.LEFT_CLICK -> graph.hid.click(MouseButtons.LEFT)
            GestureAction.RIGHT_CLICK -> graph.hid.click(MouseButtons.RIGHT)
            GestureAction.MIDDLE_CLICK -> graph.hid.click(MouseButtons.MIDDLE)
            GestureAction.SCROLL_UP -> graph.hid.scroll(1)
            GestureAction.SCROLL_DOWN -> graph.hid.scroll(-1)
            GestureAction.NONE -> return
        }
        gesturesFired++
        lastAction = "$label — ${action.displayName}"
        maybePublishMetrics()
    }

    private fun handleSettingsChanged(settings: AppSettings) {
        if (graph.session.state.value.phase == SessionPhase.ACTIVE) {
            if (settings.gesturesEnabled && sensorReliable) {
                startGestureEngineIfEnabled()
            } else if (!settings.gesturesEnabled) {
                stopGestureEngine()
            }
            maybePublishMetrics()
        }
    }

    private fun maybePublishMetrics() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastMetricsRequestMs < METRICS_INTERVAL_MS) return
        lastMetricsRequestMs = now
        if (!metricsScheduled.compareAndSet(false, true)) return
        mainHandler.post {
            metricsScheduled.set(false)
            publishMetrics()
        }
    }

    private fun publishMetrics() {
        val state = graph.session.state.value
        if (state.phase != SessionPhase.ACTIVE &&
            state.phase != SessionPhase.CALIBRATING &&
            state.phase != SessionPhase.CALIBRATION_FAILED
        ) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        val reportDelta = graph.hid.totalReportsSent - lastReportCount
        val reportWindow = (now - lastMetricsPublishMs).coerceAtLeast(1L)
        val reportHz = if (lastMetricsPublishMs == 0L) 0f else {
            reportDelta * 1000f / reportWindow
        }
        lastReportCount = graph.hid.totalReportsSent
        lastMetricsPublishMs = now
        val sensorFresh = lastSensorSampleNs != 0L &&
            (SystemClock.elapsedRealtimeNanos() - lastSensorSampleNs) < 1_000_000_000L
        val sensorHz = if (sensorFresh && sensorIntervalNs > 0.0) {
            (1_000_000_000.0 / sensorIntervalNs).toFloat()
        } else {
            0f
        }
        val status = gestureStatus
        val prediction = lastPrediction
        val settings = graph.settings.current
        graph.session.update { current ->
            if (current.phase != state.phase) current else {
                current.copy(
                    metrics = current.metrics.copy(
                        sensorHz = sensorHz,
                        reportHz = reportHz,
                        yawRateRadPerSec = latestYawRate,
                        pitchRateRadPerSec = latestPitchRate,
                        reportsSent = graph.hid.totalReportsSent,
                        reportsFailed = graph.hid.totalReportsFailed,
                        gesturesEnabled = settings.gesturesEnabled,
                        modelReady = status?.modelReady == true,
                        modelReason = status?.unavailableReason,
                        modelSha256 = status?.sha256,
                        gestureLabel = prediction?.label,
                        gestureConfidence = prediction?.confidence ?: 0f,
                        inferenceMs = prediction?.inferenceMs ?: 0f,
                        accelHz = prediction?.accelRateHz ?: 0f,
                        gyroHz = prediction?.gyroRateHz ?: 0f,
                        gestureWindows = prediction?.windowsRun ?: 0L,
                        gestureRejectedWindows = prediction?.rejectedWindows ?: 0L,
                        gesturesFired = gesturesFired,
                        lastAction = lastAction,
                    ),
                )
            }
        }
    }

    private fun stopSession() {
        if (stopping) return
        stopping = true
        mainHandler.removeCallbacksAndMessages(null)
        stopGestureEngine()
        stopMotionSensor()
        graph.hid.releaseAllButtons()
        graph.hid.setListener(null)
        graph.hid.disconnect()
        releaseWakeLock()
        graph.session.reset()
        stopForegroundCompat()
        stopSelf()
    }

    private fun failSession(
        kind: FaultKind,
        message: String,
        disconnectHid: Boolean = true,
    ) {
        if (stopping) return
        stopping = true
        mainHandler.removeCallbacksAndMessages(null)
        stopGestureEngine()
        stopMotionSensor()
        graph.hid.releaseAllButtons()
        graph.hid.setListener(null)
        if (disconnectHid) {
            runCatching { graph.hid.disconnect() }
        }
        releaseWakeLock()
        graph.session.update { current ->
            current.copy(
                phase = SessionPhase.ERROR,
                fault = SessionFault(kind, message),
                calibrationSuccess = false,
                calibrationProgress = 0f,
                metrics = current.metrics.copy(
                    sensorHz = 0f,
                    reportHz = 0f,
                    yawRateRadPerSec = 0f,
                    pitchRateRadPerSec = 0f,
                ),
            )
        }
        stopForegroundCompat()
        stopSelf()
    }

    private fun publishStartFailure(message: String) {
        graph.session.update {
            it.copy(
                phase = SessionPhase.ERROR,
                fault = SessionFault(FaultKind.INTERNAL, message),
            )
        }
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        settingsJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        if (!stopping) {
            stopGestureEngine()
            stopMotionSensor()
            graph.hid.releaseAllButtons()
            graph.hid.setListener(null)
            runCatching { graph.hid.disconnect() }
            releaseWakeLock()
            val phase = graph.session.state.value.phase
            if (phase != SessionPhase.ERROR) graph.session.reset()
            stopping = true
        }
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            runCatching { acquire(WAKE_LOCK_TIMEOUT_MS) }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            runCatching { if (lock.isHeld) lock.release() }
        }
        wakeLock = null
    }

    private fun startForegroundSafely(text: String): Boolean {
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(text),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
            true
        } catch (e: SecurityException) {
            publishStartFailure("The mouse session could not start. Check Bluetooth permission and try again.")
            false
        } catch (t: Throwable) {
            publishStartFailure("The mouse session could not start.")
            false
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(text)) }
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MouseSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stop),
            "Stop",
            stopIntent,
        ).build()
        val builder = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ImagiPoint")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(stopAction)
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Mouse session",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows while ImagiPoint is controlling a computer."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.imagipoint.watch.action.START"
        const val ACTION_STOP = "com.imagipoint.watch.action.STOP"
        const val ACTION_RETRY_CALIBRATION = "com.imagipoint.watch.action.RETRY_CALIBRATION"
        const val ACTION_CALIBRATE = "com.imagipoint.watch.action.CALIBRATE"
        const val EXTRA_ADDRESS = "device_address"
        const val EXTRA_NAME = "device_name"

        private const val NOTIFICATION_CHANNEL_ID = "mouse_session"
        private const val NOTIFICATION_ID = 41
        private const val WAKE_LOCK_TAG = "ImagiPoint:mouse-session"
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L
        private const val CONNECT_WATCHDOG_MS = 15_000L
        private const val CALIBRATION_TIMEOUT_MS = 6_000L
        private const val CALIBRATION_SUCCESS_MS = 1_200L
        private const val CALIBRATION_UI_INTERVAL_MS = 200L
        // Diagnostics/dispatch cadence: 1 Hz keeps SessionState churn off the sensor path.
        private const val METRICS_INTERVAL_MS = 1_000L
    }
}
