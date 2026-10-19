package com.imagipoint.watch.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.imagipoint.watch.BuildConfig
import com.imagipoint.watch.ui.BrandWordmark
import com.imagipoint.watch.ui.Hairline
import com.imagipoint.watch.ui.Panel
import com.imagipoint.watch.ui.PillButton
import com.imagipoint.watch.ui.ScreenScaffold
import com.imagipoint.watch.ui.SectionLabel
import com.imagipoint.watch.ui.theme.IpColors
import com.imagipoint.watch.ui.theme.IpTypography

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    ScreenScaffold(title = "About", onBack = onBack) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 2.dp,
                bottom = 64.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    BrandWordmark()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = IpTypography.labelMedium,
                        color = IpColors.TextTertiary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "A quiet air mouse for Wear OS.",
                        style = IpTypography.bodySmall,
                        color = IpColors.TextSecondary,
                    )
                }
            }

            item { SectionLabel("Privacy") }
            item {
                NoticeGroup {
                    NoticeText(
                        "No internet permission. No accounts, telemetry, ads, or uploads. " +
                            "Bluetooth mouse reports go directly to the computer you pair. " +
                            "Gesture inference runs entirely on this watch.",
                    )
                }
            }

            item { SectionLabel("Gesture dataset") }
            item {
                NoticeGroup {
                    NoticeText(
                        "Gesture-recognition model trained using the Hand Gesture Accelerometer " +
                            "and Gyroscope Dataset (HGAG-DATA), Version 3, by Khalid A. Abbas, " +
                            "Mofeed Turky Rashid, and Luigi Fortuna. Mendeley Data (2025). " +
                            "https://doi.org/10.17632/mkhn7kxjvy.3. Licensed under Creative " +
                            "Commons Attribution 4.0 International (CC BY 4.0): " +
                            "https://creativecommons.org/licenses/by/4.0/.",
                    )
                    Hairline(Modifier.padding(vertical = 8.dp))
                    NoticeText(
                        "Abbas, K. A., Rashid, M. T., & Fortuna, L. (2025). \u201CThe " +
                            "mechanomyographic dataset of hand gestures harvested using an " +
                            "accelerometer and gyroscope.\u201D Data in Brief, 60, 111558. " +
                            "https://doi.org/10.1016/j.dib.2025.111558",
                    )
                    Hairline(Modifier.padding(vertical = 8.dp))
                    NoticeText(
                        "The model file was supplied with this project. Dataset attribution " +
                            "describes the training data and does not imply authorship or a " +
                            "separate license for the trained weights.",
                        color = IpColors.TextTertiary,
                    )
                }
            }

            item { SectionLabel("Software credits") }
            item {
                NoticeGroup {
                    CreditLine(
                        "WearMouse",
                        "Apache License 2.0. The mouse HID descriptor and low-level report " +
                            "approach were adapted from https://github.com/ginkage/wearmouse. " +
                            "Copyright notices are retained in the source.",
                    )
                    Hairline(Modifier.padding(vertical = 8.dp))
                    CreditLine(
                        "Doublepoint Touch SDK",
                        "Algorithmic inspiration only. The ray-casting mathematics informed " +
                            "an independent Kotlin implementation; no JavaScript SDK code or " +
                            "runtime dependency is included. " +
                            "https://github.com/basherbots/touch-sdk-js",
                    )
                    Hairline(Modifier.padding(vertical = 8.dp))
                    CreditLine(
                        "TensorFlow Lite",
                        "TensorFlow Lite 2.16.1, Apache License 2.0. Used for local, offline " +
                            "gesture inference.",
                    )
                    Hairline(Modifier.padding(vertical = 8.dp))
                    CreditLine(
                        "HGAG-DATA",
                        "CC BY 4.0. Dataset by Abbas, Rashid, and Fortuna. Mendeley Data, " +
                            "version 3.",
                    )
                }
            }

            item { SectionLabel("Device") }
            item {
                NoticeGroup {
                    DetailLine("Watch", "${Build.MANUFACTURER} ${Build.MODEL}")
                    DetailLine("Android", Build.VERSION.RELEASE)
                    DetailLine("App", "com.imagipoint.watch ${BuildConfig.VERSION_NAME}")
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton(
                        text = "Licenses & notices",
                        onClick = onOpenLicenses,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PillButton(
                        text = "Diagnostics",
                        onClick = onOpenDiagnostics,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Text(
                    text = "ImagiPoint is an independent project. It is not affiliated with, " +
                        "endorsed by, or an official successor to Apple, Google, Samsung, " +
                        "Doublepoint, WearMouse, or WowMouse. Apple is a trademark of Apple Inc.",
                    style = IpTypography.labelMedium,
                    color = IpColors.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun NoticeGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Panel(
        modifier = Modifier.fillMaxWidth(),
        padding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        content()
    }
}

@Composable
private fun NoticeText(text: String, color: androidx.compose.ui.graphics.Color = IpColors.TextSecondary) {
    Text(
        text = text,
        style = IpTypography.bodySmall,
        color = color,
    )
}

@Composable
private fun CreditLine(title: String, body: String) {
    Column {
        Text(text = title, style = IpTypography.titleMedium)
        Text(
            text = body,
            style = IpTypography.bodySmall,
            color = IpColors.TextSecondary,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text = label,
            style = IpTypography.bodySmall,
            color = IpColors.TextTertiary,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = value,
            style = IpTypography.bodySmall,
            color = IpColors.TextPrimary,
        )
    }
}
