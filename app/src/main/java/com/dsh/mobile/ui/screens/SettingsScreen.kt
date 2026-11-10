package com.dsh.mobile.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.BackHandler
import com.dsh.mobile.ui.components.*
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dsh.mobile.data.BackgroundImages
import com.dsh.mobile.data.ApkInstaller
import com.dsh.mobile.data.AppearanceConfig
import com.dsh.mobile.data.DshConnection
import com.dsh.mobile.data.HttpPairingRpc
import com.dsh.mobile.data.McpPrompt
import com.dsh.mobile.data.McpPromptServer
import com.dsh.mobile.data.McpResource
import com.dsh.mobile.data.McpResourceServer
import com.dsh.mobile.data.McpServer
import com.dsh.mobile.data.NotificationProblem
import com.dsh.mobile.data.notificationProblems
import com.dsh.mobile.data.OkHttpClientFactory
import com.dsh.mobile.data.ReleaseInfo
import com.dsh.mobile.data.sessionTitleOf
import com.dsh.mobile.data.computerScope
import com.dsh.mobile.data.shortSessionId
import com.dsh.mobile.data.SettingsStore
import com.dsh.mobile.data.Sha3
import com.dsh.mobile.data.VaultStatus
import com.dsh.mobile.data.UpdateChecker
import com.dsh.mobile.service.DshConnectionService
import com.dsh.mobile.ui.theme.DshBrand
import com.dsh.mobile.ui.theme.DshError
import com.dsh.mobile.ui.theme.DshSuccess
import com.dsh.mobile.ui.theme.ThemeRegistry
import com.dsh.mobile.ui.theme.ThemeRepository
import com.dsh.mobile.ui.theme.ThemeStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    connection: DshConnection,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenMarket: () -> Unit,
    onDisconnect: () -> Unit,
    onConnections: () -> Unit = {},
) {
    val connState by connection.state.collectAsState()
    val remoteReady = connState is DshConnection.State.Connected && connection.currentProfile()?.paired == true
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val categoryScroll = rememberScrollState()
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    var appearance by remember { mutableStateOf(AppearanceConfig()) }
    var backgroundNotify by remember { mutableStateOf(true) }
    var notifyApprovals by remember { mutableStateOf(true) }
    var notifyCompletion by remember { mutableStateOf(true) }
    var themeMode by remember { mutableStateOf("blue") }
    var autoModel by remember { mutableStateOf(false) }
    var uiFont by remember { mutableStateOf("") }
    var codeFont by remember { mutableStateOf("") }
    var fontPicker by remember { mutableStateOf<String?>(null) } // "ui" / "code"
    var showLicenses by remember { mutableStateOf(false) }
    var notifGranted by remember { mutableStateOf(true) }
    var themeImportError by remember { mutableStateOf<String?>(null) }
    var showAppearanceDetail by rememberSaveable { mutableStateOf(false) }
    var showThemePicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(category, showAppearanceDetail) { categoryScroll.scrollTo(0) }
    fun goBack() {
        when {
            showAppearanceDetail -> showAppearanceDetail = false
            category == "归档会话" || category == "默认预设" -> category = "会话"
            category != null -> category = null
            else -> onBack()
        }
    }
    BackHandler(enabled = category != null || showAppearanceDetail) { goBack() }
    var biometricEnabled by remember { mutableStateOf(false) }
    var deviceName by remember { mutableStateOf("") }
    val customThemes by ThemeRepository.themes.collectAsState()
    val biometricAvailable = remember {
        androidx.biometric.BiometricManager.from(context)
            .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        notifGranted = g
        if (g) com.dsh.mobile.DshApplication.ensureNotificationChannels(context)
    }
    val themePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        themeImportError = null
        scope.launch {
            val imported = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes == null || bytes.isEmpty()) null to false
                else ThemeRepository.importPayload(bytes, context) to true
            }
            val (def, readable) = imported
            if (!readable) {
                themeImportError = "无法读取主题文件"
            } else if (def == null) {
                themeImportError = "主题格式无效（zip 包需含 theme.json；JSON 需完整色板，格式见 docs/theme-package-format.md）"
            } else {
                themeMode = def.id
                settingsStore.setThemeMode(def.id)
            }
        }
    }

    LaunchedEffect(Unit) {
        appearance = settingsStore.appearance.first()
        backgroundNotify = settingsStore.backgroundNotify.first()
        notifyApprovals = settingsStore.notifyApprovals.first()
        notifyCompletion = settingsStore.notifyCompletion.first()
        themeMode = settingsStore.themeMode.first()
        autoModel = settingsStore.autoModel.first()
        uiFont = settingsStore.uiFont.first()
        codeFont = settingsStore.codeFont.first()
        biometricEnabled = settingsStore.biometricLockEnabled.first()
        deviceName = settingsStore.deviceName.first()
        notifGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun setBackgroundNotify(enabled: Boolean) {
        backgroundNotify = enabled
        scope.launch { settingsStore.setBackgroundNotify(enabled) }
        if (enabled) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DshConnectionService::class.java))
            }
        } else {
            runCatching { context.stopService(Intent(context, DshConnectionService::class.java)) }
        }
    }

    fun setNotifyApprovals(on: Boolean) {
        notifyApprovals = on
        scope.launch { settingsStore.setNotifyApprovals(on) }
    }

    fun setNotifyCompletion(on: Boolean) {
        notifyCompletion = on
        scope.launch { settingsStore.setNotifyCompletion(on) }
    }

    /** 跳系统通知设置：给了渠道就打开该渠道（铃声、横幅、勿扰），否则打开本应用的通知设置 */
    fun openNotificationSettings(channelId: String?) {
        val intent = if (channelId != null) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openChannelSettings() = openNotificationSettings(null)

    // 通知诊断：每次回到本页重新读系统里的实际设置（用户可能刚在系统设置里改过）
    var notifProblems by remember { mutableStateOf<List<NotificationProblem>>(emptyList()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        notifGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        com.dsh.mobile.DshApplication.ensureNotificationChannels(context)
        notifProblems = notificationProblems(
            permissionGranted = notifGranted,
            appNotificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
            channels = com.dsh.mobile.DshApplication.channelStates(context),
        )
        onPauseOrDispose { }
    }

    fun persist(next: AppearanceConfig) {
        appearance = next
        scope.launch { settingsStore.saveAppearanceControls(next) }
    }

    var importingBackground by remember { mutableStateOf(false) }
    fun replaceBackground(uri: Uri?) {
        scope.launch {
            importingBackground = true
            try {
                val next = withContext(NonCancellable + Dispatchers.IO) {
                    val retained = uri?.let { BackgroundImages.retain(context, it) }
                    val previous = try {
                        settingsStore.replaceBackgroundUri(retained?.toString())
                    } catch (e: Exception) {
                        if (retained?.scheme == "file") BackgroundImages.release(context, retained)
                        throw e
                    }
                    if (previous != null && previous != retained?.toString()) BackgroundImages.release(context, Uri.parse(previous))
                    retained
                }
                appearance = appearance.copy(bgUri = next?.toString())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, "无法保存背景图", Toast.LENGTH_SHORT).show()
            } finally {
                importingBackground = false
            }
        }
    }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.data?.let(::replaceBackground)
    }
    fun pickBackground() {
        if (importingBackground) return
        val picker = ActivityResultContracts.PickVisualMedia()
        val intent = if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            picker.createIntent(context, PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else Intent(Intent.ACTION_PICK).setType("image/*")
        try {
            bgPicker.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到图库", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (showAppearanceDetail) "更多外观设置" else category ?: "设置",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = ::goBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(categoryScroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (category == null) {
                SettingsGroup {
                    SettingsRow("外观", { category = "外观" }, icon = Icons.Default.Palette,
                        value = ThemeRegistry.available(customThemes).firstOrNull { it.id == themeMode }?.name)
                    SettingsRow("会话", { category = "会话" }, icon = Icons.Default.ChatBubbleOutline)
                    SettingsRow("提醒", { category = "提醒" }, icon = Icons.Default.NotificationsNone)
                    SettingsRow("安全", { category = "安全" }, icon = Icons.Default.Shield)
                }
                SettingsGroup {
                    SettingsRow("连接", { category = "连接" }, icon = Icons.Default.Devices)
                    SettingsRow("保险库", { category = "保险库" }, icon = Icons.Default.Lock)
                    SettingsRow("MCP 服务", { category = "MCP 服务" }, icon = Icons.Default.Extension)
                }
                SettingsGroup {
                    SettingsRow("关于", { category = "关于" }, icon = Icons.Default.Info)
                }
            }
            if (category == "会话") {
                SettingsGroup {
                    SettingsRow("默认预设", { category = "默认预设" })
                    SettingsToggle("自适应模型", "按任务难度自动选 Flash / Pro（短问答→Flash，复杂任务→Pro）",
                        autoModel, { autoModel = it; scope.launch { settingsStore.setAutoModel(it) } })
                    SettingsRow("归档会话", { category = "归档会话" }, icon = Icons.Default.Archive)
                }
            }
            if (category == "默认预设") {
                if (remoteReady) key(connState) {
                    val computer = connection.currentProfile().computerScope()
                    val saved by remember(computer) { settingsStore.defaultPreset(computer) }.collectAsState(initial = null)
                    var reload by remember { mutableIntStateOf(0) }
                    val roster = presetRoster(connection, true, reload)
                    val presets = roster?.getOrNull().orEmpty()
                    fun save(id: String) { scope.launch { settingsStore.setDefaultPreset(computer, id) } }
                    SettingsGroup {
                        val followsComputer = saved == "" || (roster?.isSuccess == true && saved != null && presets.none { it.id == saved })
                        SettingsRow("跟随电脑默认", { save("") }, value = if (followsComputer) "已选择" else null,
                            summary = presets.firstOrNull { it.isDefault }?.label)
                        for (preset in presets) {
                            SettingsRow(preset.label, { save(preset.id) }, value = if (saved == preset.id) "已选择" else null,
                                summary = preset.description?.takeIf { it.isNotBlank() })
                        }
                    }
                    if (roster == null) CircularProgressIndicator()
                    else if (roster.isFailure) TextButton(onClick = { reload++ }) { Text("重试") }
                } else Text("请先连接电脑")
            }
            if (category == "归档会话") {
                val connected = connState as? DshConnection.State.Connected
                if (remoteReady && connected != null) {
                    key(connected.profileId, connected.generation) {
                        ArchivedSessionsSettings(connection, connected)
                    }
                } else {
                    Text("请先连接电脑")
                }
            }
            if (category == "外观" && !showAppearanceDetail) {
                SettingsGroup {
                    SettingsRow("主题模式", { showThemePicker = true },
                        value = ThemeRegistry.available(customThemes).firstOrNull { it.id == themeMode }?.name)
                    SettingsRow("界面字体", { fontPicker = "ui" }, value = UI_FONTS.firstOrNull { it.second == uiFont }?.first ?: "默认")
                    SettingsRow("代码字体", { fontPicker = "code" }, value = CODE_FONTS.firstOrNull { it.second == codeFont }?.first ?: "默认")
                }
                SettingsGroup {
                    SettingsRow(if (appearance.bgUri != null) "更换背景图" else "选择背景图", ::pickBackground)
                    if (appearance.bgUri != null) SettingsRow("移除", { if (!importingBackground) replaceBackground(null) })
                    SettingsRow("更多外观设置", { showAppearanceDetail = true })
                }
                SettingsGroup {
                    SettingsRow("导入主题 (zip/json)", { themePicker.launch(arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream")) })
                    SettingsRow("主题市场", onOpenMarket)
                }
                themeImportError?.let { Text(it, color = DshError, style = MaterialTheme.typography.bodySmall) }
                if (customThemes.isNotEmpty()) SettingsGroup {
                    Column(Modifier.padding(16.dp)) {
                        customThemes.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val preview = remember(t.id) {
                                    runCatching {
                                        ThemeStore(context).previewFile(t.id)?.let { BitmapFactory.decodeFile(it.absolutePath) }
                                    }.getOrNull()
                                }
                                if (preview != null) {
                                    Image(
                                        preview.asImageBitmap(), null,
                                        Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Box(
                                        Modifier.size(12.dp).clip(CircleShape).background(t.colors.brand),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    t.name + (t.version?.let { " v" + it } ?: "") + "（自定义）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        if (themeMode == t.id) {
                                            themeMode = "blue"
                                            scope.launch { settingsStore.setThemeMode("blue") }
                                        }
                                        ThemeRepository.remove(t.id, context)
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Delete, "删除主题",
                                        Modifier.size(16.dp), tint = DshError,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (category == "外观" && showAppearanceDetail) {
                Text("一键预设", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsGroup {
                    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            BgPreset("通透玻璃", 80f, 4f, 0.2f, 1.1f),
                            BgPreset("电影质感", 40f, 0f, 0.55f, 1.2f),
                            BgPreset("纯净原图", 95f, 0f, 0.08f, 1f),
                            BgPreset("柔和梦境", 65f, 12f, 0.25f, 1.05f),
                        ).forEach { (name, glass, blur, dim, saturate) ->
                            AssistChip(onClick = { persist(appearance.copy(bgOpacity = 1f, bgBlur = blur,
                                bgDim = dim, bgSaturate = saturate, panelGlass = glass)) }, label = { Text(name) })
                        }
                    }
                }
                SettingsGroup {
                    SettingsSlider("图像不透明度", "${(appearance.bgOpacity * 100).toInt()}%", appearance.bgOpacity, 0.05f..1f) { persist(appearance.copy(bgOpacity = it)) }
                    SettingsSlider("模糊", "${appearance.bgBlur.toInt()}px", appearance.bgBlur, 0f..30f) { persist(appearance.copy(bgBlur = it)) }
                    SettingsSlider("蒙层浓度", "${(appearance.bgDim * 100).toInt()}%", appearance.bgDim, 0f..0.85f) { persist(appearance.copy(bgDim = it)) }
                    SettingsSlider("饱和度", "${(appearance.bgSaturate * 100).toInt()}%", appearance.bgSaturate, 0.5f..1.5f) { persist(appearance.copy(bgSaturate = it)) }
                }
                SettingsGroup {
                    SettingsSlider("面板通透", "${appearance.panelGlass.toInt()}%", appearance.panelGlass, 0f..100f) { persist(appearance.copy(panelGlass = it)) }
                    SettingsSlider("屏幕亮度", "${(appearance.brightness * 100).toInt()}%", appearance.brightness, 0.4f..1f) { persist(appearance.copy(brightness = it)) }
                }

            }
            if (category == "提醒") {
                SettingsGroup {
                    SettingsToggle("后台审批提醒", "App 在后台时，桌面端请求审批/确认会推送横幅通知（通知栏常驻一个连接小图标）",
                        backgroundNotify, ::setBackgroundNotify)
                    SettingsToggle("审批与确认提醒", "桌面端请求工具审批/确认时横幅提醒（需上方总开关开启）",
                        notifyApprovals, ::setNotifyApprovals)
                    SettingsToggle("任务完成提醒", "会话任务执行完成时提醒（独立系统渠道，可单独调铃声/勿扰）",
                        notifyCompletion, ::setNotifyCompletion)
                }
                SettingsGroup {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(if (notifGranted) "通知权限：已开启" else "通知权限：未开启（审批/确认/完成不会弹通知）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (notifGranted) DshSuccess else MaterialTheme.colorScheme.error)
                        if (!notifGranted) TextButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("授权") }
                        // 渠道诊断：权限开着但系统把渠道改成静音/不弹横幅时，指出具体哪一项
                        if (notifGranted) {
                            if (notifProblems.isEmpty()) {
                                Text("提醒渠道：横幅和提示音正常",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp))
                            } else {
                                notifProblems.forEach { problem ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                        Text(problem.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.weight(1f))
                                        TextButton(onClick = { openNotificationSettings(problem.channelId) }) { Text("去设置") }
                                    }
                                }
                            }
                        }
                    }
                    SettingsRow("通知渠道设置", ::openChannelSettings)
                }
            }
            if (category == "安全") {
                SettingsGroup {
                    SettingsToggle("生物锁", if (biometricAvailable) "回到前台需生物识别验证后才能操控电脑" else "此设备无可用生物识别",
                        biometricAvailable && biometricEnabled, {
                            biometricEnabled = it
                            scope.launch { settingsStore.setBiometricLock(it) }
                        }, enabled = biometricAvailable)
                }
                SettingsGroup {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = {
                            deviceName = it
                            scope.launch { settingsStore.setDeviceName(it) }
                        },
                        label = { Text("设备名称") },
                        placeholder = { Text(Build.MODEL) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "配对其他设备时显示的名称（留空 = 手机型号）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    }
                }
            }
            if (category == "连接") {
            SettingsGroup {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (connState) {
                                is DshConnection.State.Connected -> Icons.Default.CheckCircle
                                is DshConnection.State.Error -> Icons.Default.ErrorOutline
                                else -> Icons.Default.CloudOff
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = when (connState) {
                                is DshConnection.State.Connected -> DshSuccess
                                is DshConnection.State.Error -> DshError
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when (connState) {
                                is DshConnection.State.Connected -> "已连接 " + connection.currentProfile()?.let { it.remark.ifBlank { it.deviceModel } }.orEmpty()
                                is DshConnection.State.Error -> "连接错误"
                                is DshConnection.State.Connecting -> "连接中…"
                                else -> "未连接"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connState !is DshConnection.State.Disconnected,
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("断开连接")
                    }
                }
            }

            Text("连接说明", style = MaterialTheme.typography.titleMedium)
            SettingsGroup {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "1. IP 直连：选择「IP 直连」，填写电脑的 DeepSeek Harness 地址（如 192.168.1.100:8787）。手机需要能访问该地址。\n" +
                            "2. 服务器：电脑插件填写 HTTPS 服务器地址并保存。开启「允许连接」后，电脑身份自动注册。\n" +
                            "3. 电脑点击「添加 → 服务器连接」，手机首页扫码或粘贴临时连接信息，再在电脑确认。每台手机分别配对，不需要手填主机 ID 或凭证。\n" +
                            "4. 配对码直连：电脑打开「远程控制 → 添加 → 配对码直连」，设备在「添加电脑 → 配对码直连」自动搜索，选择电脑并输入 6 位码。需在同一局域网；搜索不到时可用 IP 直连或扫码。旧手动服务器记录需删除后重新扫码。\n" +
                            "5. 服务器连接必须校验 HTTPS 证书，自签名证书需导入 CA。服务器能读取转发内容，请使用可信服务器；电脑需保持在线。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.6,
                    )
                }
            }

            Text("远程控制", style = MaterialTheme.typography.titleMedium)
            if (remoteReady) M4TokenSection(connection, settingsStore, scope)
            else OutlinedButton(onClick = onConnections) { Text("连接") }

            }
            if (category == "关于") {
            SettingsGroup {
                SettingsRow("Pro 订阅（趣味彩蛋）", onUpgrade, value = "¥0.00", icon = Icons.Default.WorkspacePremium)
            }
            Text("关于应用", style = MaterialTheme.typography.titleMedium)
            SettingsGroup {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 版本号从系统包信息读取，避免硬编码不同步
                    val versionName = remember {
                        runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull() ?: "?"
                    }
                    SettingInfoRow("应用", "DSH Remote v$versionName")
                    UpdateSection(currentVersion = versionName, context = context, scope = scope)
                    SettingInfoRow("更新地址", "WILSONowo/dsh-remote", onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(UpdateChecker.RELEASES_URL)))
                    })
                    SettingInfoRow("协议", "dsh-api (client-request / WS+SSE mux)")
                    SettingInfoRow("后端", "DeepSeek Harness")
                    SettingInfoRow("主题", "DeepSeek Harness 浅色/深色 · 背景图/蒙层/面板通透可调")
                }
            }

            // 保险库（dsh-encrypt 联动）：手机端输入密码解锁 PC 端凭证保险库——与 web 端走同一路由
            // （/api/credentials.unlock），密码本地 SHA3-256 后仅上传摘要，明文不上行；
            // 解锁为进程全局状态，手机解锁后 PC web 端同步解锁。未装 dsh-encrypt → 「不可用」。
            }
            if (category == "保险库") {
            if (!remoteReady) {
                Text("未连接", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onConnections) { Text("连接") }
            } else {
            Text("保险库", style = MaterialTheme.typography.titleMedium)
            SettingsGroup {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val connected = connState is DshConnection.State.Connected
                    var vault by remember { mutableStateOf<VaultStatus?>(null) }
                    var vaultLoaded by remember { mutableStateOf(false) }
                    var rememberedDigest by remember { mutableStateOf<String?>(null) }
                    var showUnlockDialog by remember { mutableStateOf(false) }
                    LaunchedEffect(connected) {
                        if (connected) {
                            vault = connection.vaultStatus()
                            vaultLoaded = true
                            rememberedDigest = settingsStore.vaultDigest.first()
                        } else {
                            vault = null
                            vaultLoaded = false
                        }
                    }
                    when {
                        !connected -> Text(
                            "连接后可查看保险库状态",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        !vaultLoaded -> Text(
                            "加载中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        vault == null -> Text(
                            "不可用（电脑未安装 dsh-encrypt 插件）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        vault != null && vault!!.locked -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (vault!!.lockoutRetryAfterMs > 0)
                                    "已锁定（防爆破冷却 ${vault!!.lockoutRetryAfterMs / 1000}s）"
                                else "已锁定 · 凭证不可用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DshError,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { showUnlockDialog = true }) { Text("解锁") }
                        }
                        vault != null && vault!!.encrypted -> Text(
                            "已解锁",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DshSuccess,
                        )
                        else -> Text(
                            "未设置密码（明文存储）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showUnlockDialog) {
                        VaultUnlockDialog(
                            hasRemembered = rememberedDigest != null,
                            onDismiss = { showUnlockDialog = false },
                            onUnlock = { password, remember ->
                                scope.launch {
                                    val digest = rememberedDigest?.takeIf { password.isBlank() }
                                        ?: Sha3.digest256Hex(password)
                                    connection.vaultUnlock(digest)
                                    // 成功/失败统一以新一轮 status 呈现（密码错会触发防爆破计数，
                                    // 不做弹窗报错以免诱导连续尝试；冷却剩余由 lockout 字段带出）
                                    settingsStore.setVaultDigest(if (remember) digest else null)
                                    vault = connection.vaultStatus()
                                    vaultLoaded = true
                                    showUnlockDialog = false
                                }
                            },
                        )
                    }
                }
            }

            // MCP 服务（S5）：PC 插件 mcp/list 枚举的 MCP 服务与工具（serverName + 工具数徽章；
            // 上游无连接态 API，status 恒 unknown → 显示「连接状态未知」）；未连接/空 → 「无 MCP 服务」
            }
            }
            if (category == "MCP 服务") {
            if (!remoteReady) {
                Text("未连接", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onConnections) { Text("连接") }
            } else {
            Text("MCP 服务", style = MaterialTheme.typography.titleMedium)
            SettingsGroup {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val connected = connState is DshConnection.State.Connected
                    var mcpServers by remember { mutableStateOf<List<McpServer>?>(null) }
                    LaunchedEffect(connected) {
                        mcpServers = if (connected) connection.mcpList() else emptyList()
                    }
                    val servers = mcpServers
                    when {
                        !connected -> Text(
                            "无 MCP 服务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        servers == null -> Text(
                            "加载中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        servers.isEmpty() -> Text(
                            "无 MCP 服务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> servers.forEach { s ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    s.serverName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        "${s.tools.size} 工具",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "连接状态未知",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Text("MCP 资源 / 提示词", style = MaterialTheme.typography.titleMedium)
            SettingsGroup {
                val connected = connState is DshConnection.State.Connected
                var resList by remember { mutableStateOf<List<McpResourceServer>?>(null) }
                var promList by remember { mutableStateOf<List<McpPromptServer>?>(null) }
                LaunchedEffect(connected) {
                    if (connected) {
                        resList = connection.mcpResources()
                        promList = connection.mcpPrompts()
                    } else {
                        resList = emptyList(); promList = emptyList()
                    }
                }
                val resources = resList ?: emptyList()
                val prompts = promList ?: emptyList()
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!connected) {
                        Text("无", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val resText = resources.joinToString("\n") { rs ->
                            rs.resources.joinToString("\n") { x ->
                                "${rs.serverName}: ${x.name}" + if (x.description.isNotBlank()) " — ${x.description}" else ""
                            }
                        }.ifBlank { "无资源能力" }
                        Text(resText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val promText = prompts.joinToString("\n") { ps ->
                            ps.prompts.joinToString("\n") { x ->
                                "${ps.serverName}: ${x.name}" + if (x.description.isNotBlank()) " — ${x.description}" else ""
                            }
                        }.ifBlank { "无提示词能力" }
                        Text(promText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            }
            }
            if (category == "关于") {
            Text("开发人员", style = MaterialTheme.typography.titleMedium)
            SettingsGroup {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingInfoRow("上游作者", "残星会·虚质空间分部 达妮娅同学、Yauntyours")
                    SettingInfoRow("上游作者 B站", "最喜欢达妮娅了")
                    SettingInfoRow("上游仓库", "Hyna-hla/dsh-remote")
                    SettingInfoRow("当前维护者", "WILSONowo")
                    SettingInfoRow("当前仓库", "WILSONowo/dsh-remote")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "DSH Remote 是 DeepSeek Harness 的非官方客户端。代码按 MIT 许可发布，保留上游版权声明：Copyright (c) 2026 Aemeath。" +
                            "鲸鱼娘皮肤素材按 CC BY-NC-SA 4.0 许可使用，署名链：上善 → zipzip → Small-tailqwq（dsh-external/dsh-deep-whale）。素材许可与代码许可分别适用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5,
                    )
                    TextButton(onClick = { showLicenses = true }) { Text("查看许可与素材署名") }
                }
            }
            }
        }
    }

    if (showLicenses) {
        val notices = remember {
            listOf("licenses/MIT.txt", "licenses/maid-atelier-NOTICE.txt").joinToString("\n\n") { name ->
                context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("许可与素材署名") },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(notices, modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("关闭") } },
        )
    }

    if (showThemePicker) SettingsChoiceDialog(
        title = "主题模式", options = ThemeRegistry.available(customThemes).map { it.name to it.id }, current = themeMode,
        onDismiss = { showThemePicker = false }, onPick = {
            themeMode = it; scope.launch { settingsStore.setThemeMode(it) }; showThemePicker = false
        })

    // 字体选择对话框（UI 字体 / 代码字体）
    fontPicker?.let { kind ->
        SettingsChoiceDialog(
            title = if (kind == "ui") "界面字体" else "代码字体",
            options = if (kind == "ui") UI_FONTS else CODE_FONTS,
            current = if (kind == "ui") uiFont else codeFont,
            fontForOption = { family ->
                if (family.isEmpty()) null else runCatching {
                    androidx.compose.ui.text.font.FontFamily(android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL))
                }.getOrNull()
            },
            onDismiss = { fontPicker = null },
            onPick = { family ->
                if (kind == "ui") {
                    uiFont = family
                    scope.launch { settingsStore.setUiFont(family) }
                } else {
                    codeFont = family
                    scope.launch { settingsStore.setCodeFont(family) }
                }
                fontPicker = null
            },
        )
    }
}

/** 界面字体选项（Android 系统字体族；label → 族名，"" = 主题默认） */
private val UI_FONTS = listOf(
    "默认（主题字体）" to "",
    "无衬线（标准黑体）" to "sans-serif",
    "细黑体" to "sans-serif-light",
    "中黑体" to "sans-serif-medium",
    "衬线（宋体感）" to "serif",
    "圆体（casual）" to "casual",
    "窄体（condensed）" to "sans-serif-condensed",
    "等宽（monospace）" to "monospace",
)

/** 代码字体选项（等宽族；"" = Monospace） */
private val CODE_FONTS = listOf(
    "默认（Monospace）" to "",
    "衬线等宽（serif-monospace）" to "serif-monospace",
    "窄等宽（condensed）" to "sans-serif-condensed",
    "系统无衬线" to "sans-serif",
)

/** M4：令牌轮换/吊销 + 审计日志（需已连接主机；调用插件 /token/rotate|revoke|audit） */
@Composable
private fun M4TokenSection(
    connection: DshConnection,
    settingsStore: SettingsStore,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val profiles by settingsStore.profiles.collectAsState(initial = emptyList())
    val activeId by settingsStore.activeProfileId.collectAsState(initial = null)
    val profile = profiles.firstOrNull { it.id == activeId }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var showAudit by remember { mutableStateOf(false) }
    var auditItems by remember { mutableStateOf<List<HttpPairingRpc.AuditEvent>>(emptyList()) }

    fun rpc(): HttpPairingRpc? = profile?.let { HttpPairingRpc(connection, OkHttpClientFactory.build(it).first) }

    SettingsGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("令牌管理", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                if (profile == null)
                    "连接主机后可用：轮换会立即使旧 token 失效；吊销只撤销本手机，其他已配对设备不受影响。"
                else "目标：${profile.remark.ifBlank { profile.deviceModel.ifBlank { "未命名设备" } }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true; result = null
                            result = try {
                                val r3 = rpc()?.rotateToken()
                                if (r3?.ok == true && r3.token?.matches(Regex("[a-f0-9]{64}")) == true && profile != null) {
                                    val updated = profile.copy(channelToken = r3.token, paired = true)
                                    settingsStore.upsertProfile(updated)
                                    connection.syncCurrentProfile(updated)
                                    "已轮换并保存本手机令牌，其他设备不受影响"
                                }
                                else "轮换失败：" + (r3?.error ?: "未连接")
                            } catch (e: Exception) {
                                "轮换失败：" + (e.message ?: e.javaClass.simpleName)
                            } finally { busy = false }
                        }
                    },
                    enabled = !busy && profile != null,
                ) { Text("轮换令牌") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true; result = null
                            result = try {
                                val r3 = rpc()?.revokeToken()
                                if (r3?.ok == true && profile != null) {
                                    val updated = profile.copy(channelToken = "", paired = false)
                                    settingsStore.upsertProfile(updated)
                                    connection.syncCurrentProfile(updated)
                                    "已撤销本手机，请重新配对"
                                }
                                else "吊销失败：" + (r3?.error ?: "未连接")
                            } catch (e: Exception) {
                                "吊销失败：" + (e.message ?: e.javaClass.simpleName)
                            } finally { busy = false }
                        }
                    },
                    enabled = !busy && profile != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("撤销本手机") }
            }
            result?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = {
                scope.launch { auditItems = rpc()?.audit() ?: emptyList(); showAudit = !showAudit }
            }) { Text(if (showAudit) "收起审计日志" else "查看审计日志") }
            if (showAudit) {
                val auditText = if (auditItems.isEmpty()) "暂无记录"
                else auditItems.joinToString("\n") { e ->
                    val t = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(e.ts))
                    "$t  ${e.action}  ${e.deviceId ?: "-"}  ${e.ip ?: "-"}"
                }
                Text(
                    auditText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class BgPreset(
    val name: String,
    val glass: Float,
    val blur: Float,
    val dim: Float,
    val saturate: Float,
)

@Composable
private fun SettingInfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.widthIn(max = 120.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            maxLines = if (onClick != null) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 应用内检查更新：
 *   idle → checking → latest / found → downloading → downloaded →（系统安装器）
 * 检查走 GitHub Releases latest API；下载取 release 的 debug 主包资产（无主包时回退 -min 精简包；流式带进度）；
 * 安装经 FileProvider 交给系统安装器（用户确认，无需额外权限）。
 */
@Composable
private fun UpdateSection(
    currentVersion: String,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var phase by remember { mutableStateOf("idle") } // idle/checking/latest/found/downloading/downloaded/error
    var info by remember { mutableStateOf<ReleaseInfo?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var err by remember { mutableStateOf<String?>(null) }
    var apkFile by remember { mutableStateOf<File?>(null) }

    val check: () -> Unit = {
        phase = "checking"
        err = null
        scope.launch {
            val r = UpdateChecker.checkLatest()
            if (r == null) {
                phase = "error"; err = "无法连接更新服务器（GitHub Releases）"
            } else if (UpdateChecker.isNewer(UpdateChecker.releaseAppVersion(r), currentVersion)) {
                info = r; phase = "found"
            } else {
                phase = "latest"
            }
        }
    }
    val download: () -> Unit = {
        val asset = UpdateChecker.pickApk(info?.assets ?: emptyList())
        if (asset == null) {
            phase = "error"; err = "发布中没有可安装的 APK"
        } else {
            phase = "downloading"; progress = 0f
            val target = File(context.cacheDir, "update/" + asset.name)
            scope.launch {
                try {
                    val expected = info?.let { UpdateChecker.expectedSha256(it, asset) }
                    UpdateChecker.downloadApk(asset.browserDownloadUrl, target, expected) { p -> progress = p }
                    apkFile = target
                    phase = "downloaded"
                } catch (e: Exception) {
                    phase = "error"; err = e.message
                }
            }
        }
    }
    val install: () -> Unit = {
        val f = apkFile
        if (f != null && f.exists()) {
            // 多策略安装：系统安装器（INSTALL_PACKAGE / VIEW 双 intent）→ PackageInstaller 会话兜底
            val error = ApkInstaller.install(context, f)
            if (error != null) {
                phase = "error"
                err = error
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "更新",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (phase) {
            "idle" -> TextButton(onClick = check) { Text("检查更新") }
            "checking" -> Text("检查中…", style = MaterialTheme.typography.bodyMedium)
            "latest" -> Text("已是最新版本", style = MaterialTheme.typography.bodyMedium, color = DshSuccess)
            "found" -> Column(horizontalAlignment = Alignment.End) {
                Text(
                    "发现新版本 v${info?.tagName?.removePrefix("v")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DshBrand,
                )
                // Release 说明摘录：纯文本化 + 去链接标记，最多 6 行（完整日志看仓库）
                info?.body?.takeIf { it.isNotBlank() }?.let { body ->
                    Text(
                        body
                            .replace(Regex("""\[([^\]]+)]\(([^)]+)\)"""), "$1")
                            .replace('#', '№')
                            .replace("**", "")
                            .lineSequence()
                            .map(String::trim)
                            .filter { it.isNotEmpty() }
                            .take(6)
                            .joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 260.dp),
                    )
                }
                TextButton(onClick = download) { Text("下载更新") }
            }
            "downloading" -> Column(horizontalAlignment = Alignment.End) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(120.dp).height(4.dp),
                )
                Text(
                    "下载中 ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            "downloaded" -> Column(horizontalAlignment = Alignment.End) {
                Text(
                    "下载完成 v${info?.tagName?.removePrefix("v")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DshSuccess,
                )
                TextButton(onClick = install) { Text("安装") }
            }
            else -> Column(horizontalAlignment = Alignment.End) {
                Text(
                    err ?: "更新失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = DshError,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = check) { Text("重试") }
            }
        }
    }
}

/** 保险库解锁对话框：密码留空 = 使用已记住的摘要直接解锁。 */
@Composable
private fun VaultUnlockDialog(
    hasRemembered: Boolean,
    onDismiss: () -> Unit,
    onUnlock: (password: String, remember: Boolean) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("解锁保险库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasRemembered) Text(
                    "已记住密码——输入框留空直接解锁",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = remember, onCheckedChange = { remember = it })
                    Text("记住密码（本机加密存储）", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "密码仅在本机求 SHA3-256 摘要后上传（与 web 端一致），解锁后电脑同步生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUnlock(password, remember) },
                enabled = password.isNotBlank() || hasRemembered,
            ) { Text("解锁") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ArchivedSessionsSettings(connection: DshConnection, target: DshConnection.State.Connected) {
    val scope = rememberCoroutineScope()
    val directory = connection.directory
    val workspace by directory.workspace.state.collectAsState()
    val sessions by directory.sessions.state.collectAsState()
    val ids = workspace.value?.archivedSessionIds
    val titles = remember(sessions.value) { sessions.value.orEmpty().associate { it.sessionId to sessionTitleOf(it) } }
    var mutationError by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(directory) { directory.workspace.load(); directory.sessions.load() }
    val error = mutationError ?: workspace.error?.message ?: sessions.error?.message
    error?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = {
            mutationError = null
            directory.workspace.load(force = true)
            directory.sessions.load(force = true)
        }) { Text("重试") }
    }
    when {
        ids == null && error == null -> CircularProgressIndicator()
        ids?.isEmpty() == true -> Text("暂无归档会话")
        !ids.isNullOrEmpty() -> SettingsGroup {
            for (id in ids.orEmpty()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(titles[id] ?: "会话 ${shortSessionId(id)}", Modifier.weight(1f))
                    TextButton(enabled = restoring == null, onClick = {
                        if (connection.state.value != target || restoring != null) return@TextButton
                        restoring = id
                        scope.launch {
                            try {
                                if (connection.state.value != target) return@launch
                                connection.restoreSession(id)
                                mutationError = null
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                mutationError = e.message ?: "恢复失败"
                            } finally { restoring = null }
                        }
                    }) { Text(if (restoring == id) "恢复中…" else "恢复") }
                }
            }
        }
    }
}
