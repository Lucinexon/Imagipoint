package com.imagipoint.watch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imagipoint.watch.session.SessionPhase
import com.imagipoint.watch.session.SessionState
import com.imagipoint.watch.ui.ErrorBanner
import com.imagipoint.watch.ui.IpIcon
import com.imagipoint.watch.ui.IpIconType
import com.imagipoint.watch.ui.MetricRow
import com.imagipoint.watch.ui.ModelIntegrity
import com.imagipoint.watch.ui.ModelIntegrityReport
import com.imagipoint.watch.ui.Panel
import com.imagipoint.watch.ui.ScreenScaffold
import com.imagipoint.watch.ui.SectionLabel
import com.imagipoint.watch.ui.StatusDot
import com.imagipoint.watch.ui.formatDecimal
import com.imagipoint.watch.ui.formatPercent
import com.imagipoint.watch.ui.formatSigned
import com.imagipoint.watch.ui.theme.IpColors
import com.imagipoint.watch.ui.theme.IpTypography

@Composable
fun DiagnosticsScreen(
    session: SessionState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val integrity by produceState<ModelIntegrityReport?>(initialValue = null) {
        value = ModelIntegrity.verify(context)
    }
    val metrics = session.metrics
    val live = session.isRunning || session.phase == SessionPhase.CALIBRATION_FAILED ||
        session.phase == SessionPhase.ERROR

    ScreenScaffold(title = "Diagnostics", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Panel(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    accent = session.phase == SessionPhase.ACTIVE,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(
                            color = when {
                                session.phase == SessionPhase.ACTIVE -> IpColors.Success
                                session.isRunning -> IpColors.Warning
                                else -> IpColors.TextTertiary
                            },
                            pulsing = session.phase == SessionPhase.CONNECTING ||
                                session.phase == SessionPhase.CALIBRATING,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    session.phase == SessionPhase.ACTIVE -> "Live session"
                                    session.isRunning -> "Session preparing"
                                    session.phase == SessionPhase.CALIBRATION_FAILED -> "Calibration needed"
                                    session.phase == SessionPhase.ERROR -> "Session stopped with an error"
                                    else -> "No live session"
                                },
                                style = IpTypography.titleMedium,
                                color = IpColors.TextPrimary,
                                maxLines = 1,
                            )
                            Text(
                                text = session.deviceName ?: "Diagnostics are local to this watch",
                                style = IpTypography.bodySmall,
                                color = IpColors.TextTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            if (metrics.sensorWarning != null) {
                item {
                    ErrorBanner(metrics.sensorWarning.orEmpty(), warning = true)
                }
            }
            if (metrics.gesturesEnabled && !metrics.modelReady && metrics.modelReason != null) {
                item {
                    ErrorBanner(
                        message = "Gesture inference unavailable: ${metrics.modelReason}",
                        warning = true,
                    )
                }
            }

            if (live) {
                item {
                    MetricGroup("Motion") {
                        MetricRow("Sensor rate", "${formatDecimal(metrics.sensorHz, 0)} Hz")
                        MetricRow("Report rate", "${formatDecimal(metrics.reportHz, 0)} Hz")
                        MetricRow(
                            "Yaw rate",
                            "${formatSigned(metrics.yawRateRadPerSec, 2)} rad/s",
                        )
                        MetricRow(
                            "Pitch rate",
                            "${formatSigned(metrics.pitchRateRadPerSec, 2)} rad/s",
                        )
                    }
                }
                item {
                    MetricGroup("HID mouse") {
                        MetricRow("Reports sent", metrics.reportsSent.toString())
                        MetricRow(
                            "Reports failed",
                            metrics.reportsFailed.toString(),
                            valueColor = if (metrics.reportsFailed > 0) IpColors.Warning else IpColors.TextSecondary,
                        )
                        MetricRow("Report format", "4 bytes · ±127")
                    }
                }
                item {
                    MetricGroup("Gesture inference") {
                        MetricRow(
                            "Status",
                            when {
                                !metrics.gesturesEnabled -> "Off"
                                metrics.modelReady -> "Model ready"
                                else -> "Unavailable"
                            },
                            valueColor = when {
                                !metrics.gesturesEnabled -> IpColors.TextTertiary
                                metrics.modelReady -> IpColors.Success
                                else -> IpColors.Warning
                            },
                        )
                        if (metrics.gesturesEnabled) {
                            MetricRow("Accel rate", "${formatDecimal(metrics.accelHz, 0)} Hz")
                            MetricRow("Gyro rate", "${formatDecimal(metrics.gyroHz, 0)} Hz")
                            MetricRow("Inference", "${formatDecimal(metrics.inferenceMs, 1)} ms")
                            MetricRow("Windows", metrics.gestureWindows.toString())
                            MetricRow("Rejected windows", metrics.gestureRejectedWindows.toString())
                            MetricRow(
                                "Top class",
                                metrics.gestureLabel ?: "Waiting for a stable window",
                            )
                            MetricRow("Confidence", formatPercent(metrics.gestureConfidence))
                            MetricRow("Actions fired", metrics.gesturesFired.toString())
                            MetricRow("Last action", metrics.lastAction ?: "None")
                        }
                    }
                }
            }

            item {
                MetricGroup("Bundled model") {
                    MetricRow(
                        "Integrity",
                        when {
                            integrity == null -> "Checking…"
                            integrity?.verified == true -> "Verified"
                            else -> "Failed"
                        },
                        valueColor = when {
                            integrity == null -> IpColors.TextTertiary
                            integrity?.verified == true -> IpColors.Success
                            else -> IpColors.Danger
                        },
                    )
                    MetricRow("Windows", "200 × 6 · 200 Hz")
                    MetricRow("Hop", "50 samples · 250 ms")
                    MetricRow("Input filter", "30–90 Hz bandpass")
                    MetricRow("Normalization", "Embedded in model")
                    MetricRow("Runtime", "TFLite 2.16.1 · 1 thread")
                    val hash = session.metrics.modelSha256 ?: integrity?.sha256
                    if (hash != null) {
                        MetricRow("SHA-256", hash.take(12) + "…")
                    }
                    val reason = integrity?.reason
                    if (reason != null) {
                        Spacer(Modifier.height(4.dp))
                        ErrorBanner(reason, warning = true)
                    }
                }
            }

            item {
                Panel(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        BoxIcon()
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Diagnostics are displayed in memory only. Nothing is logged, recorded, " +
                                "or sent anywhere. Gesture inference never runs on the pointer's sensor path.",
                            style = IpTypography.bodySmall,
                            color = IpColors.TextTertiary,
                            maxLines = 6,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionLabel(title)
        Spacer(Modifier.height(5.dp))
        Panel(
            modifier = Modifier.fillMaxWidth(),
            padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun BoxIcon() {
    Column(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(IpColors.SurfaceRaised),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IpIcon(IpIconType.SHIELD, Modifier.size(13.dp), IpColors.TextSecondary)
    }
}
