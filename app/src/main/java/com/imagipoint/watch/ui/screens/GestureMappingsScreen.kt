package com.imagipoint.watch.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imagipoint.watch.gestures.GestureAction
import com.imagipoint.watch.gestures.GestureLabels
import com.imagipoint.watch.settings.AppSettings
import com.imagipoint.watch.ui.ActionLabel
import com.imagipoint.watch.ui.ConfirmDialog
import com.imagipoint.watch.ui.ErrorBanner
import com.imagipoint.watch.ui.Hairline
import com.imagipoint.watch.ui.Panel
import com.imagipoint.watch.ui.PillButton
import com.imagipoint.watch.ui.ScreenScaffold
import com.imagipoint.watch.ui.SectionLabel
import com.imagipoint.watch.ui.SettingRow
import com.imagipoint.watch.ui.TextAction
import com.imagipoint.watch.ui.rememberConfirmHaptic
import com.imagipoint.watch.ui.rememberTapHaptic
import com.imagipoint.watch.ui.theme.IpColors
import com.imagipoint.watch.ui.theme.IpTypography

/**
 * Only the eleven classes that are verified to exist in the supplied model are exposed.
 * Mapping changes are persisted immediately in local settings.
 */
@Composable
fun GestureMappingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var editingLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (editingLabel != null) editingLabel = null else onBack()
    }

    if (editingLabel != null) {
        GestureActionPicker(
            label = editingLabel.orEmpty(),
            selected = settings.gestureMappings[editingLabel] ?: GestureAction.NONE,
            onBack = { editingLabel = null },
            onSelect = { action ->
                val label = editingLabel
                if (label != null) {
                    onUpdate { current ->
                        current.copy(
                            gestureMappings = if (action == GestureAction.NONE) {
                                current.gestureMappings - label
                            } else {
                                current.gestureMappings + (label to action)
                            },
                        )
                    }
                }
                editingLabel = null
            },
        )
        return
    }

    ScreenScaffold(title = "Gestures", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "Optional mouse actions run entirely on this watch.",
                    style = IpTypography.bodySmall,
                    color = IpColors.TextSecondary,
                )
            }
            if (!settings.gesturesEnabled) {
                item {
                    ErrorBanner(
                        message = "Gesture actions are off. The pointer works without them.",
                        warning = true,
                        actionText = "Enable",
                        onAction = {
                            onUpdate { it.copy(gesturesEnabled = true) }
                        },
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Verified classes", Modifier.weight(1f))
                    Text(
                        text = "${settings.gestureMappings.size} mapped",
                        style = IpTypography.bodySmall,
                        color = IpColors.TextTertiary,
                    )
                }
            }
            item {
                Panel(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    GestureLabels.VERIFIED.forEachIndexed { index, label ->
                        if (index > 0) Hairline(Modifier.padding(horizontal = 4.dp))
                        val action = settings.gestureMappings[label] ?: GestureAction.NONE
                        SettingRow(
                            title = label,
                            subtitle = action.description,
                            onClick = { editingLabel = label },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ActionLabel(action, Modifier.width(66.dp))
                                    Spacer(Modifier.width(2.dp))
                                }
                            },
                        )
                    }
                }
            }
            item {
                Text(
                    text = "Confidence, stability, and cooldown are applied before any action. " +
                        "A held gesture fires once until it is released.",
                    style = IpTypography.bodySmall,
                    color = IpColors.TextTertiary,
                )
            }
            if (settings.gestureMappings.isNotEmpty()) {
                item {
                    TextAction(
                        text = "Clear all mappings",
                        onClick = { confirmClear = true },
                        color = IpColors.Danger,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            item {
                PillButton(
                    text = "Gesture settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear mappings?",
            body = "Every gesture will be ignored until you assign an action again.",
            confirmLabel = "Clear",
            onConfirm = {
                onUpdate { it.copy(gestureMappings = emptyMap()) }
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
            destructive = true,
        )
    }
}

@Composable
private fun GestureActionPicker(
    label: String,
    selected: GestureAction,
    onBack: () -> Unit,
    onSelect: (GestureAction) -> Unit,
) {
    val haptic = rememberTapHaptic()
    val confirmHaptic = rememberConfirmHaptic()
    ScreenScaffold(title = "Gesture action", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text(
                        text = label,
                        style = IpTypography.titleLarge,
                        color = IpColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Choose one mouse action. Actions never run while calibrating.",
                        style = IpTypography.bodySmall,
                        color = IpColors.TextSecondary,
                    )
                }
            }
            item {
                Panel(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    GestureAction.entries.forEachIndexed { index, action ->
                        if (index > 0) Hairline(Modifier.padding(horizontal = 4.dp))
                        val isSelected = action == selected
                        SettingRow(
                            title = action.displayName,
                            subtitle = action.description,
                            onClick = {
                                if (isSelected) haptic() else confirmHaptic()
                                onSelect(action)
                            },
                            trailing = {
                                if (isSelected) {
                                    Text(
                                        text = "Selected",
                                        style = IpTypography.bodySmall,
                                        color = IpColors.Accent,
                                    )
                                }
                            },
                        )
                    }
                }
            }
            item {
                Text(
                    text = "These actions use the standard three-button mouse report. " +
                        "They do not control media playback or system shortcuts.",
                    style = IpTypography.bodySmall,
                    color = IpColors.TextTertiary,
                )
            }
        }
    }
}
