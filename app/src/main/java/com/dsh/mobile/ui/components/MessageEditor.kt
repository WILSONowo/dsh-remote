package com.dsh.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One rounded composer, with anchored menus and a voice/send/stop action. */
@Composable
fun MessageEditor(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    shape: Shape,
    canSend: Boolean,
    sending: Boolean,
    running: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    sendIcon: ImageVector = Icons.Default.ArrowUpward,
    interrupting: Boolean = false,
    permissionLabel: String = "权限",
    permissionIcon: ImageVector = Icons.Outlined.Shield,
    showPermissions: Boolean = true,
    actionsEnabled: Boolean = true,
    modelLabel: String = "模型",
    modelDescription: String = "切换模型",
    onPermissions: () -> Unit = {},
    onModel: () -> Unit = {},
    onVoice: (() -> Unit)? = null,
    onAdd: () -> Unit = {},
    maxLines: Int = 6,
    addMenu: @Composable () -> Unit = {},
    permissionMenu: @Composable () -> Unit = {},
    modelMenu: @Composable () -> Unit = {},
) {
    Surface(Modifier.fillMaxWidth().testTag("message_composer"), shape = shape,
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(8.dp)) {
            BasicTextField(value = value, onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("message_editor"),
                minLines = 1, maxLines = maxLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        innerTextField()
                    }
                })
            Row(Modifier.fillMaxWidth().testTag("message_toolbar"), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = onAdd, enabled = actionsEnabled, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Add, if (interrupting) "添加（插话已开启）" else "添加",
                            modifier = Modifier.size(28.dp),
                            tint = if (interrupting) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    addMenu()
                }
                if (showPermissions) Box {
                    IconButton(onClick = onPermissions, modifier = Modifier.size(48.dp).semantics { stateDescription = permissionLabel }) {
                        Icon(permissionIcon, "审查严格度（访问模式）", Modifier.size(24.dp))
                    }
                    permissionMenu()
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Box {
                        Row(Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).clip(CircleShape)
                            .clickable(enabled = actionsEnabled, onClick = onModel).semantics { contentDescription = modelDescription }
                            .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Text(modelLabel, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                        modelMenu()
                    }
                }
                val voice = !running && !sending && !canSend && onVoice != null
                val enabled = actionsEnabled && (running || voice || (canSend && !sending))
                IconButton(onClick = { when { running -> onStop(); voice -> onVoice?.invoke(); else -> onSend() } },
                    enabled = enabled, modifier = Modifier.size(48.dp).testTag("message_action")) {
                    Surface(shape = CircleShape, modifier = Modifier.size(38.dp),
                        color = if (enabled || sending) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        contentColor = if (enabled || sending) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.onSurfaceVariant) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                running -> Icon(Icons.Default.Stop, "停止运行", Modifier.size(20.dp))
                                sending -> CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                                voice -> Icon(Icons.Outlined.Mic, "语音输入", Modifier.size(22.dp))
                                else -> Icon(sendIcon, "发送", Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
