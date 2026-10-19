package com.imagipoint.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Small, original line-icon set drawn on a 24x24 grid. Keeping these as code avoids a
 * multi-megabyte icon dependency and gives every glyph the same optical weight.
 */
enum class IpIconType {
    BACK,
    CHEVRON_RIGHT,
    SETTINGS,
    TARGET,
    CURSOR,
    STOP,
    PLAY,
    CHECK,
    WARNING,
    BLUETOOTH,
    REFRESH,
    INFO,
    CLOSE,
    SHIELD,
    COMPUTER,
    WAVE,
}

@Composable
fun IpIcon(
    type: IpIconType,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    contentDescription: String? = null,
) {
    val semantics = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(semantics) {
        val unit = size.minDimension / 24f
        val stroke = 1.8f * unit
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            color = tint,
            start = point(x1, y1),
            end = point(x2, y2),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        fun path(fill: Boolean = false, builder: Path.() -> Unit) {
            val p = Path().apply(builder)
            if (fill) {
                drawPath(p, tint)
            } else {
                drawPath(
                    p,
                    tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        when (type) {
            IpIconType.BACK -> path {
                moveTo(15f * unit, 5f * unit)
                lineTo(8f * unit, 12f * unit)
                lineTo(15f * unit, 19f * unit)
            }
            IpIconType.CHEVRON_RIGHT -> path {
                moveTo(9f * unit, 5f * unit)
                lineTo(16f * unit, 12f * unit)
                lineTo(9f * unit, 19f * unit)
            }
            IpIconType.SETTINGS -> {
                line(3f, 6f, 21f, 6f)
                line(3f, 12f, 21f, 12f)
                line(3f, 18f, 21f, 18f)
                drawCircle(tint, radius = 2.5f * unit, center = point(9f, 6f))
                drawCircle(tint, radius = 2.5f * unit, center = point(15f, 12f))
                drawCircle(tint, radius = 2.5f * unit, center = point(8f, 18f))
            }
            IpIconType.TARGET -> {
                drawCircle(tint, radius = 7f * unit, center = point(12f, 12f), style = Stroke(stroke))
                drawCircle(tint, radius = 2.2f * unit, center = point(12f, 12f))
                line(12f, 1.5f, 12f, 4.5f)
                line(12f, 19.5f, 12f, 22.5f)
                line(1.5f, 12f, 4.5f, 12f)
                line(19.5f, 12f, 22.5f, 12f)
            }
            IpIconType.CURSOR -> path(fill = true) {
                moveTo(6f * unit, 3f * unit)
                lineTo(19f * unit, 12.2f * unit)
                lineTo(12.8f * unit, 13.2f * unit)
                lineTo(9.2f * unit, 20.5f * unit)
                close()
            }
            IpIconType.STOP -> drawRoundRect(
                color = tint,
                topLeft = point(6f, 6f),
                size = androidx.compose.ui.geometry.Size(12f * unit, 12f * unit),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * unit),
            )
            IpIconType.PLAY -> path(fill = true) {
                moveTo(8f * unit, 4.5f * unit)
                lineTo(19.5f * unit, 12f * unit)
                lineTo(8f * unit, 19.5f * unit)
                close()
            }
            IpIconType.CHECK -> path {
                moveTo(4.5f * unit, 12.5f * unit)
                lineTo(9.7f * unit, 17.5f * unit)
                lineTo(19.5f * unit, 6.8f * unit)
            }
            IpIconType.WARNING -> {
                path {
                    moveTo(12f * unit, 3f * unit)
                    lineTo(22f * unit, 20.5f * unit)
                    lineTo(2f * unit, 20.5f * unit)
                    close()
                }
                line(12f, 9f, 12f, 14.5f)
                drawCircle(tint, radius = 1.1f * unit, center = point(12f, 17.4f))
            }
            IpIconType.BLUETOOTH -> path {
                moveTo(7f * unit, 7f * unit)
                lineTo(17f * unit, 17f * unit)
                lineTo(12f * unit, 21f * unit)
                lineTo(12f * unit, 3f * unit)
                lineTo(17f * unit, 7f * unit)
                lineTo(7f * unit, 17f * unit)
            }
            IpIconType.REFRESH -> {
                drawArc(
                    color = tint,
                    startAngle = 35f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = point(3.5f, 3.5f),
                    size = androidx.compose.ui.geometry.Size(17f * unit, 17f * unit),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                line(18.5f, 3.5f, 20.5f, 7.5f)
                line(20.5f, 7.5f, 16f, 7.8f)
            }
            IpIconType.INFO -> {
                drawCircle(tint, radius = 9f * unit, center = point(12f, 12f), style = Stroke(stroke))
                drawCircle(tint, radius = 1.2f * unit, center = point(12f, 7.5f))
                line(12f, 11f, 12f, 17f)
            }
            IpIconType.CLOSE -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            IpIconType.SHIELD -> path {
                moveTo(12f * unit, 2.8f * unit)
                lineTo(20f * unit, 6f * unit)
                lineTo(19f * unit, 13.5f * unit)
                cubicTo(18.4f * unit, 17.5f * unit, 15.6f * unit, 20f * unit, 12f * unit, 21.5f * unit)
                cubicTo(8.4f * unit, 20f * unit, 5.6f * unit, 17.5f * unit, 5f * unit, 13.5f * unit)
                lineTo(4f * unit, 6f * unit)
                close()
            }
            IpIconType.COMPUTER -> {
                drawRoundRect(
                    color = tint,
                    topLeft = point(3f, 4.5f),
                    size = androidx.compose.ui.geometry.Size(18f * unit, 12f * unit),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit),
                    style = Stroke(stroke),
                )
                line(12f, 16.5f, 12f, 20f)
                line(8f, 20f, 16f, 20f)
            }
            IpIconType.WAVE -> path {
                moveTo(2.5f * unit, 12f * unit)
                lineTo(6f * unit, 12f * unit)
                lineTo(8.5f * unit, 6f * unit)
                lineTo(12f * unit, 18f * unit)
                lineTo(15.5f * unit, 9f * unit)
                lineTo(18f * unit, 12f * unit)
                lineTo(21.5f * unit, 12f * unit)
            }
        }
    }
}
