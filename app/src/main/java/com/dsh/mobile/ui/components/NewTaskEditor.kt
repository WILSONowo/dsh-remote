package com.dsh.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** Session creation uses an agent preset; session-only model/permission controls appear after creation. */
@Composable
fun NewTaskEditor(input: String, onInputChange: (String) -> Unit, placeholder: String,
    sending: Boolean, canSend: Boolean, onSend: () -> Unit, onVoice: () -> Unit,
    preset: String, presets: List<Pair<String, String>>, onPresetSelect: (String) -> Unit,
    onPickImage: () -> Unit, error: String?) {
    var addMenu by remember { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }
    MessageEditor(input, onInputChange, placeholder, RoundedCornerShape(28.dp), canSend, sending, false, onSend, {},
        showPermissions = false, modelLabel = presets.firstOrNull { it.first == preset }?.second ?: preset,
        modelDescription = "预设", onModel = { presetMenu = true }, onVoice = onVoice, onAdd = { addMenu = true },
        maxLines = if (LocalConfiguration.current.screenHeightDp < 600) 3 else 6,
        addMenu = {
            ComposerPopup(addMenu, { addMenu = false }) {
                ComposerMenuItem(text = { Text("添加图片") }, leadingIcon = { Icon(Icons.Outlined.Image, null) },
                    onClick = { addMenu = false; onPickImage() })
            }
        }, modelMenu = {
            ComposerPopup(presetMenu, { presetMenu = false }) {
                presets.forEach { (id, name) ->
                    ComposerMenuItem(text = { Text(name) },
                        trailingIcon = { if (id == preset) Icon(Icons.Default.Check, "已选择") },
                        onClick = { onPresetSelect(id); presetMenu = false })
                }
            }
        })
    error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

@Composable
fun RecentSessionControls(total: Int, visible: Int, onMore: () -> Unit, onCollapse: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        if (visible < total) TextButton(onClick = onMore) { Text("显示更多") }
        if (visible > 5) TextButton(onClick = onCollapse) { Text("收起") }
    }
}
