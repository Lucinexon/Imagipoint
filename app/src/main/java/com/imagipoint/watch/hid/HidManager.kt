package com.imagipoint.watch.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Serializable device row for the connection screen. */
data class BondedHost(
    val name: String,
    val address: String,
    val isConnected: Boolean,
)

data class HidState(
    val initialized: Boolean = false,
    val adapterAvailable: Boolean = true,
    val adapterEnabled: Boolean = true,
    val permissionRequired: Boolean = false,
    val registered: Boolean = false,
    val connectingAddress: String? = null,
    val connectedName: String? = null,
    val connectedAddress: String? = null,
    val error: String? = null,
    val bondedDevices: List<BondedHost> = emptyList(),
) {
    val isConnected: Boolean get() = connectedAddress != null
}

/**
 * Owns the platform HID Device profile and emits mouse reports.
 *
 * Design constraints:
 * - Never report "connected" until the platform confirms it.
 * - Coalesce movement on a dedicated worker and throttle to roughly 60 reports/second.
 * - Button releases are force-sent, including during teardown, so no button can stay stuck.
 * - All state updates happen on the main thread; sensor callbacks only enqueue reports.
 */
class HidManager(context: Context) {
    interface Listener {
        fun onConnected(deviceName: String, address: String)
        fun onDisconnected(reason: String?)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _state = MutableStateFlow(
        HidState(adapterAvailable = adapter != null, adapterEnabled = adapter?.isEnabled == true),
    )
    val state: StateFlow<HidState> = _state.asStateFlow()

    private val movementLock = Any()
    private val sendLock = Any()
    private val reportsSent = AtomicLong()
    private val reportsFailed = AtomicLong()

    private var hidDevice: BluetoothHidDevice? = null
    private var profileRequested = false
    private var appRegistered = false
    private var shuttingDown = false
    private var disconnecting = false
    private var connectedDevice: BluetoothDevice? = null
    private var targetDevice: BluetoothDevice? = null
    private var targetAddress: String? = null
    private var sessionListener: Listener? = null

    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null
    private var pendingDx = 0
    private var pendingDy = 0
    private var pendingWheel = 0
    private var pendingButtons = 0
    private var lastSentButtons = 0
    private var lastSendUptime = 0L
    private var drainScheduled = false
    private var suppressReports = false
    private var sendGeneration = 0L
    private val lastReport = ByteArray(MouseReport.REPORT_SIZE).also { bytes ->
        MouseReport.RELEASE_ALL.writeTo(bytes)
    }

    // Permission is checked before the call; runCatching also handles revocation races.
    @SuppressLint("MissingPermission")
    private val connectTimeout = Runnable {
        val address = targetAddress ?: return@Runnable
        if (_state.value.connectingAddress == address) {
            targetAddress = null
            targetDevice = null
            failConnect("The computer did not accept the connection. Keep it awake and try again.")
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { connectedDevice?.let { hidDevice?.disconnect(it) } }
            }
        }
    }

    private val drainRunnable = Runnable { drain(force = false) }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    _state.value = _state.value.copy(adapterEnabled = true, error = null)
                    initialize()
                }
                BluetoothAdapter.STATE_OFF -> {
                    val wasConnected = connectedDevice != null
                    clearConnection()
                    _state.value = _state.value.copy(
                        adapterEnabled = false,
                        permissionRequired = false,
                        error = "Bluetooth is off. Turn it on, then try again.",
                    )
                    if (wasConnected) sessionListener?.onDisconnected("Bluetooth was turned off.")
                }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(bluetoothStateReceiver, filter)
        }
    }

    val totalReportsSent: Long get() = reportsSent.get()
    val totalReportsFailed: Long get() = reportsFailed.get()

    fun setListener(listener: Listener?) {
        sessionListener = listener
    }

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun hasAdvertisePermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_ADVERTISE) ==
            PackageManager.PERMISSION_GRANTED

    /** Idempotent. Must be called again after the user grants Bluetooth permission. */
    @SuppressLint("MissingPermission")
    fun initialize() {
        if (shuttingDown) return
        if (!hasConnectPermission()) {
            _state.value = _state.value.copy(
                permissionRequired = true,
                initialized = false,
                error = "Nearby devices permission is required for Bluetooth mouse control.",
            )
            return
        }
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) {
            _state.value = _state.value.copy(
                adapterAvailable = false,
                error = "This device does not expose Bluetooth HID support.",
            )
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            _state.value = _state.value.copy(
                adapterEnabled = false,
                permissionRequired = false,
                error = "Bluetooth is off. Turn it on, then try again.",
            )
            return
        }
        if (profileRequested) {
            _state.value = _state.value.copy(adapterEnabled = true, permissionRequired = false)
            return
        }
        profileRequested = true
        _state.value = _state.value.copy(
            initialized = false,
            adapterAvailable = true,
            adapterEnabled = true,
            permissionRequired = false,
            error = null,
        )
        try {
            val requested = bluetoothAdapter.getProfileProxy(
                appContext,
                profileListener,
                BluetoothProfile.HID_DEVICE,
            )
            if (!requested) {
                profileRequested = false
                _state.value = _state.value.copy(
                    initialized = true,
                    error = "Bluetooth HID profile is unavailable on this watch.",
                )
            }
        } catch (e: SecurityException) {
            profileRequested = false
            _state.value = _state.value.copy(
                permissionRequired = true,
                error = "Bluetooth permission was denied.",
            )
        } catch (t: Throwable) {
            profileRequested = false
            Log.e(TAG, "Unable to request HID profile", t)
            _state.value = _state.value.copy(error = "Unable to start Bluetooth HID.")
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedDevices() {
        if (!hasConnectPermission()) return
        val bluetoothAdapter = adapter ?: return
        try {
            val connected = connectedDevice?.address
            val rows = bluetoothAdapter.bondedDevices.orEmpty()
                .map { device ->
                    BondedHost(
                        name = safeDeviceName(device),
                        address = device.address,
                        isConnected = device.address == connected,
                    )
                }
                .sortedWith(
                    compareByDescending<BondedHost> { it.isConnected }
                        .thenBy { it.name.lowercase() },
                )
            _state.value = _state.value.copy(bondedDevices = rows)
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(permissionRequired = true)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        initialize()
        targetAddress = address
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            failConnect("Bluetooth is unavailable. Turn it on, then try again.")
            return
        }
        if (!hasConnectPermission()) {
            failConnect("Nearby devices permission is required.", permission = true)
            return
        }
        val device = try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (t: Throwable) {
            failConnect("That computer is no longer available.")
            return
        }
        val isBonded = try {
            bluetoothAdapter.bondedDevices.orEmpty().any { it.address == address }
        } catch (e: SecurityException) {
            false
        }
        if (!isBonded) {
            failConnect("Pair this computer from its Bluetooth settings first.")
            return
        }
        if (connectedDevice?.address == address) {
            _state.value = _state.value.copy(
                connectingAddress = null,
                connectedName = safeDeviceName(device),
                connectedAddress = address,
                error = null,
            )
            sessionListener?.onConnected(safeDeviceName(device), address)
            return
        }
        targetDevice = device
        _state.value = _state.value.copy(
            connectingAddress = address,
            error = null,
        )
        if (!appRegistered || hidDevice == null) {
            // The pending target is connected from onAppStatusChanged once registration completes.
            return
        }
        startPlatformConnect(device)
    }

    @SuppressLint("MissingPermission")
    private fun startPlatformConnect(device: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null) {
            failConnect("Bluetooth HID is not ready yet.")
            return
        }
        try {
            val started = hid.connect(device)
            if (!started) {
                failConnect("The computer refused the HID connection.")
                return
            }
            mainHandler.removeCallbacks(connectTimeout)
            mainHandler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
        } catch (e: SecurityException) {
            failConnect("Bluetooth permission was denied.", permission = true)
        } catch (t: Throwable) {
            Log.e(TAG, "HID connect failed", t)
            failConnect("Unable to connect to the computer.")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        mainHandler.removeCallbacks(connectTimeout)
        disconnecting = true
        releaseAllButtons()
        val device = connectedDevice ?: targetDevice
        clearConnection()
        _state.value = _state.value.copy(
            connectingAddress = null,
            connectedAddress = null,
            connectedName = null,
            error = null,
        )
        if (device != null) {
            try {
                hidDevice?.disconnect(device)
            } catch (_: SecurityException) {
            } catch (t: Throwable) {
                Log.w(TAG, "HID disconnect failed", t)
            }
        }
        sessionListener?.onDisconnected(null)
        refreshBondedDevices()
        disconnecting = false
    }

    private fun failConnect(message: String, permission: Boolean = false) {
        targetDevice = null
        targetAddress = null
        mainHandler.removeCallbacks(connectTimeout)
        _state.value = _state.value.copy(
            connectingAddress = null,
            permissionRequired = permission || _state.value.permissionRequired,
            error = message,
        )
        sessionListener?.onError(message)
    }

    /** Enqueue relative movement. Safe to call from the sensor thread. */
    fun move(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        ensureWorker()
        synchronized(movementLock) {
            pendingDx = (pendingDx + dx).coerceIn(-MAX_PENDING, MAX_PENDING)
            pendingDy = (pendingDy + dy).coerceIn(-MAX_PENDING, MAX_PENDING)
            scheduleDrainLocked()
        }
    }

    fun scroll(amount: Int) {
        if (amount == 0) return
        ensureWorker()
        synchronized(movementLock) {
            pendingWheel = (pendingWheel + amount).coerceIn(-MAX_PENDING, MAX_PENDING)
            scheduleDrainLocked()
        }
    }

    /** Press-and-hold support for future mappings. */
    fun setButton(button: Int, pressed: Boolean) {
        ensureWorker()
        worker?.post {
            synchronized(movementLock) {
                pendingButtons = if (pressed) {
                    pendingButtons or (button and MouseButtons.MASK)
                } else {
                    pendingButtons and (button and MouseButtons.MASK).inv()
                }
            }
            drain(force = true)
        }
    }

    /** A down/up pair with a short, fixed hold time. */
    fun click(button: Int) {
        ensureWorker()
        val handler = worker ?: return
        handler.post {
            synchronized(movementLock) {
                pendingButtons = pendingButtons or (button and MouseButtons.MASK)
            }
            drain(force = true)
        }
        handler.postDelayed({
            synchronized(movementLock) {
                pendingButtons = pendingButtons and (button and MouseButtons.MASK).inv()
            }
            drain(force = true)
        }, CLICK_HOLD_MS)
    }

    /**
     * Synchronously send a release report and suppress pending reports until [resumeReports].
     * Used on stop, sensor failure, disconnect, and teardown.
     */
    fun releaseAllButtons() {
        synchronized(movementLock) {
            sendGeneration++
            suppressReports = true
            pendingDx = 0
            pendingDy = 0
            pendingWheel = 0
            pendingButtons = 0
            lastSentButtons = 0
        }
        val device = connectedDevice
        val hid = hidDevice
        if (device != null && hid != null) {
            synchronized(sendLock) {
                try {
                    MouseReport.RELEASE_ALL.writeTo(lastReport)
                    if (hid.sendReport(device, HidConstants.REPORT_ID, lastReport)) {
                        reportsSent.incrementAndGet()
                    } else {
                        reportsFailed.incrementAndGet()
                    }
                } catch (e: SecurityException) {
                    reportsFailed.incrementAndGet()
                } catch (t: Throwable) {
                    reportsFailed.incrementAndGet()
                    Log.w(TAG, "Unable to release mouse buttons", t)
                }
            }
        }
    }

    fun resumeReports() {
        synchronized(movementLock) {
            suppressReports = false
            lastSentButtons = 0
            sendGeneration++
        }
    }

    fun shutdown() {
        shuttingDown = true
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { appContext.unregisterReceiver(bluetoothStateReceiver) }
        releaseAllButtons()
        try {
            if (appRegistered && ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                hidDevice?.unregisterApp()
            }
        } catch (_: Throwable) {
        }
        appRegistered = false
        hidDevice?.let {
            runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        }
        hidDevice = null
        workerThread?.quitSafely()
        workerThread = null
        worker = null
    }

    private fun clearConnection() {
        clearConnectedDevice()
        targetDevice = null
        targetAddress = null
        mainHandler.removeCallbacks(connectTimeout)
    }

    private fun clearConnectedDevice() {
        connectedDevice = null
        lastSentButtons = 0
    }

    private fun ensureWorker() {
        if (worker != null) return
        synchronized(movementLock) {
            if (worker != null) return
            val thread = HandlerThread("ImagiPoint-hid", android.os.Process.THREAD_PRIORITY_DISPLAY)
            thread.start()
            workerThread = thread
            worker = Handler(thread.looper)
        }
    }

    private fun scheduleDrainLocked(delayMs: Long = 0L) {
        if (drainScheduled) return
        val handler = worker ?: return
        drainScheduled = true
        handler.postDelayed(drainRunnable, delayMs)
    }

    private fun drain(force: Boolean) {
        var dx = 0
        var dy = 0
        var wheel = 0
        var buttons = 0
        var generation = 0L
        var device: BluetoothDevice? = null
        synchronized(movementLock) {
            drainScheduled = false
            val connected = connectedDevice
            if (connected == null || hidDevice == null) {
                pendingDx = 0
                pendingDy = 0
                pendingWheel = 0
                return
            }
            val now = SystemClock.uptimeMillis()
            val elapsed = now - lastSendUptime
            if (!force && elapsed < MIN_REPORT_INTERVAL_MS) {
                scheduleDrainLocked(MIN_REPORT_INTERVAL_MS - elapsed)
                return
            }
            dx = pendingDx.coerceIn(MouseReport.MIN_AXIS, MouseReport.MAX_AXIS)
            dy = pendingDy.coerceIn(MouseReport.MIN_AXIS, MouseReport.MAX_AXIS)
            wheel = pendingWheel.coerceIn(MouseReport.MIN_AXIS, MouseReport.MAX_AXIS)
            pendingDx -= dx
            pendingDy -= dy
            pendingWheel -= wheel
            buttons = pendingButtons
            if (suppressReports) return
            if (dx == 0 && dy == 0 && wheel == 0 && buttons == lastSentButtons) return
            device = connected
            generation = sendGeneration
            lastSendUptime = now
        }
        val target = device ?: return
        sendSnapshot(target, dx, dy, wheel, buttons, generation)
        synchronized(movementLock) {
            val buttonsPending = pendingButtons != lastSentButtons
            val movementPending = pendingDx != 0 || pendingDy != 0 || pendingWheel != 0
            when {
                // Never spin if the platform rejects a button-only report.
                buttonsPending -> scheduleDrainLocked(BUTTON_RETRY_DELAY_MS)
                movementPending -> scheduleDrainLocked()
            }
        }
    }

    private fun sendSnapshot(
        device: BluetoothDevice,
        dx: Int,
        dy: Int,
        wheel: Int,
        buttons: Int,
        generation: Long,
    ) {
        val hid = hidDevice ?: return
        synchronized(sendLock) {
            synchronized(movementLock) {
                if (suppressReports || generation != sendGeneration) return
            }
            try {
                MouseReport.writeReport(
                    target = lastReport,
                    buttons = buttons,
                    dx = dx,
                    dy = dy,
                    wheel = wheel,
                )
                if (hid.sendReport(device, HidConstants.REPORT_ID, lastReport)) {
                    reportsSent.incrementAndGet()
                    synchronized(movementLock) {
                        lastSentButtons = buttons
                    }
                } else {
                    reportsFailed.incrementAndGet()
                }
            } catch (e: SecurityException) {
                reportsFailed.incrementAndGet()
            } catch (t: Throwable) {
                reportsFailed.incrementAndGet()
                Log.w(TAG, "HID report failed", t)
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            mainHandler.post {
                hidDevice = proxy as BluetoothHidDevice
                _state.value = _state.value.copy(initialized = true)
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            mainHandler.post {
                hidDevice = null
                appRegistered = false
                profileRequested = false
                val wasConnected = connectedDevice != null
                val wasConnecting = targetAddress != null
                clearConnection()
                _state.value = _state.value.copy(
                    initialized = false,
                    registered = false,
                    connectingAddress = null,
                    connectedAddress = null,
                    connectedName = null,
                    error = if (shuttingDown) null else "Bluetooth HID service disconnected.",
                )
                if (!shuttingDown) {
                    if (wasConnected) {
                        sessionListener?.onDisconnected("Bluetooth HID service disconnected.")
                    } else if (wasConnecting) {
                        sessionListener?.onError("Bluetooth HID service disconnected.")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val hid = hidDevice ?: return
        if (appRegistered) return
        try {
            val accepted = hid.registerApp(
                HidConstants.SDP_SETTINGS,
                null,
                HidConstants.QOS_SETTINGS,
                appContext.mainExecutor,
                hidCallback,
            )
            if (!accepted) {
                _state.value = _state.value.copy(
                    registered = false,
                    error = "The watch rejected the HID device registration.",
                )
                if (targetAddress != null) {
                    sessionListener?.onError("The watch rejected the HID device registration.")
                }
            }
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(
                permissionRequired = true,
                error = "Bluetooth permission was denied.",
            )
            if (targetAddress != null) {
                sessionListener?.onError("Bluetooth permission was denied.")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "HID registration failed", t)
            _state.value = _state.value.copy(error = "Unable to register ImagiPoint as a mouse.")
            if (targetAddress != null) {
                sessionListener?.onError("Unable to register ImagiPoint as a mouse.")
            }
        }
    }

    private fun markConnected(device: BluetoothDevice) {
        mainHandler.removeCallbacks(connectTimeout)
        val previous = connectedDevice?.address
        connectedDevice = device
        targetDevice = device
        targetAddress = device.address
        val name = safeDeviceName(device)
        _state.value = _state.value.copy(
            registered = true,
            connectingAddress = null,
            connectedName = name,
            connectedAddress = device.address,
            error = null,
        )
        refreshBondedDevices()
        if (previous != device.address) {
            sessionListener?.onConnected(name, device.address)
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String {
        val raw = try {
            device.name
        } catch (_: SecurityException) {
            null
        }
        return raw?.takeIf { it.isNotBlank() } ?: "Computer"
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            mainHandler.post {
                appRegistered = registered
                _state.value = _state.value.copy(
                    registered = registered,
                    initialized = true,
                    error = if (!registered && !shuttingDown) {
                        "The HID registration was rejected. Try again."
                    } else {
                        null
                    },
                )
                if (registered) {
                    refreshBondedDevices()
                    if (pluggedDevice != null) {
                        markConnected(pluggedDevice)
                    } else {
                        val pending = targetDevice
                        if (pending != null && connectedDevice == null) {
                            startPlatformConnect(pending)
                        }
                    }
                } else if (!shuttingDown) {
                    clearConnection()
                    sessionListener?.onDisconnected("The HID registration was lost.")
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            mainHandler.post {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> markConnected(device)
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        val wasConnected = connectedDevice?.address == device.address
                        if (wasConnected) {
                            releaseAllButtons()
                            clearConnectedDevice()
                            _state.value = _state.value.copy(
                                connectingAddress = null,
                                connectedAddress = null,
                                connectedName = null,
                                error = if (disconnecting) null else "Connection to the computer was lost.",
                            )
                            refreshBondedDevices()
                            if (!disconnecting) {
                                sessionListener?.onDisconnected("Connection to the computer was lost.")
                            }
                        } else if (_state.value.connectingAddress == device.address) {
                            _state.value = _state.value.copy(
                                connectingAddress = null,
                                error = "The computer did not accept the connection.",
                            )
                            sessionListener?.onError("The computer did not accept the connection.")
                        }
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            if (id.toInt() != HidConstants.REPORT_ID) return
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            ) return
            val data = synchronized(sendLock) { lastReport.copyOf() }
            try {
                hidDevice?.replyReport(device, type, id, data)
            } catch (_: Throwable) {
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray?) {
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            ) return
            try {
                hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            } catch (_: Throwable) {
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            mainHandler.post {
                if (connectedDevice?.address == device.address) {
                    releaseAllButtons()
                    clearConnectedDevice()
                    _state.value = _state.value.copy(
                        connectedAddress = null,
                        connectedName = null,
                        error = "The computer removed the HID connection.",
                    )
                    sessionListener?.onDisconnected("The computer removed the HID connection.")
                }
            }
        }
    }

    private companion object {
        const val BUTTON_RETRY_DELAY_MS = 30L
        const val TAG = "HidManager"
        const val CONNECT_TIMEOUT_MS = 12_000L
        const val MIN_REPORT_INTERVAL_MS = 16L
        const val CLICK_HOLD_MS = 35L
        const val MAX_PENDING = 1_024
    }
}
