package com.dsh.mobile.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.*
import com.dsh.mobile.DshApplication
import com.dsh.mobile.ui.theme.DshShape
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HostProfileScreen(profileId: String?, connection: DshConnection, onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val isNew = profileId == null

    // 非阻塞加载原 profile：组合期不读 DataStore；新建时 produce 一个空 profile 供编辑
    val original by produceState<HostProfile?>(
        initialValue = null,
        profileId,
    ) {
        settingsStore.ensureMigrated()
        value = settingsStore.profiles.first().firstOrNull { it.id == profileId }
            ?: if (profileId == null) HostProfile(id = UUID.randomUUID().toString(), remark = "", url = "") else null
    }

    var remark by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var connectionMode by remember { mutableStateOf("direct") }
    var codeDirect by remember { mutableStateOf(false) }
    var relayServerUrl by remember { mutableStateOf("") }
    var relayHostId by remember { mutableStateOf("") }
    var trustSelfSigned by remember { mutableStateOf(false) }
    var caCertUri by remember { mutableStateOf<String?>(null) }
    var proxyType by remember { mutableStateOf("none") }
    var proxyHost by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("") }
    var proxyUser by remember { mutableStateOf("") }
    var proxyPass by remember { mutableStateOf("") }
    var autoConnect by remember { mutableStateOf(false) }
    var diag by remember { mutableStateOf<List<DiagStep>?>(null) }
    var diagRunning by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 原 profile 就绪后回填一次各字段（编辑态加载到才填；新建态回填空值无副作用）
    LaunchedEffect(original) {
        val p = original ?: return@LaunchedEffect
        remark = p.remark
        url = p.url
        connectionMode = p.connectionMode
        relayServerUrl = p.relayServerUrl
        relayHostId = p.relayHostId
        trustSelfSigned = p.trustSelfSigned
        caCertUri = p.caCertUri
        proxyType = p.proxy?.type ?: "none"
        proxyHost = p.proxy?.host ?: ""
        proxyPort = p.proxy?.port?.takeIf { it > 0 }?.toString() ?: ""
        proxyUser = p.proxy?.username ?: ""
        proxyPass = p.proxy?.password ?: ""
        autoConnect = p.autoConnect
    }

    val caPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val dir = File(context.filesDir, "certs").apply { mkdirs() }
                    val target = File(dir, (original?.id ?: "new") + ".pem")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                    caCertUri = target.absolutePath
                }.onFailure {
                    Toast.makeText(context, "CA 证书导入失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun save() {
        val edited = (original ?: HostProfile(id = UUID.randomUUID().toString(), remark = "", url = ""))
            .copy(
                // 设备名称留空即用机型；不再把地址回填成名称（列表不展示 IP）
                remark = remark.trim(),
                url = normalizeBaseUrl(url),
                connectionMode = connectionMode,
                relayServerUrl = relayServerUrl.trim(),
                relayHostId = relayHostId.trim(),
                trustSelfSigned = trustSelfSigned,
                caCertUri = caCertUri,
                proxy = if (proxyType == "none") null else ProxyConfig(
                    type = proxyType, host = proxyHost,
                    port = proxyPort.toIntOrNull() ?: 0,
                    username = proxyUser, password = proxyPass,
                ),
                autoConnect = autoConnect,
            )
        val error = runCatching { edited.connectionUrl() }.exceptionOrNull()
        if (error != null) {
            Toast.makeText(context, error.message ?: "请检查连接设置", Toast.LENGTH_SHORT).show()
            return
        }
        val profile = resetPairingIfEndpointChanged(original, edited)
        scope.launch {
            settingsStore.upsertProfile(profile)
            if (isNew) {
                settingsStore.profiles.first().filter { it.id != profile.id && it.autoConnect }.forEach {
                    settingsStore.upsertProfile(it.copy(autoConnect = false))
                }
                settingsStore.setActiveProfile(profile.id)
                connection.connect(profile)
                onBack()
                return@launch
            }
            // 活跃主机改动需传导到前台连接与后台服务 watcher
            if (settingsStore.activeProfileId.first() == profile.id) {
                val orig = original
                val paramsChanged = orig == null ||
                    orig.url != profile.url || orig.connectionMode != profile.connectionMode ||
                    orig.relayServerUrl != profile.relayServerUrl || orig.relayHostId != profile.relayHostId ||
                    orig.trustSelfSigned != profile.trustSelfSigned ||
                    orig.caCertUri != profile.caCertUri ||
                    orig.proxy != profile.proxy
                if (paramsChanged) {
                    // 先断开再连接：URL 不变时 connect() 会因「已连接且同地址」短路，
                    // 显式断开确保 SSL/代理等参数变化也能重建 OkHttpClient
                    connection.disconnect()
                    connection.connect(profile)
                }
            }
            onBack()
        }
    }

    fun delete() {
        original?.let { p ->
            scope.launch {
                // 删除活跃主机：先断开前台连接，避免幽灵连接
                if (settingsStore.activeProfileId.first() == p.id) {
                    connection.disconnect()
                }
                settingsStore.deleteProfile(p.id)
                onBack()
            }
        }
    }

    fun runDiag() {
        val profile = (original ?: return).copy(
            remark = remark, url = normalizeBaseUrl(url),
            connectionMode = connectionMode, relayServerUrl = relayServerUrl.trim(), relayHostId = relayHostId.trim(),
            trustSelfSigned = trustSelfSigned, caCertUri = caCertUri,
            proxy = if (proxyType == "none") null else ProxyConfig(
                type = proxyType, host = proxyHost, port = proxyPort.toIntOrNull() ?: 0,
                username = proxyUser, password = proxyPass,
            ),
        )
        diagRunning = true
        scope.launch {
            diag = runCatching { runDiagnostics(profile) }.getOrElse {
                listOf(DiagStep("连接设置", false, it.message, 0))
            }
            diagRunning = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "添加电脑" else "编辑电脑") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = remark, onValueChange = { remark = it },
                label = { Text("设备名称（可选）") },
                placeholder = { Text("如：家里 / 公司；留空显示电脑名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = connectionMode == "direct" && !codeDirect, onClick = { connectionMode = "direct"; codeDirect = false }, label = { Text("IP 直连") })
                if (isNew) FilterChip(selected = codeDirect, onClick = { connectionMode = "direct"; codeDirect = true }, label = { Text("配对码直连") })
                FilterChip(selected = connectionMode == "relay", onClick = { connectionMode = "relay"; codeDirect = false; trustSelfSigned = false }, label = { Text("服务器") })
            }
            if (codeDirect) {
                LanPairingSection(onIp = { codeDirect = false }, onSelected = { target, address ->
                    // Reuse a saved endpoint without treating discovery metadata as an authenticated identity.
                    val saved = settingsStore.profiles.first()
                    val previous = saved.firstOrNull { it.connectionMode == "direct" && normalizeBaseUrl(it.url) == address }
                    val profile = (previous ?: HostProfile(id = UUID.randomUUID().toString(), remark = "", url = address))
                        .copy(remark = remark.trim().ifBlank { previous?.remark.orEmpty() },
                            deviceModel = previous?.deviceModel?.ifBlank { target.name } ?: target.name, autoConnect = true)
                    connection.disconnect()
                    (context.applicationContext as? DshApplication)?.pairingCoordinator?.prepareDiscovery(profile, target.session)
                    saved.filter { it.autoConnect && it.id != profile.id }.forEach { settingsStore.upsertProfile(it.copy(autoConnect = false)) }
                    settingsStore.upsertProfile(profile)
                    settingsStore.setActiveProfile(profile.id)
                    connection.connect(profile)
                    onBack()
                })
            } else {
                if (connectionMode == "relay") {
                    Text("请从首页扫码或粘贴电脑生成的连接信息。服务器地址和电脑身份由程序管理，不能手动填写。")
                    if (relayServerUrl.isNotBlank()) Text("服务器：$relayServerUrl", style = MaterialTheme.typography.bodySmall)
                } else OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("电脑地址") },
                    placeholder = { Text("192.168.1.100:8787 或电脑的可访问域名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (connectionMode != "relay" || !isNew) {
                // 已记录的设备信息（只读）：重连校验依据
                original?.let { p ->
                    if (p.deviceModel.isNotBlank() || p.computerId.isNotBlank()) {
                        Card(Modifier.fillMaxWidth(), shape = DshShape.card) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("已记录设备", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (p.deviceModel.isNotBlank()) "电脑名称：${p.deviceModel}" else "电脑名称：未记录",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (p.computerId.isNotBlank()) "已保存电脑身份，修改名称不会影响配对" else "配对后自动保存电脑身份",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth(), shape = DshShape.card) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("HTTPS 证书", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("信任自签名证书", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (connectionMode == "relay") "服务器中转必须校验证书；使用自签名证书时请导入 CA 证书" else "⚠ 仅对本主机生效，跳过证书校验",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Switch(checked = trustSelfSigned, enabled = connectionMode == "direct", onCheckedChange = { trustSelfSigned = it })
                        }
                        OutlinedButton(onClick = { caPicker.launch(arrayOf("application/x-pem-file", "application/octet-stream")) }) {
                            Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导入 CA 证书")
                        }
                        caCertUri?.let {
                            Text(
                                "已导入：$it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Card(Modifier.fillMaxWidth(), shape = DshShape.card) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("代理（按主机）", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf("none" to "无", "http" to "HTTP", "socks5" to "SOCKS5").forEach { (v, label) ->
                                FilterChip(
                                    selected = proxyType == v,
                                    onClick = { proxyType = v },
                                    label = { Text(label) },
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                        if (proxyType != "none") {
                            OutlinedTextField(
                                value = proxyHost, onValueChange = { proxyHost = it },
                                label = { Text("代理主机") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = proxyPort, onValueChange = { proxyPort = it },
                                label = { Text("端口") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (proxyType == "http") {
                                OutlinedTextField(
                                    value = proxyUser, onValueChange = { proxyUser = it },
                                    label = { Text("账号（可选）") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = proxyPass, onValueChange = { proxyPass = it },
                                    label = { Text("密码（可选）") }, singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else if (proxyType == "socks5") {
                                Text(
                                    "SOCKS5 暂不支持认证（OkHttp 限制）；需要认证请改用 HTTP 代理",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启动时自动连接", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = autoConnect, onCheckedChange = { autoConnect = it })
                }

                Button(
                    onClick = { runDiag() },
                    enabled = !diagRunning && url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = DshShape.pill,
                ) {
                    if (diagRunning) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("诊断中…")
                    } else {
                        Text("测试连接")
                    }
                }

                diag?.let { steps ->
                    Card(Modifier.fillMaxWidth(), shape = DshShape.card) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("诊断结果", style = MaterialTheme.typography.titleSmall)
                            steps.forEach { s ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (s.ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        null,
                                        Modifier.size(16.dp),
                                        tint = if (s.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("${s.name}（${s.elapsedMs}ms）", style = MaterialTheme.typography.bodySmall)
                                        s.detail?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { save() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = DshShape.pill,
                ) { Text(if (isNew) "保存并连接" else "保存") }

                if (!isNew) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = DshShape.pill,
                    ) { Text("删除该主机", color = MaterialTheme.colorScheme.error) }
                }
            }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("删除记录") },
        text = { Text(original?.remark?.ifBlank { original?.deviceModel.orEmpty() }.orEmpty()) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; delete() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )

}
