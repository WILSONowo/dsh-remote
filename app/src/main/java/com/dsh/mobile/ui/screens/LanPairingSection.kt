package com.dsh.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dsh.mobile.data.LanDiscovery
import com.dsh.mobile.data.probeLanPairingTarget
import com.dsh.mobile.data.LanPairingTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LanPairingSection(onIp: () -> Unit, onSelected: suspend (LanPairingTarget, String) -> Unit) {
    val context = LocalContext.current
    val discovery = remember { LanDiscovery(context) }
    val state by discovery.state.collectAsState()
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    var selected by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.targets) {
        if (selected !in state.targets.map { it.session }) selected = state.targets.singleOrNull()?.session
    }
    DisposableEffect(discovery, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) discovery.stop()
            if (event == Lifecycle.Event.ON_START) discovery.start()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) discovery.start()
        onDispose { owner.lifecycle.removeObserver(observer); discovery.stop() }
    }
    Text("在电脑的 DeepSeek Harness「远程控制 → 添加 → 配对码直连」打开配对页面。设备需在同一局域网，无需填写 IP。")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (state.searching) "正在查找电脑…" else "局域网电脑", style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { error = null; discovery.start() }, enabled = !busy) { Text("重新搜索") }
    }
    if (state.targets.isEmpty() && !state.searching) {
        Text("未发现等待配对的电脑。请检查电脑配对页面、局域网连接和防火墙；访客网络可能隔离设备。", style = MaterialTheme.typography.bodySmall)
    }
    state.targets.forEach { target ->
        OutlinedCard(onClick = { selected = target.session; error = null }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp)) {
                RadioButton(selected = selected == target.session, onClick = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(target.name)
                    Text("等待配对 · 识别标记 ${target.session.takeLast(6)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(enabled = selected != null && !busy, onClick = {
        val target = state.targets.find { it.session == selected } ?: return@Button
        busy = true; error = null
        scope.launch {
            try {
                val url = probeLanPairingTarget(target)
                if (url == null) error = "无法访问这台电脑，或配对页面已关闭。请重新搜索，也可改用 IP 直连。"
                else { discovery.stop(); onSelected(target, url) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "连接失败，请重新搜索后重试。" }
            finally { busy = false }
        }
    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "检查连接中…" else "继续，输入配对码") }
    TextButton(onClick = onIp, enabled = !busy) { Text("改用 IP 直连") }
}
