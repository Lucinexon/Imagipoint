package com.imagipoint.watch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.imagipoint.watch.gestures.GestureAction
import com.imagipoint.watch.ui.theme.IpColors
import com.imagipoint.watch.ui.theme.IpNumericStyle
import com.imagipoint.watch.ui.theme.IpTypography
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberTapHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

@Composable
fun rememberConfirmHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}

@Composable
fun IpIconButton(
    type: IpIconType,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 44,
    visualSize: Int = 36,
    tint: Color = IpColors.TextPrimary,
    bordered: Boolean = false,
    enabled: Boolean = true,
) {
    val haptic = rememberTapHaptic()
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .then(if (bordered) Modifier.border(1.dp, IpColors.Hairline, CircleShape) else Modifier)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        IpIcon(
            type = type,
            modifier = Modifier.size(visualSize.dp),
            tint = if (enabled) tint else IpColors.TextTertiary,
            contentDescription = contentDescription,
        )
    }
}

/**
 * Quiet, near-black instrument panel used instead of a stack of bordered cards.
 * The gradient is static; only state colour changes.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = rememberTapHaptic()
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(IpColors.SurfaceRaised.copy(alpha = 0.92f), IpColors.Surface),
                ),
            )
            .border(
                width = 1.dp,
                color = if (accent) IpColors.Accent.copy(alpha = 0.20f) else IpColors.HairlineSoft,
                shape = shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button) {
                        haptic()
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(padding),
        content = content,
    )
}

/**
 * The home-screen focal control: a machined bezel, static tick ring and one live arc.
 * It deliberately has no decorative animation; the arc communicates state.
 */
@Composable
fun InstrumentDial(
    actionLabel: String,
    icon: IpIconType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    failed: Boolean = false,
    loading: Boolean = false,
    progress: Float? = null,
    caption: String? = null,
    diameter: androidx.compose.ui.unit.Dp = 112.dp,
) {
    val haptic = rememberConfirmHaptic()
    val accentAlpha = if (failed) 0.75f else if (active) 0.9f else 0.55f
    Box(
        modifier = modifier
            .size(diameter)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val tickOuter = radius - 1.dp.toPx()
            for (index in 0 until 48) {
                val angle = Math.toRadians(index * 7.5 - 90.0)
                val major = index % 4 == 0
                val length = if (major) 4.5.dp.toPx() else 2.5.dp.toPx()
                val start = Offset(
                    center.x + cos(angle).toFloat() * tickOuter,
                    center.y + sin(angle).toFloat() * tickOuter,
                )
                val end = Offset(
                    center.x + cos(angle).toFloat() * (tickOuter - length),
                    center.y + sin(angle).toFloat() * (tickOuter - length),
                )
                drawLine(
                    color = if (major) IpColors.Hairline else IpColors.HairlineSoft,
                    start = start,
                    end = end,
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val arcRadius = radius - 11.dp.toPx()
            val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
            val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
            drawArc(
                color = IpColors.HairlineSoft,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(1.dp.toPx()),
            )
            when {
                progress != null -> drawArc(
                    color = IpColors.Accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
                active || failed -> drawArc(
                    color = IpColors.Accent.copy(alpha = accentAlpha),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
                else -> drawArc(
                    color = IpColors.Accent.copy(alpha = 0.65f),
                    startAngle = -90f,
                    sweepAngle = 28f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.72f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(IpColors.SurfaceRaised, IpColors.Graphite),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = if (active) {
                        IpColors.Accent.copy(alpha = 0.34f)
                    } else {
                        IpColors.Hairline
                    },
                    shape = CircleShape,
                )
                .alpha(if (enabled) 1f else 0.55f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (caption != null) {
                    Text(
                        text = caption.uppercase(),
                        style = IpTypography.labelSmall.copy(fontSize = 7.sp, letterSpacing = 1.1.sp),
                        color = when {
                            active -> IpColors.Success
                            failed -> IpColors.Danger
                            else -> IpColors.TextTertiary
                        },
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                if (loading) {
                    LoadingRing(Modifier.size(24.dp))
                } else {
                    IpIcon(
                        type = icon,
                        modifier = Modifier.size(17.dp),
                        tint = if (active || failed) IpColors.Accent else IpColors.TextPrimary,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = actionLabel,
                    style = IpTypography.labelLarge.copy(fontSize = 11.sp, lineHeight = 13.sp),
                    color = if (active || failed) IpColors.Accent else IpColors.TextPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IpColors.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 26.dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IpIconButton(
                type = IpIconType.BACK,
                contentDescription = "Back",
                onClick = onBack,
                size = 44,
                visualSize = 21,
                tint = IpColors.TextSecondary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                style = IpTypography.titleLarge,
                color = IpColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            content = content,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(3.dp)
                .background(IpColors.Accent.copy(alpha = 0.7f), CircleShape),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text.uppercase(),
            style = IpTypography.labelMedium,
            color = IpColors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(IpColors.HairlineSoft),
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: IpIconType? = null,
    danger: Boolean = false,
) {
    val haptic = rememberTapHaptic()
    val contentColor = when {
        !enabled -> IpColors.TextTertiary
        primary -> IpColors.Black
        danger -> IpColors.Danger
        else -> IpColors.TextPrimary
    }
    val background = if (primary && enabled) IpColors.Accent else Color.Transparent
    val borderColor = when {
        primary -> Color.Transparent
        danger -> IpColors.Danger.copy(alpha = 0.5f)
        else -> IpColors.Hairline
    }
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic()
                    onClick()
                },
            )
            .padding(horizontal = 17.dp, vertical = 11.dp)
            .alpha(if (enabled) 1f else 0.6f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IpIcon(icon, Modifier.size(14.dp), contentColor)
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            style = IpTypography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = IpColors.TextSecondary,
    enabled: Boolean = true,
) {
    val haptic = rememberTapHaptic()
    Text(
        text = text,
        style = IpTypography.labelLarge,
        color = if (enabled) color else IpColors.TextTertiary,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic()
                    onClick()
                },
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    warning: Boolean = false,
) {
    val color = if (warning) IpColors.Warning else IpColors.Danger
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IpIcon(
            type = IpIconType.WARNING,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp),
            tint = color,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = message,
                style = IpTypography.bodySmall,
                color = IpColors.TextPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (actionText != null && onAction != null) {
                TextAction(
                    text = actionText,
                    onClick = onAction,
                    color = color,
                    modifier = Modifier.padding(start = 0.dp),
                )
            }
        }
    }
}

@Composable
fun StatusDot(
    color: Color,
    pulsing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (pulsing) {
            Box(
                Modifier
                    .size(8.dp)
                    .border(1.dp, color.copy(alpha = 0.32f), CircleShape),
            )
        }
        Box(
            Modifier
                .size(5.dp)
                .background(color, CircleShape),
        )
    }
}

@Composable
fun CompactToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val haptic = rememberTapHaptic()
    Box(
        modifier = modifier
            .width(52.dp)
            .height(44.dp)
            .clickable(
                role = Role.Switch,
                onClick = {
                    haptic()
                    onCheckedChange(!checked)
                },
            )
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    stateDescription = if (checked) "On" else "Off"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(25.dp)
                .clip(CircleShape)
                .background(if (checked) IpColors.Accent else IpColors.SurfaceRaised)
                .border(
                    1.dp,
                    if (checked) Color.Transparent else IpColors.Hairline,
                    CircleShape,
                )
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(19.dp)
                    .background(if (checked) IpColors.Black else IpColors.TextSecondary, CircleShape),
            )
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val haptic = rememberTapHaptic()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = {
                            haptic()
                            onClick()
                        },
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = IpTypography.titleMedium,
                color = IpColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = IpTypography.bodySmall,
                    color = IpColors.TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            IpIcon(
                IpIconType.CHEVRON_RIGHT,
                Modifier.size(16.dp),
                IpColors.TextTertiary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = IpTypography.titleMedium,
                color = if (enabled) IpColors.TextPrimary else IpColors.TextTertiary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = valueText,
                style = IpTypography.bodySmall,
                color = if (enabled) IpColors.TextSecondary else IpColors.TextTertiary,
                maxLines = 1,
            )
        }
        val fraction = ((local - valueRange.start) / (valueRange.endInclusive - valueRange.start))
            .coerceIn(0f, 1f)
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = {
                if (local != value) onCommit(local)
            },
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent,
            ),
            thumb = {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (enabled) IpColors.Accent else IpColors.TextTertiary),
                )
            },
            track = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(if (enabled) IpColors.Hairline else IpColors.Surface),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(2.dp)
                            .background(if (enabled) IpColors.Accent else IpColors.TextTertiary),
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
        )
    }
}

@Composable
fun SegmentedChoice(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onSelectLeft: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberTapHaptic()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(IpColors.Surface)
            .border(1.dp, IpColors.HairlineSoft, CircleShape)
            .padding(3.dp),
    ) {
        Segment(
            label = leftLabel,
            selected = leftSelected,
            modifier = Modifier.weight(1f),
            onClick = {
                haptic()
                onSelectLeft(true)
            },
        )
        Segment(
            label = rightLabel,
            selected = !leftSelected,
            modifier = Modifier.weight(1f),
            onClick = {
                haptic()
                onSelectLeft(false)
            },
        )
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(if (selected) IpColors.SurfaceRaised else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = IpTypography.labelLarge,
            color = if (selected) IpColors.Accent else IpColors.TextTertiary,
            maxLines = 1,
        )
    }
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = IpColors.TextSecondary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = IpTypography.bodySmall,
            color = IpColors.TextTertiary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = IpNumericStyle,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun BrandWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .border(1.dp, IpColors.Accent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IpIcon(IpIconType.CURSOR, Modifier.size(7.dp), IpColors.Accent)
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = "IMAGIPOINT",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 9.sp,
            letterSpacing = 2.7.sp,
            color = IpColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
fun DeviceChip(
    text: String,
    connected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberTapHaptic()
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .widthIn(max = 184.dp)
            .clip(CircleShape)
            .background(IpColors.Surface)
            .border(1.dp, IpColors.HairlineSoft, CircleShape)
            .clickable(
                role = Role.Button,
                onClick = {
                    haptic()
                    onClick()
                },
            )
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(if (connected) IpColors.Success else IpColors.TextTertiary)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = IpTypography.bodySmall,
            color = IpColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IpColors.Scrim)
                .padding(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(IpColors.SurfaceRaised, IpColors.Surface),
                        ),
                    )
                    .border(1.dp, IpColors.HairlineSoft, RoundedCornerShape(24.dp))
                    .padding(16.dp),
            ) {
                Text(title, style = IpTypography.titleLarge, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Text(
                    body,
                    style = IpTypography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        primary = !destructive,
                        danger = destructive,
                    )
                }
            }
        }
    }
}

@Composable
fun ActionLabel(action: GestureAction, modifier: Modifier = Modifier) {
    Text(
        text = action.displayName,
        style = IpTypography.bodySmall,
        color = if (action == GestureAction.NONE) IpColors.TextTertiary else IpColors.Accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun LoadingRing(
    modifier: Modifier = Modifier,
    color: Color = IpColors.Accent,
    progress: Float? = null,
) {
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        if (progress == null) {
            drawArc(
                color = color.copy(alpha = 0.20f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 105f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        } else {
            drawArc(
                color = color.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = IpTypography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        Text(
            body,
            style = IpTypography.bodySmall,
            color = IpColors.TextTertiary,
            textAlign = TextAlign.Center,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AnimatedErrorBanner(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    warning: Boolean = false,
) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        Box(Modifier.padding(top = 4.dp)) {
            ErrorBanner(
                message = message,
                actionText = actionText,
                onAction = onAction,
                warning = warning,
            )
        }
    }
}
