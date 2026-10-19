package com.imagipoint.watch.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imagipoint.watch.hid.BondedHost
import com.imagipoint.watch.hid.HidState
import com.imagipoint.watch.ui.EmptyState
import com.imagipoint.watch.ui.ErrorBanner
import com.imagipoint.watch.ui.IpIcon
import com.imagipoint.watch.ui.IpIconButton
import com.imagipoint.watch.ui.IpIconType
import com.imagipoint.watch.ui.LoadingRing
import com.imagipoint.watch.ui.Panel
import com.imagipoint.watch.ui.PillButton
import com.imagipoint.watch.ui.ScreenScaffold
import com.imagipoint.watch.ui.SectionLabel
import com.imagipoint.watch.ui.StatusDot
import com.imagipoint.watch.ui.TextAction
import com.imagipoint.watch.ui.rememberTapHaptic
import com.imagipoint.watch.ui.theme.IpColors
import com.imagipoint.watch.ui.theme.IpTypography

@Composable
fun ConnectionScreen(
    hid: HidState,
    selectedAddress: String?,
    bluetoothPermission: Boolean,
    advertisePermission: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (BondedHost) -> Unit,
    onDisconnect: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRequestPermission: () -> Unit,
    onRequestAdvertisePermission: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberTapHaptic()
    var discoverableSeconds by rememberSaveable { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        discoverableSeconds = if (result.resultCode > 0) result.resultCode else 0
        if (result.resultCode <= 0) notice = "Discoverable mode was not enabled."
    }

    LaunchedEffect(Unit) { onRefresh() }

    val selectedMissing = !selectedAddress.isNullOrBlank() &&
        hid.bondedDevices.none { it.address == selectedAddress } &&
        !hid.isConnected
    val canUseBluetooth = bluetoothPermission && hid.adapterAvailable

    ScreenScaffold(
        title = "Connection",
        onBack = onBack,
        trailing = {
            IpIconButton(
                type = IpIconType.REFRESH,
                contentDescription = "Refresh paired devices",
                onClick = {
                    notice = null
                    onRefresh()
                },
                size = 44,
                visualSize = 18,
                tint = IpColors.TextSecondary,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 0.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Panel(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = 11.dp, vertical = 9.dp),
                    accent = hid.isConnected,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(
                            color = when {
                                hid.isConnected -> IpColors.Success
                                hid.connectingAddress != null -> IpColors.Warning
                                else -> IpColors.TextTertiary
                            },
                            pulsing = hid.connectingAddress != null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    hid.isConnected -> hid.connectedName ?: "Computer"
                                    hid.connectingAddress != null -> "Connecting…"
                                    else -> "Not connected"
                                },
                                style = IpTypography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    hid.isConnected -> "Bluetooth mouse registered"
                                    hid.connectingAddress != null -> "Waiting for the host"
                                    !canUseBluetooth -> "Bluetooth access required"
                                    else -> "Select a paired computer below"
                                },
                                style = IpTypography.bodySmall,
                                color = IpColors.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (hid.isConnected) {
                            TextAction(
                                text = "Disconnect",
                                onClick = onDisconnect,
                                color = IpColors.Danger,
                            )
                        }
                    }
                }
            }

            if (!bluetoothPermission) {
                item {
                    ErrorBanner(
                        message = "Nearby devices access is off. ImagiPoint cannot create a Bluetooth mouse without it.",
                        actionText = "Allow access",
                        onAction = onRequestPermission,
                    )
                }
            } else if (!hid.adapterAvailable) {
                item {
                    ErrorBanner(message = "This watch does not report a Bluetooth adapter.")
                }
            } else if (!hid.adapterEnabled) {
                item {
                    ErrorBanner(
                        message = "Bluetooth is turned off. Turn it on to connect a computer.",
                        actionText = "Bluetooth settings",
                        onAction = onOpenBluetoothSettings,
                    )
                }
            }

            hid.error?.let { error ->
                item {
                    ErrorBanner(message = error)
                }
            }
            notice?.let { text ->
                item { ErrorBanner(message = text, warning = true) }
            }
            if (selectedMissing) {
                item {
                    ErrorBanner(
                        message = "The previously selected computer is no longer paired. Choose another host.",
                        warning = true,
                    )
                }
            }

            if (canUseBluetooth && hid.initialized && !hid.registered) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingRing(Modifier.size(14.dp), color = IpColors.TextTertiary)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "Preparing the Bluetooth mouse…",
                            style = IpTypography.bodySmall,
                            color = IpColors.TextTertiary,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Paired computers", Modifier.weight(1f))
                    if (hid.bondedDevices.isNotEmpty()) {
                        Text(
                            text = "${hid.bondedDevices.size}",
                            style = IpTypography.labelMedium,
                            color = IpColors.TextTertiary,
                        )
                    }
                }
            }

            if (!canUseBluetooth) {
                item {
                    EmptyState(
                        title = "Bluetooth unavailable",
                        body = "Restore Bluetooth access to see your paired computers.",
                    )
                }
            } else if (hid.bondedDevices.isEmpty()) {
                item {
                    EmptyState(
                        title = "No paired computers",
                        body = "Make the watch discoverable, then pair from your computer.",
                    )
                }
            } else {
                items(hid.bondedDevices, key = { it.address }) { host ->
                    val isConnected = hid.isConnected && host.address == hid.connectedAddress
                    HostRow(
                        host = host,
                        selected = host.address == selectedAddress,
                        connecting = hid.connectingAddress == host.address,
                        connected = isConnected,
                        enabled = hid.connectingAddress == null,
                        onClick = {
                            haptic()
                            notice = when {
                                hid.isConnected && host.address != hid.connectedAddress ->
                                    "Disconnect from the current computer before switching."
                                isConnected -> null
                                else -> null
                            }
                            if (!isConnected && (!hid.isConnected || host.address == hid.connectedAddress)) {
                                onConnect(host)
                            }
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            item {
                Panel(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(12.dp),
                    accent = discoverableSeconds > 0,
                ) {
                    Text(
                        text = "Pair a new computer",
                        style = IpTypography.titleMedium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Open Bluetooth on your computer, choose “Add device”, and select the watch while discoverable.",
                        style = IpTypography.bodySmall,
                        color = IpColors.TextTertiary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    PillButton(
                        text = when {
                            !advertisePermission -> "Allow discoverable mode"
                            discoverableSeconds > 0 -> "Discoverable for ${discoverableSeconds / 60} min"
                            else -> "Make watch discoverable"
                        },
                        onClick = {
                            notice = null
                            if (!advertisePermission) {
                                onRequestAdvertisePermission()
                            } else {
                                runCatching {
                                    discoverableLauncher.launch(
                                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120),
                                    )
                                }.onFailure {
                                    notice = "The system could not open discoverable settings."
                                }
                            }
                        },
                        icon = IpIconType.BLUETOOTH,
                        enabled = canUseBluetooth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (discoverableSeconds > 0) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IpIcon(
                                IpIconType.CHECK,
                                Modifier.size(12.dp),
                                IpColors.Success,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Visible to nearby computers now",
                                style = IpTypography.bodySmall,
                                color = IpColors.Success,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IpIcon(IpIconType.SHIELD, Modifier.size(11.dp), IpColors.TextTertiary)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Pairing uses system Bluetooth only",
                        style = IpTypography.bodySmall,
                        color = IpColors.TextTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HostRow(
    host: BondedHost,
    selected: Boolean,
    connecting: Boolean,
    connected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    if (selected) {
                        listOf(IpColors.SurfaceRaised, IpColors.SurfaceSoft)
                    } else {
                        listOf(IpColors.Surface, IpColors.Graphite)
                    },
                ),
            )
            .border(
                1.dp,
                when {
                    connected -> IpColors.Success.copy(alpha = 0.32f)
                    selected -> IpColors.Hairline
                    else -> IpColors.HairlineSoft
                },
                RoundedCornerShape(18.dp),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(IpColors.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            IpIcon(
                IpIconType.COMPUTER,
                Modifier.size(16.dp),
                if (connected) IpColors.Success else IpColors.TextSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = host.name,
                style = IpTypography.titleMedium,
                color = if (enabled) IpColors.TextPrimary else IpColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = host.address,
                style = IpTypography.bodySmall,
                color = IpColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        when {
            connecting -> LoadingRing(Modifier.size(16.dp), color = IpColors.Warning)
            connected -> IpIcon(IpIconType.CHECK, Modifier.size(15.dp), IpColors.Success)
            selected -> StatusDot(IpColors.Accent)
        }
    }
}
