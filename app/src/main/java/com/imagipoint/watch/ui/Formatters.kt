package com.imagipoint.watch.ui

import java.util.Locale
import kotlin.math.roundToInt

fun formatDecimal(value: Float, decimals: Int = 1): String =
    String.format(Locale.US, "%.${decimals}f", value)

fun formatPercent(value: Float): String = "${(value * 100f).roundToInt()}%"

fun formatMillis(value: Long): String = when {
    value < 1_000L -> "${value} ms"
    else -> String.format(Locale.US, "%.1f s", value / 1_000f)
}

fun formatSigned(value: Float, decimals: Int = 1): String {
    val text = formatDecimal(kotlin.math.abs(value), decimals)
    return if (value >= 0f) "+$text" else "-$text"
}

fun formatActionLabel(value: String): String =
    value.split('_').joinToString(" ") { word ->
        word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
    }
