package com.imagipoint.watch.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imagipoint.watch.AppGraph
import com.imagipoint.watch.session.SessionPhase
import com.imagipoint.watch.ui.screens.AboutScreen
import com.imagipoint.watch.ui.screens.CalibrationFailureOverlay
import com.imagipoint.watch.ui.screens.CalibrationOverlay
import com.imagipoint.watch.ui.screens.CalibrationScreen
import com.imagipoint.watch.ui.screens.CalibrationSuccessBadge
import com.imagipoint.watch.ui.screens.ConnectionScreen
import com.imagipoint.watch.ui.screens.DiagnosticsScreen
import com.imagipoint.watch.ui.screens.GestureMappingsScreen
import com.imagipoint.watch.ui.screens.HomeScreen
import com.imagipoint.watch.ui.screens.LicensesScreen
import com.imagipoint.watch.ui.screens.PermissionScreen
import com.imagipoint.watch.ui.screens.SettingsScreen
import com.imagipoint.watch.ui.theme.IpColors
import com.imagipoint.watch.service.MouseSessionService

private enum class Screen {
    HOME,
    CONNECTION,
    CALIBRATION,
    SETTINGS,
    GESTURES,
    DIAGNOSTICS,
    ABOUT,
    LICENSES,
}

private const val UI_PREFS = "imagipoint_ui"
private const val PREF_PERMISSION_SKIPPED = "permission_skipped"

@Composable
fun ImagipointApp(
    graph: AppGraph,
    externalPermissionEpoch: Int,
) {
    val context = LocalContext.current
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val hid by graph.hid.state.collectAsStateWithLifecycle()
    val session by graph.session.state.collectAsStateWithLifecycle()
    val prefs = remember(context) { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var diagnosticsParent by rememberSaveable { mutableStateOf(Screen.HOME) }
    var calibrationParent by rememberSaveable { mutableStateOf(Screen.HOME) }
    var localPermissionEpoch by remember { mutableIntStateOf(0) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var gateVisible by rememberSaveable {
        mutableStateOf(
            !hasBluetoothPermission(context) &&
                !prefs.getBoolean(PREF_PERMISSION_SKIPPED, false),
        )
    }

    val permissionEpoch = externalPermissionEpoch + localPermissionEpoch
    val bluetoothPermission = remember(permissionEpoch) {
        hasBluetoothPermission(context)
    }
    val advertisePermission = remember(permissionEpoch) {
        hasAdvertisePermission(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val connectGranted = if (Build.VERSION.SDK_INT >= 31) {
            result[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }
        localPermissionEpoch++
        if (connectGranted) {
            permissionDenied = false
            gateVisible = false
            graph.hid.initialize()
            graph.hid.refreshBondedDevices()
        } else {
            permissionDenied = true
            gateVisible = true
        }
    }

    fun requestPermissions() {
        if (Build.VERSION.SDK_INT < 31) {
            gateVisible = false
            return
        }
        val permissions = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(bluetoothPermission) {
        if (bluetoothPermission) {
            gateVisible = false
            graph.hid.initialize()
            graph.hid.refreshBondedDevices()
        }
    }

    LaunchedEffect(session.phase) {
        if (session.phase == SessionPhase.ERROR) {
            screen = Screen.HOME
        }
    }

    if (gateVisible && !bluetoothPermission) {
        PermissionScreen(
            denied = permissionDenied,
            onAllow = { requestPermissions() },
            onOpenSettings = { openAppSettings(context) },
            onSkip = {
                prefs.edit().putBoolean(PREF_PERMISSION_SKIPPED, true).apply()
                gateVisible = false
            },
        )
        return
    }

    fun startSession() {
        val address = hid.connectedAddress ?: settings.selectedDeviceAddress
        val name = if (hid.isConnected) hid.connectedName else settings.selectedDeviceName
        if (address == null) {
            screen = Screen.CONNECTION
            return
        }
        val intent = Intent(context, MouseSessionService::class.java).apply {
            action = MouseSessionService.ACTION_START
            putExtra(MouseSessionService.EXTRA_ADDRESS, address)
            putExtra(MouseSessionService.EXTRA_NAME, name ?: "Computer")
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun sendSessionAction(action: String) {
        context.startService(Intent(context, MouseSessionService::class.java).setAction(action))
    }

    fun stopSession() {
        sendSessionAction(MouseSessionService.ACTION_STOP)
        screen = Screen.HOME
    }

    fun retrySession() {
        if (session.phase == SessionPhase.CALIBRATION_FAILED) {
            sendSessionAction(MouseSessionService.ACTION_RETRY_CALIBRATION)
        } else {
            startSession()
        }
    }

    fun goBack() {
        screen = when (screen) {
            Screen.HOME -> Screen.HOME
            Screen.CONNECTION -> Screen.HOME
            Screen.CALIBRATION -> calibrationParent
            Screen.SETTINGS -> Screen.HOME
            Screen.GESTURES -> Screen.SETTINGS
            Screen.DIAGNOSTICS -> diagnosticsParent
            Screen.ABOUT -> Screen.SETTINGS
            Screen.LICENSES -> Screen.ABOUT
        }
    }

    fun openDiagnostics(parent: Screen) {
        diagnosticsParent = parent
        screen = Screen.DIAGNOSTICS
    }

    fun openCalibration(parent: Screen) {
        calibrationParent = parent
        screen = Screen.CALIBRATION
    }

    BackHandler(enabled = screen != Screen.HOME) {
        goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IpColors.Black),
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (
                    fadeIn(tween(170)) +
                        slideInHorizontally(tween(170)) { it / 14 }
                    ).togetherWith(
                    fadeOut(tween(100)) +
                        slideOutHorizontally(tween(110)) { -it / 14 },
                )
            },
            label = "ImagiPoint screen",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            when (target) {
                Screen.HOME -> HomeScreen(
                    session = session,
                    settings = settings,
                    hid = hid,
                    bluetoothPermission = bluetoothPermission,
                    onStart = { startSession() },
                    onStop = { stopSession() },
                    onRetry = { retrySession() },
                    onOpenConnection = { screen = Screen.CONNECTION },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenCalibration = { openCalibration(Screen.HOME) },
                    onOpenDiagnostics = { openDiagnostics(Screen.HOME) },
                    onRequestPermission = {
                        permissionDenied = false
                        gateVisible = true
                        requestPermissions()
                    },
                )

                Screen.CONNECTION -> ConnectionScreen(
                    hid = hid,
                    selectedAddress = settings.selectedDeviceAddress,
                    bluetoothPermission = bluetoothPermission,
                    advertisePermission = advertisePermission,
                    onBack = { screen = Screen.HOME },
                    onRefresh = { graph.hid.refreshBondedDevices() },
                    onConnect = { host ->
                        graph.settings.update {
                            it.copy(
                                selectedDeviceAddress = host.address,
                                selectedDeviceName = host.name,
                            )
                        }
                        graph.hid.connect(host.address)
                    },
                    onDisconnect = { graph.hid.disconnect() },
                    onOpenBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    onRequestPermission = { requestPermissions() },
                    onRequestAdvertisePermission = { requestPermissions() },
                )

                Screen.CALIBRATION -> CalibrationScreen(
                    session = session,
                    hasSelectedDevice = settings.selectedDeviceAddress != null || hid.isConnected,
                    onBack = { goBack() },
                    onOpenConnection = { screen = Screen.CONNECTION },
                    onStart = { startSession() },
                    onCalibrate = { sendSessionAction(MouseSessionService.ACTION_CALIBRATE) },
                    onRetry = { retrySession() },
                    onStop = { stopSession() },
                )

                Screen.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onBack = { screen = Screen.HOME },
                    onUpdate = { transform -> graph.settings.update(transform) },
                    onOpenMappings = { screen = Screen.GESTURES },
                    onOpenDiagnostics = { openDiagnostics(Screen.SETTINGS) },
                    onOpenAbout = { screen = Screen.ABOUT },
                    onOpenCalibration = { openCalibration(Screen.SETTINGS) },
                )

                Screen.GESTURES -> GestureMappingsScreen(
                    settings = settings,
                    onBack = { screen = Screen.SETTINGS },
                    onUpdate = { transform -> graph.settings.update(transform) },
                    onOpenSettings = { screen = Screen.SETTINGS },
                )

                Screen.DIAGNOSTICS -> DiagnosticsScreen(
                    session = session,
                    onBack = { screen = diagnosticsParent },
                )

                Screen.ABOUT -> AboutScreen(
                    onBack = { screen = Screen.SETTINGS },
                    onOpenLicenses = { screen = Screen.LICENSES },
                    onOpenDiagnostics = { openDiagnostics(Screen.ABOUT) },
                )

                Screen.LICENSES -> LicensesScreen(
                    onBack = { screen = Screen.ABOUT },
                )
            }
        }

        if (!gateVisible) {
            when (session.phase) {
                SessionPhase.CALIBRATING -> CalibrationOverlay(
                    progress = session.calibrationProgress,
                    onStop = { stopSession() },
                )

                SessionPhase.CALIBRATION_FAILED -> CalibrationFailureOverlay(
                    message = session.fault?.message
                        ?: "The wrist did not settle. Hold still and try again.",
                    onRetry = { retrySession() },
                    onStop = { stopSession() },
                )

                else -> Unit
            }

            if (
                session.calibrationSuccess &&
                (screen == Screen.HOME || screen == Screen.CALIBRATION)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp),
                ) {
                    CalibrationSuccessBadge()
                }
            }
        }
    }
}

private fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED

private fun hasAdvertisePermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        ) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}
