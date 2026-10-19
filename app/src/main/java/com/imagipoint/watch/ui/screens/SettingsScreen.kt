package com.imagipoint.watch.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imagipoint.watch.gestures.GestureLabels
import com.imagipoint.watch.settings.AppSettings
import com.imagipoint.watch.settings.Handedness
import com.imagipoint.watch.ui.CompactToggle
import com.imagipoint.watch.ui.ConfirmDialog
import com.imagipoint.watch.ui.Hairline
import com.imagipoint.watch.ui.IpIcon
import com.imagipoint.watch.ui.IpIconType
import com.imagipoint.watch.ui.Panel
import com.imagipoint.watch.ui.ScreenScaffold
import com.imagipoint.watch.ui.SectionLabel
import com.imagipoint.watch.ui.SegmentedChoice
import com.imagipoint.watch.ui.SettingRow
import com.imagipoint.watch.ui.SettingSlider
import com.imagipoint.watch.ui.formatDecimal
import com.imagipoint.watch.ui.formatPercent
import com.imagipoint.watch.ui.theme.IpColors
import com.imagipoint.watch.ui.theme.IpTypography
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenMappings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenCalibration: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Settings", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item { SectionLabel("Pointer") }
            item {
                SettingsGroup {
                    SettingSlider(
                        title = "Sensitivity",
                        value = settings.sensitivity,
                        valueRange = AppSettings.SENSITIVITY_MIN..AppSettings.SENSITIVITY_MAX,
                        valueText = "${formatDecimal(settings.sensitivity, 2)}×",
                        onCommit = { value -> onUpdate { it.copy(sensitivity = value) } },
                    )
                    Hairline(Modifier.padding(horizontal = 4.dp))
                    SettingSlider(
                        title = "Smoothing",
                        value = settings.smoothingMs.toFloat(),
                        valueRange = AppSettings.SMOOTHING_MIN_MS.toFloat()..AppSettings.SMOOTHING_MAX_MS.toFloat(),
                        valueText = "${settings.smoothingMs} ms",
                        onCommit = { value -> onUpdate { it.copy(smoothingMs = value.roundToInt()) } },
                    )
                    Hairline(Modifier.padding(horizontal = 4.dp))
                    SettingSlider(
                        title = "Dead zone",
                        value = settings.deadZonePxPerSecond,
                        valueRange = AppSettings.DEAD_ZONE_MIN..AppSettings.DEAD_ZONE_MAX,
                        valueText = "${settings.deadZonePxPerSecond.roundToInt()} px/s",
                        onCommit = { value -> onUpdate { it.copy(deadZonePxPerSecond = value) } },
                    )
                }
            }

            item {
                SettingsGroup {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Handedness",
                                style = IpTypography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (settings.handedness == Handedness.RIGHT) "Right" else "Left",
                                style = IpTypography.bodySmall,
                                color = IpColors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.height(1.dp))
                        Text(
                            "Left flips horizontal movement",
                            style = IpTypography.bodySmall,
                            color = IpColors.TextTertiary,
                        )
                        Spacer(Modifier.height(7.dp))
                        SegmentedChoice(
                            leftLabel = "Right wrist",
                            rightLabel = "Left wrist",
                            leftSelected = settings.handedness == Handedness.RIGHT,
                            onSelectLeft = { right ->
                                onUpdate {
                                    it.copy(
                                        handedness = if (right) Handedness.RIGHT else Handedness.LEFT,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            item {
                SettingRow(
                    title = "Recalibrate neutral",
                    subtitle = "Guided recenter while the session runs",
                    onClick = onOpenCalibration,
                )
            }

            item { SectionLabel("Gestures", Modifier.padding(top = 3.dp)) }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Gesture actions",
                        subtitle = "Experimental · verified classes only",
                        trailing = {
                            CompactToggle(
                                checked = settings.gesturesEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdate { it.copy(gesturesEnabled = enabled) }
                                },
                                contentDescription = "Gesture actions",
                            )
                        },
                    )
                }
            }

            item {
                AnimatedVisibility(visible = settings.gesturesEnabled) {
                    SettingsGroup {
                        SettingSlider(
                            title = "Confidence",
                            value = settings.gestureConfidence,
                            valueRange = AppSettings.CONFIDENCE_MIN..AppSettings.CONFIDENCE_MAX,
                            valueText = formatPercent(settings.gestureConfidence),
                            onCommit = { value -> onUpdate { it.copy(gestureConfidence = value) } },
                        )
                        Hairline(Modifier.padding(horizontal = 4.dp))
                        SettingSlider(
                            title = "Cooldown",
                            value = settings.gestureCooldownMs.toFloat(),
                            valueRange = AppSettings.COOLDOWN_MIN_MS.toFloat()..AppSettings.COOLDOWN_MAX_MS.toFloat(),
                            valueText = "${formatDecimal(settings.gestureCooldownMs / 1000f, 1)} s",
                            onCommit = { value -> onUpdate { it.copy(gestureCooldownMs = value.roundToInt()) } },
                        )
                        Hairline(Modifier.padding(horizontal = 4.dp))
                        SettingRow(
                            title = "Gesture mappings",
                            subtitle = if (settings.gestureMappings.isEmpty()) {
                                "No actions assigned"
                            } else {
                                "${settings.gestureMappings.size} of ${GestureLabels.VERIFIED.size} classes mapped"
                            },
                            onClick = onOpenMappings,
                        )
                    }
                }
            }

            item { SectionLabel("System", Modifier.padding(top = 3.dp)) }
            item {
                SettingsGroup {
                    SettingRow(
                        title = "Diagnostics strip",
                        subtitle = "Show live rates on the home screen",
                        trailing = {
                            CompactToggle(
                                checked = settings.showDiagnostics,
                                onCheckedChange = { enabled ->
                                    onUpdate { it.copy(showDiagnostics = enabled) }
                                },
                                contentDescription = "Diagnostics strip",
                            )
                        },
                    )
                    Hairline(Modifier.padding(horizontal = 4.dp))
                    SettingRow(
                        title = "Diagnostics",
                        subtitle = "Sensors, HID, model, and inference",
                        onClick = onOpenDiagnostics,
                    )
                    Hairline(Modifier.padding(horizontal = 4.dp))
                    SettingRow(
                        title = "About & credits",
                        subtitle = "Privacy, licenses, HGAG-DATA",
                        onClick = onOpenAbout,
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingRow(
                        title = "Reset preferences",
                        subtitle = "Keeps your selected computer",
                        onClick = { confirmReset = true },
                        trailing = {
                            IpIcon(
                                IpIconType.WARNING,
                                Modifier.size(15.dp),
                                IpColors.Danger,
                            )
                        },
                    )
                }
            }

            item {
                Text(
                    text = "Settings stay on this watch. There is no account or cloud copy.",
                    style = IpTypography.bodySmall,
                    color = IpColors.TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset preferences?",
            body = "Pointer, gesture, and diagnostic preferences return to their defaults. You stay paired.",
            confirmLabel = "Reset",
            destructive = true,
            onDismiss = { confirmReset = false },
            onConfirm = {
                onUpdate { current ->
                    AppSettings(
                        selectedDeviceAddress = current.selectedDeviceAddress,
                        selectedDeviceName = current.selectedDeviceName,
                    )
                }
                confirmReset = false
            },
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Panel(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        content()
    }
}
