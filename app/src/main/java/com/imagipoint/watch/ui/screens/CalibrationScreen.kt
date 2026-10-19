package com.imagipoint.watch.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imagipoint.watch.session.SessionPhase
import com.imagipoint.watch.session.SessionState
import com.imagipoint.watch.ui.IpIcon
import com.imagipoint.watch.ui.IpIconType
import com.imagipoint.watch.ui.Panel
import com.imagipoint.watch.ui.PillButton
import com.imagipoint.watch.ui.ScreenScaffold
import com.imagipoint.watch.ui.TextAction
import com.imagipoint.watch.ui.theme.IpColors
import com.imagipoint.watch.ui.theme.IpTypography
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CalibrationScreen(
    session: SessionState,
    hasSelectedDevice: Boolean,
    onBack: () -> Unit,
    onOpenConnection: () -> Unit,
    onStart: () -> Unit,
    onCalibrate: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
) {
    ScreenScaffold(title = "Calibration", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 0.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CalibrationGauge(
                        progress = if (session.phase == SessionPhase.CALIBRATING) {
                            session.calibrationProgress
                        } else {
                            null
                        },
                        active = session.phase == SessionPhase.ACTIVE,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "REFERENCE",
                            style = IpTypography.labelSmall,
                            color = IpColors.TextTertiary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when (session.phase) {
                                SessionPhase.ACTIVE -> "Reference captured"
                                SessionPhase.CALIBRATING -> "Hold your wrist steady"
                                SessionPhase.CALIBRATION_FAILED -> "Let's try that again"
                                SessionPhase.CONNECTING, SessionPhase.STARTING -> "Preparing session"
                                else -> "Neutral posture"
                            },
                            style = IpTypography.headlineMedium,
                            color = IpColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when (session.phase) {
                                SessionPhase.ACTIVE -> "Move naturally. Recenter when needed."
                                SessionPhase.CALIBRATING -> "Keep the watch still while the arc fills."
                                SessionPhase.CALIBRATION_FAILED -> "Too much movement to capture."
                                SessionPhase.CONNECTING, SessionPhase.STARTING -> "The mouse link is being prepared."
                                else -> "A calm, repeatable position."
                            },
                            style = IpTypography.bodySmall,
                            color = IpColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (session.phase) {
                        SessionPhase.ACTIVE -> {
                            PillButton(
                                text = "Recenter now",
                                onClick = onCalibrate,
                                modifier = Modifier.fillMaxWidth(),
                                primary = true,
                            )
                        }
                        SessionPhase.CALIBRATION_FAILED -> {
                            PillButton(
                                text = "Retry calibration",
                                onClick = onRetry,
                                modifier = Modifier.fillMaxWidth(),
                                primary = true,
                            )
                            TextAction(
                                text = "Stop session",
                                onClick = onStop,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                color = IpColors.Danger,
                            )
                        }
                        SessionPhase.CALIBRATING, SessionPhase.CONNECTING, SessionPhase.STARTING -> {
                            PillButton(
                                text = "Preparing…",
                                onClick = {},
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                            )
                        }
                        SessionPhase.ERROR -> {
                            PillButton(
                                text = "Back to home",
                                onClick = onBack,
                                modifier = Modifier.fillMaxWidth(),
                                primary = true,
                            )
                        }
                        else -> {
                            PillButton(
                                text = if (hasSelectedDevice) "Start and calibrate" else "Choose a computer",
                                onClick = if (hasSelectedDevice) onStart else onOpenConnection,
                                modifier = Modifier.fillMaxWidth(),
                                primary = true,
                            )
                        }
                    }
                }
            }

            item {
                CalibrationSteps()
            }

            item {
                Text(
                    text = "The reference resets only during an explicit calibration or when you start " +
                        "a session. ImagiPoint never recenters silently while the cursor is moving.",
                    style = IpTypography.bodySmall,
                    color = IpColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CalibrationGauge(progress: Float?, active: Boolean) {
    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            for (index in 0 until 36) {
                val angle = Math.toRadians(index * 10.0 - 90.0)
                val major = index % 3 == 0
                val length = if (major) 3.5.dp.toPx() else 2.dp.toPx()
                drawLine(
                    color = if (major) IpColors.Hairline else IpColors.HairlineSoft,
                    start = Offset(
                        center.x + cos(angle).toFloat() * (radius - 1.dp.toPx()),
                        center.y + sin(angle).toFloat() * (radius - 1.dp.toPx()),
                    ),
                    end = Offset(
                        center.x + cos(angle).toFloat() * (radius - 1.dp.toPx() - length),
                        center.y + sin(angle).toFloat() * (radius - 1.dp.toPx() - length),
                    ),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val arcRadius = radius - 8.dp.toPx()
            val topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
            val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
            drawArc(
                color = IpColors.HairlineSoft,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(1.dp.toPx()),
            )
            when {
                progress != null -> drawArc(
                    color = IpColors.Accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
                )
                active -> drawArc(
                    color = IpColors.Accent.copy(alpha = 0.8f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
                else -> drawArc(
                    color = IpColors.Accent.copy(alpha = 0.55f),
                    startAngle = -90f,
                    sweepAngle = 36f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(IpColors.SurfaceRaised, IpColors.Graphite)))
                .border(1.dp, IpColors.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IpIcon(
                type = IpIconType.TARGET,
                modifier = Modifier.size(18.dp),
                tint = if (active || progress != null) IpColors.Accent else IpColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun CalibrationSteps() {
    Panel(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Step(number = "1", text = "Extend your arm into a relaxed pointing position.")
            Step(number = "2", text = "Hold still until the ring completes.")
            Step(number = "3", text = "Move your wrist; the cursor follows.")
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(19.dp)
                .clip(CircleShape)
                .background(IpColors.AccentDim),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                style = IpTypography.labelMedium,
                color = IpColors.Accent,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            style = IpTypography.bodySmall,
            color = IpColors.TextSecondary,
            maxLines = 2,
        )
    }
}

/** Non-dismissible full-screen calibration state. Stop is always available. */
@Composable
fun CalibrationOverlay(
    progress: Float,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IpColors.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 30.dp, end = 30.dp, top = 24.dp, bottom = 58.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CalibrationGauge(progress = progress, active = true)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Hold your wrist steady",
                style = IpTypography.titleLarge,
                color = IpColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Movement is paused.",
                style = IpTypography.bodySmall,
                color = IpColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        TextAction(
            text = "Stop session",
            onClick = onStop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp),
            color = IpColors.TextTertiary,
        )
    }
}

/** Shown when calibration times out; the HID link remains connected for an immediate retry. */
@Composable
fun CalibrationFailureOverlay(
    message: String?,
    onRetry: () -> Unit,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IpColors.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IpColors.AccentDim),
                contentAlignment = Alignment.Center,
            ) {
                IpIcon(IpIconType.WARNING, Modifier.size(18.dp), IpColors.Warning)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Calibration failed",
                style = IpTypography.titleLarge,
                color = IpColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message ?: "Hold your wrist still for one second, then try again.",
                style = IpTypography.bodySmall,
                color = IpColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = "Try again",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                primary = true,
            )
            Spacer(Modifier.height(4.dp))
            TextAction(
                text = "Stop session",
                onClick = onStop,
                color = IpColors.Danger,
            )
        }
    }
}

/** Small confirmation shown for 1.2 seconds after a successful capture. */
@Composable
fun CalibrationSuccessBadge() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 44.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(IpColors.SurfaceRaised)
                .border(1.dp, IpColors.Hairline, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IpIcon(IpIconType.CHECK, Modifier.size(13.dp), IpColors.Success)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Reference captured",
                style = IpTypography.bodySmall,
                color = IpColors.TextPrimary,
                maxLines = 1,
            )
        }
    }
}
