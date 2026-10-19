package com.imagipoint.watch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imagipoint.watch.hid.HidState
import com.imagipoint.watch.session.SessionPhase
import com.imagipoint.watch.session.SessionState
import com.imagipoint.watch.settings.AppSettings
import com.imagipoint.watch.ui.BrandWordmark
import com.imagipoint.watch.ui.InstrumentDial
import com.imagipoint.watch.ui.IpIconButton
import com.imagipoint.watch.ui.IpIconType
import com.imagipoint.watch.ui.StatusDot
import com.imagipoint.watch.ui.formatDecimal
import com.imagipoint.watch.ui.theme.IpColors
import com.imagipoint.watch.ui.theme.IpTypography

@Composable
fun HomeScreen(
    session: SessionState,
    settings: AppSettings,
    hid: HidState,
    bluetoothPermission: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val phase = session.phase
    val connectedName = session.deviceName ?: hid.connectedName
    val selectedName = settings.selectedDeviceName
    val hasDevice = !settings.selectedDeviceAddress.isNullOrBlank()
    val busy = phase == SessionPhase.STARTING || phase == SessionPhase.CONNECTING ||
        phase == SessionPhase.CALIBRATING
    val active = phase == SessionPhase.ACTIVE
    val failed = phase == SessionPhase.ERROR || phase == SessionPhase.CALIBRATION_FAILED
    val showStrip = settings.showDiagnostics && (active || busy)

    val statusColor = when {
        active -> IpColors.Success
        busy -> IpColors.Warning
        failed -> IpColors.Danger
        !bluetoothPermission || !hid.adapterEnabled -> IpColors.Warning
        else -> IpColors.TextTertiary
    }
    val statusText = when {
        !bluetoothPermission -> "Bluetooth access is needed. Tap the dial to allow it."
        active && session.calibrationSuccess -> "Neutral position set. Move naturally."
        active -> "Ready for movement."
        phase == SessionPhase.STARTING -> "Starting session…"
        phase == SessionPhase.CONNECTING -> "Connecting to ${connectedName ?: selectedName ?: "computer"}…"
        phase == SessionPhase.CALIBRATING -> "Hold your wrist steady."
        phase == SessionPhase.CALIBRATION_FAILED -> "Calibration needs another try."
        phase == SessionPhase.ERROR -> session.fault?.message ?: "The session ended unexpectedly."
        !hid.adapterAvailable -> "This watch has no Bluetooth adapter."
        !hid.adapterEnabled -> "Bluetooth is off. Open connection settings."
        !hasDevice -> "No computer selected"
        else -> "Aim at your screen and start."
    }
    val actionLabel = when {
        !bluetoothPermission -> "Allow"
        active -> "Stop"
        busy -> "Wait"
        failed -> "Retry"
        !hasDevice -> "Set up"
        else -> "Start"
    }
    val caption = when {
        !bluetoothPermission -> "Access"
        phase == SessionPhase.STARTING -> "Starting"
        phase == SessionPhase.CONNECTING -> "Linking"
        phase == SessionPhase.CALIBRATING -> "Hold"
        showStrip -> "${formatDecimal(session.metrics.sensorHz, 0)}Hz  " +
            "${formatDecimal(session.metrics.reportHz, 0)}/s"
        active -> "Live"
        failed -> "Check"
        !hid.adapterAvailable -> "No radio"
        !hid.adapterEnabled -> "Radio off"
        !hasDevice -> "No host"
        else -> "Ready"
    }
    val canPress = !busy && hid.adapterAvailable
    val statusClickable = showStrip

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IpColors.Black)
            .padding(start = 20.dp, end = 20.dp, top = 7.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BrandWordmark()
            Spacer(Modifier.width(7.dp))
            StatusDot(statusColor, pulsing = busy)
        }

        Spacer(Modifier.weight(0.55f))

        InstrumentDial(
            actionLabel = actionLabel,
            icon = when {
                active -> IpIconType.STOP
                failed -> IpIconType.REFRESH
                !hasDevice -> IpIconType.COMPUTER
                else -> IpIconType.PLAY
            },
            onClick = {
                when {
                    !bluetoothPermission -> onRequestPermission()
                    !hid.adapterAvailable || !hid.adapterEnabled -> onOpenConnection()
                    active -> onStop()
                    failed -> onRetry()
                    !hasDevice -> onOpenConnection()
                    else -> onStart()
                }
            },
            enabled = canPress,
            active = active,
            failed = failed,
            loading = busy,
            caption = caption,
            diameter = 104.dp,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = statusText,
            style = IpTypography.bodyMedium.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = when {
                failed -> IpColors.Danger
                !bluetoothPermission || !hid.adapterEnabled -> IpColors.Warning
                else -> IpColors.TextSecondary
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (statusClickable) {
                        Modifier.clickable(role = Role.Button, onClick = onOpenDiagnostics)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 10.dp, vertical = 1.dp),
        )

        Spacer(Modifier.weight(0.45f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IpIconButton(
                type = IpIconType.SETTINGS,
                contentDescription = "Settings",
                onClick = onOpenSettings,
                size = 44,
                visualSize = 19,
                tint = IpColors.TextSecondary,
                bordered = true,
            )
            Spacer(Modifier.width(18.dp))
            IpIconButton(
                type = IpIconType.TARGET,
                contentDescription = "Calibration",
                onClick = onOpenCalibration,
                size = 44,
                visualSize = 19,
                tint = if (active) IpColors.Accent else IpColors.TextSecondary,
                bordered = true,
            )
        }
    }
}
