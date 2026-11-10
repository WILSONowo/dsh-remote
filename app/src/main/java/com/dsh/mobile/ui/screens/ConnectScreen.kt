package com.dsh.mobile.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dsh.mobile.R
import com.dsh.mobile.DshApplication
import com.dsh.mobile.data.*
import com.dsh.mobile.ui.theme.DshBrand
import com.dsh.mobile.ui.theme.DshSuccess
import com.dsh.mobile.ui.theme.DshShape
import com.dsh.mobile.ui.scanner.QrScannerActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 设备记录标题：自定义名 > 电脑名称 > 占位（列表不展示公网 IP） */
private fun deviceTitle(p: HostProfile): String =
    p.remark.ifBlank { p.deviceModel.ifBlank { "未命名设备" } }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    connection: DshConnection,
    onEditHost: (String?) -> Unit = {},
    startupPending: Boolean = false,
    onStartupHandled: () -> Unit = {},
    onOpenSessions: () -> Unit = {},
    onDisconnect: () -> Unit = {},
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    val profiles by settingsStore.profiles.collectAsState(initial = emptyList())
    val appCtx = context.applicationContext as? DshApplication
    var scanPreview by remember { mutableStateOf<PairingLink?>(null) }
    var showPaste by remember { mutableStateOf(false) }
    var pastedLink by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }
    val activeId by settingsStore.activeProfileId.collectAsState(initial = null)
    val connState by connection.state.collectAsState()
    var pendingDelete by remember { mutableStateOf<HostProfile?>(null) }

    val sortedProfiles = remember(profiles) { profiles.sortedByDescending { it.lastUsedAt } }
    val activeProfile = profiles.firstOrNull { it.id == activeId }

    // 设备记录的在线状态：页面可见时每 15 秒并发探测一次其余电脑（不建立加密通道，不发送凭证）
    val probes = remember { mutableStateMapOf<String, ProbeResult>() }
    val others = remember(sortedProfiles, activeId) { sortedProfiles.filter { it.id != activeId } }
    LaunchedEffect(others) {
        probes.keys.retainAll(others.map { it.id }.toSet())
        while (true) {
            kotlinx.coroutines.coroutineScope {
                others.forEach { p -> launch { probes[p.id] = HostProbe.probe(p) } }
            }
            kotlinx.coroutines.delay(15_000)
        }
    }

    fun connectTo(profile: HostProfile, link: PairingLink? = null) {
        scope.launch {
            val target = profile.copy(autoConnect = true)
            // 多设备记录共存：仅当前主机保留「启动自动连接」（记住上次使用），其余只取消标记、不删记录
            settingsStore.upsertProfile(target)
            settingsStore.setActiveProfile(profile.id)
            settingsStore.profiles.first()
                .filter { it.id != profile.id && it.autoConnect }
                .forEach { settingsStore.upsertProfile(it.copy(autoConnect = false)) }
            if (link != null) {
                connection.disconnect()
                appCtx?.pairingCoordinator?.prepareScan(target, link)
            }
            connection.connect(target) { info ->
                scope.launch { settingsStore.markAttempt(info.profileId, info.errorCode) }
            }
        }
    }

    // —— 扫码：解析结果 → 新建或更新配置并连接 ——
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanned = result.data?.getStringExtra("SCAN_RESULT")?.trim().orEmpty()
            if (scanned.isNotEmpty()) {
                if (DirectLink.isDirectLink(scanned)) {
                    // Direct QR carries the computer's key, so this LAN pairing is not trust-on-first-use.
                    runCatching {
                        val link = DirectLink.parse(scanned)
                        link.profile(profiles.firstOrNull { it.connectionMode == "direct" && normalizeBaseUrl(it.url) == link.address })
                    }.onSuccess { connectTo(it) }.onFailure {
                        Toast.makeText(context, it.message ?: "直连二维码无效，请在电脑重新生成", Toast.LENGTH_LONG).show()
                    }
                    return@rememberLauncherForActivityResult
                }
                if (scanned.startsWith("dshremote:")) {
                    runCatching { PairingLink.parse(scanned) }.onSuccess { scanPreview = it }.onFailure {
                        Toast.makeText(context, it.message ?: "连接信息无效或已过期，请在电脑重新生成二维码", Toast.LENGTH_LONG).show()
                    }
                    return@rememberLauncherForActivityResult
                }
                val existing = profiles.firstOrNull { it.url == scanned }
                val profile = existing
                    ?: HostProfile(
                        id = java.util.UUID.randomUUID().toString(),
                        remark = "",
                        url = scanned,
                    )
                if (runCatching { profile.connectionUrl() }.isSuccess) connectTo(profile)
                else Toast.makeText(context, "请扫描电脑插件生成的连接二维码", Toast.LENGTH_LONG).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scannerLauncher.launch(Intent(context, QrScannerActivity::class.java))
        } else {
            Toast.makeText(context, "需要相机权限才能扫码连接", Toast.LENGTH_SHORT).show()
        }
    }

    fun startScan() {
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    // —— 自动连接 ——
    LaunchedEffect(Unit) {
        settingsStore.ensureMigrated()
        if (!startupPending) return@LaunchedEffect
        onStartupHandled()
        val auto = settingsStore.profiles.first().firstOrNull { it.autoConnect }
        if (auto != null && connection.currentProfile() == null) {
            scope.launch { settingsStore.setActiveProfile(auto.id) }
            connectTo(auto)
        }
    }

    // —— 配对握手结果提示（Toast 需主线程，LaunchedEffect 默认运行于主线程）——
    LaunchedEffect(Unit) {
        val app = context.applicationContext as? DshApplication ?: return@LaunchedEffect
        app.pairingCoordinator.events.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // 首次配对：中转使用临时连接信息，直连使用配对码或电脑确认。
    var pairCode by remember { mutableStateOf("") }
    var pairError by remember { mutableStateOf<String?>(null) }
    var pairBusy by remember { mutableStateOf(false) }
    var pairDismissedFor by remember { mutableStateOf<String?>(null) }
    val awaitingId = appCtx?.pairingCoordinator?.awaitingDecision?.collectAsState(initial = null)?.value
    val waitingId = appCtx?.pairingCoordinator?.waitingForApproval?.collectAsState(initial = null)?.value
    LaunchedEffect(awaitingId) {
        // 决策清除（断线/配对完成）后复位「本会话已关」标记，下次连接可再弹
        if (awaitingId == null) pairDismissedFor = null
        else { pairCode = ""; pairError = null }
    }
    val showPairDialog = awaitingId != null && pairDismissedFor != awaitingId

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
        if (waitingId != null) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("等待电脑确认：请在 DeepSeek Harness 的远程控制页面允许本设备连接。")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (connection.currentProfile()?.connectionMode == "direct") TextButton(onClick = {
                            pairDismissedFor = null
                            appCtx?.pairingCoordinator?.resumePairing()
                        }) { Text("改用配对码") }
                        TextButton(onClick = onDisconnect) { Text("取消") }
                    }
                }
            }
        }
        // 错误横幅（spec §6：错误码 → 原因 → 建议；不可恢复错误标注已停止重连）
        val st = connState
        if (st is DshConnection.State.Error && st.code != null) {
            val code = st.code
            val stopped = code == ConnectionErrorCode.AUTH_FAILED ||
                code == ConnectionErrorCode.VERSION_MISMATCH
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(
                        ErrorMessages.reason(code) + if (stopped) "（已停止自动重连）" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        ErrorMessages.advice(code),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_dsh_desktop), null, Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DSH Remote", Modifier.alignByBaseline(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(" v$versionName", Modifier.alignByBaseline(), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("远程连接 DeepSeek Harness", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onEditHost(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加电脑")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { startScan() }) { Text("扫码连接") }
                    TextButton(onClick = { showPaste = true; pasteError = null }) { Text("粘贴连接信息") }
                }
            }

            // 活跃主机卡片
            activeProfile?.let { p ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("active_computer"),
                        shape = DshShape.card,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            val hostState = if (connection.currentProfile()?.id == p.id) connState else DshConnection.State.Disconnected
                            val connected = hostState as? DshConnection.State.Connected
                            val dotColor = when (hostState) {
                                is DshConnection.State.Connected -> if (p.paired) DshSuccess else DshBrand
                                is DshConnection.State.Error -> MaterialTheme.colorScheme.error
                                is DshConnection.State.Connecting -> DshBrand
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FlowRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(deviceTitle(p), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        ((connected?.takeIf { it.profileId == p.id }?.hostVersion ?: p.lastHostVersion)?.let { "版本 $it" } ?: "版本未知") +
                                            // 插件按 protocol-contract.json 判断：电脑上的 DSH 比插件验证过的版本新，协议可能有变
                                            (if (connected?.takeIf { it.profileId == p.id }?.hostVerified == false) "（插件未验证此版本）" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(Modifier.size(8.dp).background(dotColor, DshShape.pill))
                                    Text(
                                        when (hostState) {
                                            is DshConnection.State.Connected -> if (p.paired && connection.currentProfile()?.paired == true) "已连接" else "待配对"
                                            is DshConnection.State.Connecting -> "连接中…"
                                            is DshConnection.State.Error -> "连接失败"
                                            else -> "未连接"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = dotColor,
                                    )
                                }
                            }
                            if (p.deviceModel.isNotBlank() && p.deviceModel != deviceTitle(p)) Text(
                                p.deviceModel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if ((connected != null || hostState is DshConnection.State.Connecting) && p.paired) {
                                    Button(onClick = onOpenSessions, enabled = connection.currentProfile()?.paired == true) { Text("会话") }
                                } else if (hostState !is DshConnection.State.Connecting && connected == null) {
                                    Button(onClick = { connectTo(p) }) { Text("连接") }
                                }
                                TextButton(onClick = { onEditHost(p.id) }) { Text("编辑") }
                                Spacer(Modifier.weight(1f))
                                if (connected != null) {
                                    TextButton(
                                        onClick = onDisconnect,
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    ) { Text("断开连接") }
                                }
                            }
                            if (connected != null) {
                                if (!p.paired && waitingId == null) TextButton(onClick = {
                                    pairDismissedFor = null; pairCode = ""; pairError = null
                                    appCtx?.pairingCoordinator?.resumePairing()
                                }) { Text("继续配对") }
                            }
                        }
                    }
                }
            }

            if (profiles.any { it.id != activeId } || profiles.isEmpty()) item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "设备记录",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (sortedProfiles.isEmpty()) {
                item {
                    Text(
                        "还没有电脑记录：扫描电脑上的配对二维码，或粘贴临时连接信息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(sortedProfiles.filter { it.id != activeId }, key = { it.id }) { p ->
                    val isActive = p.id == activeId
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = DshShape.card,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        onClick = {
                            // 活跃主机断开/失败态（如退出重进后自动连接未成）时，点卡片即可重连
                            if (!isActive || (
                                    connState !is DshConnection.State.Connected &&
                                        connState !is DshConnection.State.Connecting
                                    )
                            ) connectTo(p)
                        },
                    ) {
                        Row(
                            Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(deviceTitle(p), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(p.lastHostVersion?.let { "版本 $it" } ?: "版本未知",
                                            modifier = Modifier.align(Alignment.CenterVertically),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val probe = probes[p.id]
                                        val probeColor = when (probe?.presence) {
                                            Presence.ONLINE -> DshSuccess
                                            Presence.OFFLINE -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Row(
                                            modifier = Modifier.align(Alignment.CenterVertically),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(Modifier.size(6.dp).background(probeColor, DshShape.pill))
                                            Spacer(Modifier.width(4.dp))
                                            Text(HostProbe.label(probe), style = MaterialTheme.typography.bodySmall, color = probeColor)
                                        }
                                    }
                                    if (isActive) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "使用中",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DshBrand,
                                        )
                                    }
                                    if (p.paired && p.computerId.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "已验证",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DshSuccess,
                                        )
                                    }
                                }
                                if (p.deviceModel != deviceTitle(p)) Text(
                                    if (p.deviceModel.isNotBlank()) p.deviceModel
                                    else "配对后自动记录电脑名称",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                p.lastErrorCode?.let { code ->
                                    runCatching { ConnectionErrorCode.valueOf(code) }.getOrNull()?.let { c ->
                                        Text(
                                            "上次错误：${ErrorMessages.reason(c)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { onEditHost(p.id) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Edit, "编辑设备", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { pendingDelete = p }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Delete, "删除记录", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // 删除设备记录（二次确认）：删活跃主机先断开前台连接
    pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除设备记录") },
                text = { Text("将删除「${deviceTitle(target)}」的连接记录与通道令牌，不影响电脑端数据。") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            if (activeId == target.id) connection.disconnect()
                            settingsStore.deleteProfile(target.id)
                            pendingDelete = null
                        }
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                },
            )
        }

        if (showPaste) {
            AlertDialog(
                onDismissRequest = { showPaste = false; pastedLink = "" },
                title = { Text("粘贴连接信息") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("在电脑的 DeepSeek Harness「远程控制 → 添加 → 服务器连接」中复制临时连接信息。")
                    OutlinedTextField(value = pastedLink, onValueChange = { pastedLink = it.take(4096); pasteError = null },
                        label = { Text("临时连接信息") }, modifier = Modifier.fillMaxWidth())
                    pasteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } },
                confirmButton = { TextButton(onClick = {
                    runCatching { PairingLink.parse(pastedLink) }.onSuccess {
                        scanPreview = it; showPaste = false; pastedLink = ""
                    }.onFailure { pasteError = it.message ?: "连接信息无效或已过期，请在电脑重新生成" }
                }) { Text("继续") } },
                dismissButton = { TextButton(onClick = { showPaste = false; pastedLink = "" }) { Text("取消") } },
            )
        }
        scanPreview?.let { link ->
            AlertDialog(
                onDismissRequest = { scanPreview = null },
                title = { Text("连接电脑") },
                text = { Text("电脑：${link.name}\n服务器：${link.server}\n\n连接后请在电脑确认，允许这台手机控制电脑。") },
                confirmButton = { TextButton(onClick = {
                    runCatching {
                        require(link.expiresAt > System.currentTimeMillis())
                        val existing = profiles.firstOrNull { it.connectionMode == "relay" &&
                            it.relayServerUrl.trimEnd('/') == link.server.trimEnd('/') && it.relayHostId == link.hostId }
                        link.profile(existing)
                    }.onSuccess { connectTo(it, link) }.onFailure {
                        Toast.makeText(context, "连接信息已过期或电脑身份已改变，请重新确认并扫码", Toast.LENGTH_LONG).show()
                    }
                    scanPreview = null
                }) { Text("连接") } },
                dismissButton = { TextButton(onClick = { scanPreview = null }) { Text("取消") } },
            )
        }

        // Relay pairing only accepts QR/link tickets; direct pairing is separate.
        if (showPairDialog) {
            val relayPairing = connection.currentProfile()?.connectionMode == "relay"
            AlertDialog(
                onDismissRequest = { pairDismissedFor = awaitingId },
                title = { Text("首次配对") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeProfile?.deviceModel?.takeIf { it.isNotBlank() }?.let { Text("电脑：$it") }
                        // Same code the computer shows next to the pairing code; a mismatch means someone is in between.
                        val pin = activeProfile?.serverPin?.ifBlank { null }
                            ?: connection.currentProfile()?.serverPin?.ifBlank { null }
                            ?: activeProfile?.id?.let { ServerPinRegistry.observed(it) }
                        if (!relayPairing && pin != null) Text(
                            "安全码 ${SecureChannel.safetyCode(pin)}：请确认与电脑配对页面显示的一致，不一致时请取消。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            if (relayPairing) "请在电脑的 DeepSeek Harness「远程控制 → 添加 → 服务器连接」生成二维码，再扫码或粘贴临时连接信息。中转不再支持手填主机 ID 或六位码配对。"
                            else "请确认连接的电脑，并输入其 DeepSeek Harness「远程控制 → 添加 → 配对码直连」页面显示的 6 位配对码。",
                        )
                        if (!relayPairing) OutlinedTextField(
                            value = pairCode,
                            onValueChange = { v ->
                                pairCode = v.filter { it.isDigit() }.take(6)
                                pairError = null
                            },
                            label = { Text("6 位配对码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        pairError?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = relayPairing || (pairCode.length == 6 && !pairBusy),
                        onClick = {
                            if (relayPairing) {
                                pairDismissedFor = awaitingId
                                startScan()
                            } else
                            scope.launch {
                                pairBusy = true
                                val r = appCtx?.pairingCoordinator?.pairWithCode(pairCode)
                                    ?: PairingCoordinator.PairCodeResult(false, "初始化失败")
                                if (!r.ok) pairError = r.message
                                pairBusy = false
                            }
                        },
                    ) { Text(if (relayPairing) "扫码连接" else if (pairBusy) "配对中…" else "配对") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pairDismissedFor = awaitingId
                        if (relayPairing) showPaste = true
                        else scope.launch { appCtx?.pairingCoordinator?.chooseLegacyHandshake() }
                    }) { Text(if (relayPairing) "粘贴连接信息" else "等待电脑确认") }
                },
            )
        }
    }
}
