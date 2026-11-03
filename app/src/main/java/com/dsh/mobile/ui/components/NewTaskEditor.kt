package com.dsh.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.dsh.mobile.data.AgentPreset
import com.dsh.mobile.data.DshConnection
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
internal fun presetRoster(connection: DshConnection, enabled: Boolean, reload: Int): Result<List<AgentPreset>>? {
    val presets = connection.directory.presets
    val snapshot by presets.state.collectAsState()
    LaunchedEffect(presets, enabled, reload) {
        if (enabled) presets.load(force = reload > 0)
    }
    if (!enabled) return null
    return snapshot.error?.let { Result.failure(it) } ?: snapshot.value?.let { Result.success(it) }
}

@Composable
fun NewTaskEditor(input: String, onInputChange: (String) -> Unit, placeholder: String,
    sending: Boolean, canSend: Boolean, onSend: () -> Unit, onVoice: () -> Unit,
    preset: AgentPreset?, roster: Result<List<AgentPreset>>?, onPresetSelect: (String) -> Unit,
    onRetry: () -> Unit, onPickImage: () -> Unit, error: String?, actionsEnabled: Boolean, sendEnabled: Boolean) {
    var addMenu by remember { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }
    MessageEditor(input, onInputChange, placeholder, RoundedCornerShape(28.dp), canSend, sending, false, onSend, {},
        actionsEnabled = actionsEnabled, sendEnabled = sendEnabled, showPermissions = false, modelLabel = preset?.label ?: "电脑默认",
        modelDescription = "切换预设", onModel = { presetMenu = true }, onVoice = onVoice, onAdd = { addMenu = true },
        maxLines = if (LocalConfiguration.current.screenHeightDp < 600) 3 else 6,
        addMenu = {
            ComposerPopup(addMenu, { addMenu = false }) {
                ComposerMenuItem(text = { Text("添加图片") }, leadingIcon = { Icon(Icons.Outlined.Image, null) },
                    onClick = { addMenu = false; onPickImage() })
            }
        }, modelMenu = {
            ComposerPopup(presetMenu, { presetMenu = false }) {
                if (roster == null) ComposerMenuItem({ Text("加载中…") }, {}, enabled = false)
                else if (roster.isFailure) ComposerMenuItem({ Text("重试") }, onRetry)
                else if (roster.getOrNull().isNullOrEmpty()) ComposerMenuItem({ Text("电脑默认") }, { presetMenu = false })
                roster?.getOrNull().orEmpty().forEach { item ->
                    ComposerMenuItem(text = { Text(item.label) },
                        trailingIcon = { if (item.id == preset?.id) Icon(Icons.Default.Check, "已选择") },
                        onClick = { onPresetSelect(item.id); presetMenu = false })
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
