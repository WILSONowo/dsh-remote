package com.dsh.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared anchored menus for the composer, constrained to the available window. */
@Composable
fun ComposerPopup(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    preferredWidth: Dp = 280.dp,
    content: @Composable ColumnScope.() -> Unit) {
    val configuration = LocalConfiguration.current
    val width = minOf(preferredWidth, (configuration.screenWidthDp - 32).coerceAtLeast(160).dp)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest,
        modifier = modifier.width(width).heightIn(max = (configuration.screenHeightDp * 0.65f).dp),
        offset = DpOffset(0.dp, 8.dp), shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 4.dp,
        content = content)
}

@Composable
fun ComposerMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null) {
    val foreground = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f)
    CompositionLocalProvider(LocalContentColor provides foreground) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (leadingIcon != null) Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leadingIcon() }
            Box(Modifier.weight(1f)) { ProvideTextStyle(MaterialTheme.typography.bodyLarge, text) }
            if (trailingIcon != null) Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { trailingIcon() }
        }
    }
}
