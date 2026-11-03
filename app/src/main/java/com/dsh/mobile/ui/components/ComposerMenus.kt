package com.dsh.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.dsh.mobile.data.SessionModelsValue
import kotlinx.serialization.json.*

internal data class ComposerModel(
    val provider: String, val providerName: String, val id: String, val name: String,
    val efforts: List<Pair<String, String>>,
)

private fun modelDisplayName(name: String): String =
    if (name.length > 9 && name.startsWith("deepseek-", ignoreCase = true)) name.substring(9) else name

internal fun composerModels(info: SessionModelsValue?): List<ComposerModel> = runCatching {
    (info?.groups as? JsonArray).orEmpty().flatMap { group ->
        val g = group.jsonObject
        val provider = g["id"]?.jsonPrimitive?.contentOrNull ?: return@flatMap emptyList()
        val providerName = g["name"]?.jsonPrimitive?.contentOrNull ?: provider
        (g["models"] as? JsonArray).orEmpty().mapNotNull { model ->
            val m = model.jsonObject
            val id = m["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val efforts = ((m["reasoning"] as? JsonObject)?.get("efforts") as? JsonArray).orEmpty().mapNotNull { effort ->
                val e = effort.jsonObject
                val eid = e["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                eid to (e["name"]?.jsonPrimitive?.contentOrNull ?: eid)
            }
            ComposerModel(provider, providerName, id, modelDisplayName(m["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { id } ?: id), efforts)
        }
    }
}.getOrDefault(emptyList())

internal fun composerModelLabel(info: SessionModelsValue?): String {
    val selection = info?.current ?: return "模型"
    val model = composerModels(info).firstOrNull { it.provider == selection.provider && it.id == selection.model }
    val effort = model?.efforts?.firstOrNull { it.first == selection.reasoningEffort }?.second ?: selection.reasoningEffort
    return listOfNotNull(model?.name ?: modelDisplayName(selection.model).ifBlank { "模型" }, effort?.takeIf { it.isNotBlank() }).joinToString(" ")
}

@Composable
fun ModelPickerMenu(expanded: Boolean, info: SessionModelsValue?, loading: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onRetry: () -> Unit, onApply: (String, String, String?) -> Unit,
    autoSelected: Boolean = false, onAuto: (() -> Unit)? = null) {
    if (!expanded) return
    var modelList by remember(expanded) { mutableStateOf(false) }
    val models = remember(info) { composerModels(info) }
    val selected = models.firstOrNull { it.provider == info?.current?.provider && it.id == info.current.model }
    ComposerPopup(expanded = expanded, onDismissRequest = onDismiss, preferredWidth = 248.dp) {
        if (onAuto != null && !modelList) {
            ComposerMenuItem(text = { Text("自适应模型") }, enabled = !busy,
                trailingIcon = { if (autoSelected) Icon(Icons.Default.Check, "已选择") }, onClick = onAuto)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        if ((loading && models.isEmpty()) || busy) {
            ComposerMenuItem(text = { Text(if (busy) "切换中…" else "加载中…") }, enabled = false, onClick = {},
                leadingIcon = { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) })
        } else if (models.isEmpty()) {
            ComposerMenuItem(text = { Text("无法获取模型列表") }, enabled = false, onClick = {})
            ComposerMenuItem(text = { Text("重试") }, onClick = onRetry)
        } else if (modelList || selected == null) {
            if (selected != null) ComposerMenuItem(text = { Text("模型") }, leadingIcon = { Icon(Icons.Default.ArrowBack, null) },
                onClick = { modelList = false })
            models.groupBy { it.provider }.values.forEach { group ->
                Text(group.first().providerName, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                group.forEach { model ->
                    val current = model.provider == info?.current?.provider && model.id == info.current.model
                    ComposerMenuItem(text = { Text(model.name, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { if (current) Icon(Icons.Default.Check, "已选择") },
                        onClick = { onApply(model.provider, model.id, if (current) info?.current?.reasoningEffort else null) })
                }
            }
        } else {
            if (!selected?.efforts.isNullOrEmpty()) {
                Text("思考程度", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                selected!!.efforts.forEach { (id, name) ->
                    ComposerMenuItem(text = { Text(name) },
                        trailingIcon = { if (info?.current?.reasoningEffort == id) Icon(Icons.Default.Check, "已选择") },
                        onClick = { onApply(selected.provider, selected.id, id) })
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            ComposerMenuItem(text = {
                Column { Text("模型"); Text(selected?.name ?: info?.current?.model.orEmpty(),
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }, trailingIcon = { Icon(Icons.Default.ChevronRight, null) }, onClick = { modelList = true })
        }
    }
}

@Composable
fun AccessModeMenu(expanded: Boolean, current: String?, busy: Boolean, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val options = listOf(
        Triple("read-only", "只读", "只能读取文件，不能修改"),
        Triple("workspace-write", "工作区可写", "可修改工作区内的文件"),
        Triple("danger-full-access", "完全访问", "可执行任意操作（含敏感操作，谨慎使用）"),
    )
    ComposerPopup(expanded = expanded, onDismissRequest = onDismiss, preferredWidth = 304.dp) {
        options.forEach { (id, label, description) ->
            ComposerMenuItem(text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }, enabled = !busy, onClick = { onSelect(id) },
                leadingIcon = { Icon(when (id) {
                    "read-only" -> Icons.Outlined.Lock
                    "danger-full-access" -> Icons.Default.WarningAmber
                    else -> Icons.Outlined.Shield
                }, null) },
                trailingIcon = { if (current == id) Icon(Icons.Default.Check, "已选择") })
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    }
}
