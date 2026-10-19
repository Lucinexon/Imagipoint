package com.imagipoint.watch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imagipoint.watch.ui.BrandWordmark
import com.imagipoint.watch.ui.IpIcon
import com.imagipoint.watch.ui.IpIconType
import com.imagipoint.watch.ui.PillButton
import com.imagipoint.watch.ui.TextAction
import com.imagipoint.watch.ui.theme.IpColors
import com.imagipoint.watch.ui.theme.IpTypography

/**
 * First-run permission rationale. Deliberately compact: the primary action and the
 * skip action must fit inside the round 454 px safe area without clipping.
 */
@Composable
fun PermissionScreen(
    denied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IpColors.Black)
            .padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandWordmark()
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, IpColors.Hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(IpColors.SurfaceRaised, IpColors.Graphite),
                            ),
                        )
                        .border(1.dp, IpColors.Accent.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    IpIcon(
                        type = if (denied) IpIconType.WARNING else IpIconType.BLUETOOTH,
                        modifier = Modifier.size(17.dp),
                        tint = if (denied) IpColors.Warning else IpColors.Accent,
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = if (denied) "Nearby devices is off" else "Bluetooth access",
                style = IpTypography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (denied) {
                    "Turn on Nearby devices in system settings so ImagiPoint can act as a Bluetooth mouse."
                } else {
                    "ImagiPoint needs Nearby devices access to act as a Bluetooth mouse. " +
                        "Sensor data stays on your watch."
                },
                style = IpTypography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (denied) IpColors.Warning else IpColors.TextSecondary,
                maxLines = 4,
            )
            Spacer(Modifier.height(9.dp))
            PillButton(
                text = if (denied) "Open settings" else "Allow access",
                onClick = if (denied) onOpenSettings else onAllow,
                modifier = Modifier.fillMaxWidth(),
                primary = true,
            )
            Spacer(Modifier.height(1.dp))
            TextAction(
                text = if (denied) "Back" else "Not now",
                onClick = onSkip,
            )
        }
    }
}
