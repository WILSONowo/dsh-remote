package com.dsh.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Shared preference styling using the active DSH palette and native Compose semantics. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    summary: String? = null,
    icon: ImageVector? = null,
) {
    Row(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, null, Modifier.padding(9.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!value.isNullOrBlank()) Text(value, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
                }
            }
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun SettingsToggle(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        .heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f))
            Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f),
                modifier = Modifier.padding(top = 4.dp))
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White,
                uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSlider(title: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit) {
    val active = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.secondaryContainer
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(valueLabel, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = title },
            thumb = { Spacer(Modifier.size(width = 0.dp, height = 28.dp)) },
            track = {
                Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = radius)
                    val width = size.width * ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                    drawRoundRect(active, topLeft = Offset(if (rtl) size.width - width else 0f, 0f),
                        size = Size(width, size.height), cornerRadius = radius)
                }
            })
    }
}

@Composable
fun SettingsChoiceDialog(title: String, options: List<Pair<String, String>>, current: String,
    onDismiss: () -> Unit, onPick: (String) -> Unit, fontForOption: (String) -> FontFamily? = { null }) {
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (label, id) ->
                    val selected = current == id
                    val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "selection")
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color)
                        .selectable(selected, role = Role.RadioButton, onClick = { onPick(id) })
                        .heightIn(min = 56.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (selected) Icon(Icons.Default.Check, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        else Spacer(Modifier.size(22.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge,
                            fontFamily = fontForOption(id) ?: MaterialTheme.typography.bodyLarge.fontFamily,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
