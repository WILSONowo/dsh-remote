package com.dsh.mobile.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dsh.mobile.data.computerScope
import android.net.Uri
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.dsh.mobile.DshApplication
import com.dsh.mobile.data.ApprovalCenter
import com.dsh.mobile.data.DshConnection
import com.dsh.mobile.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.dsh.mobile.service.DshConnectionService
import com.dsh.mobile.ui.screens.*

sealed class Screen(val route: String) {
    data object Connect : Screen("connect")
    data object Home : Screen("home")
    data object Session : Screen("session/{sessionId}?focusSeq={focusSeq}") {
        fun createRoute(sessionId: String, focusSeq: Long? = null) =
            "session/${Uri.encode(sessionId)}" + (focusSeq?.let { "?focusSeq=$it" } ?: "")
    }
    data object Settings : Screen("settings")
    data object Pending : Screen("pending")
    data object Pro : Screen("pro")
    data object Market : Screen("market")
    data object HostProfile : Screen("hostProfile/{profileId}") {
        fun createRoute(profileId: String?) = "hostProfile/${profileId ?: "new"}"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    connection: DshConnection,
    approvalCenter: ApprovalCenter,
) {
    val connState by connection.state.collectAsState()
    val pending by DshApplication.pendingSession.collectAsState()
    val context = LocalContext.current

    // 配对决策（awaitingDecision != null = 活跃主机未配对，等待用户输配对码）
    val awaitingId = (context.applicationContext as? DshApplication)
        ?.pairingCoordinator?.awaitingDecision?.collectAsState(initial = null)?.value

    val opScope = rememberCoroutineScope()
    val store = remember { SettingsStore(context) }
    val profiles by store.profiles.collectAsState(initial = emptyList())
    val activeId by store.activeProfileId.collectAsState(initial = null)
    val activeProfile = profiles.firstOrNull { it.id == activeId }
    var startupPending by rememberSaveable { mutableStateOf(true) }
    var computerMenu by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var notificationAsked by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(connState, activeProfile?.paired) {
        if (connState !is DshConnection.State.Connected || connection.currentProfile()?.paired != true) return@LaunchedEffect
        if (store.backgroundNotify.first()) {
            if (!notificationAsked && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationAsked = true
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DshConnectionService::class.java)) }
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val ready = connState is DshConnection.State.Connected &&
        activeProfile?.paired == true && connection.currentProfile()?.id == activeId && connection.currentProfile()?.paired == true

    fun openRoot(destination: Screen) {
        navController.navigate(destination.route) {
            popUpTo(Screen.Connect.route)
            launchSingleTop = true
        }
    }
    fun openWithAccess(destination: String) {
        val id = activeId ?: return
        if (verifying) return
        val origin = navController.currentBackStackEntry
        verifying = true
        opScope.launch(Dispatchers.Main.immediate) {
            try {
                val permitted = connection.confirmAccess(id)
                if (navController.currentBackStackEntry !== origin || connection.currentProfile()?.id != id) return@launch
                if (!permitted) openRoot(Screen.Connect)
                else if (destination == Screen.Home.route) openRoot(Screen.Home)
                else navController.navigate(destination) { launchSingleTop = true }
            } finally {
                verifying = false
            }
        }
    }
    fun selectRoot(screen: Screen) {
        if (screen == Screen.Home) openWithAccess(screen.route) else openRoot(screen)
    }
    LaunchedEffect(connState, activeProfile?.paired, awaitingId, route) {
        if (route in listOf(Screen.Home.route, Screen.Session.route, Screen.Pending.route) &&
            (connection.currentProfile()?.paired == false || connState == DshConnection.State.Disconnected ||
                (connState as? DshConnection.State.Error)?.code == com.dsh.mobile.data.ConnectionErrorCode.AUTH_FAILED)) {
            openRoot(Screen.Connect)
        }
    }
    BackHandler(enabled = route == Screen.Home.route || route == Screen.Settings.route) {
        openRoot(Screen.Connect)
    }

    /** 用户主动断开：停后台服务 + 清各主机自动连接标记（连接页不再自动重连旧地址），清理完再回连接页 */
    fun onUserDisconnect() {
        connection.disconnect()
        runCatching { context.stopService(Intent(context, DshConnectionService::class.java)) }
        opScope.launch(Dispatchers.Main.immediate) {
            val store = SettingsStore(context)
            store.profiles.first().filter { it.autoConnect }.forEach {
                store.upsertProfile(it.copy(autoConnect = false))
            }
            openRoot(Screen.Connect)
        }
    }

    LaunchedEffect(connState, activeProfile?.paired, pending) {
        val profile = connection.currentProfile()
        if (connState !is DshConnection.State.Connected || profile?.paired != true) return@LaunchedEffect
        val target = pending ?: return@LaunchedEffect
        DshApplication.pendingSession.value = null
        if (target.profileId == profile.id && target.computer == profile.computerScope()) {
            openWithAccess(Screen.Session.createRoute(target.sessionId))
        } else openRoot(Screen.Connect)
    }

    val configuration = LocalConfiguration.current
    val useRail = configuration.screenWidthDp >= 600 && configuration.screenWidthDp > configuration.screenHeightDp
    val rootScreen = route in listOf(Screen.Connect.route, Screen.Home.route, Screen.Settings.route)
    val showNavigation = rootScreen
    Scaffold(
        modifier = if (route == Screen.Home.route) Modifier.imePadding() else Modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showNavigation && !useRail) RootNavigation(false, route, ready, ::selectRoot)
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (showNavigation && useRail) RootNavigation(true, route, ready, ::selectRoot)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (route == Screen.Home.route) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Computer, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(activeProfile?.remark?.ifBlank { activeProfile.deviceModel }?.ifBlank { "电脑" } ?: "电脑",
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge)
                            Text(if (ready) "已连接" else if (connState is DshConnection.State.Connecting) "连接中…" else "未连接", style = MaterialTheme.typography.labelSmall)
                            Box {
                                TextButton(onClick = { computerMenu = true }, enabled = !switching) { Text("切换") }
                                DropdownMenu(expanded = computerMenu, onDismissRequest = { computerMenu = false }) {
                                    profiles.sortedByDescending { it.lastUsedAt }.forEach { profile ->
                                        DropdownMenuItem(
                                            text = { Text(profile.remark.ifBlank { profile.deviceModel }.ifBlank { "电脑" }, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis) },
                                            trailingIcon = { if (profile.id == activeId) Icon(Icons.Default.Check, "当前电脑") },
                                            onClick = {
                                                computerMenu = false
                                                if (profile.id != activeId || !ready) {
                                                    switching = true
                                                    opScope.launch(Dispatchers.Main.immediate) {
                                                        try {
                                                            val target = profile.copy(autoConnect = true)
                                                            store.upsertProfile(target)
                                                            store.setActiveProfile(target.id)
                                                            store.profiles.first().filter { it.id != target.id && it.autoConnect }
                                                                .forEach { store.upsertProfile(it.copy(autoConnect = false)) }
                                                            if (!target.paired) {
                                                                openRoot(Screen.Connect)
                                                            }
                                                            connection.connect(target) { info ->
                                                                opScope.launch(Dispatchers.Main.immediate) { store.markAttempt(info.profileId, info.errorCode) }
                                                            }
                                                        } finally { switching = false }
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).consumeWindowInsets(if (route == Screen.Home.route) WindowInsets.statusBars else WindowInsets(0, 0, 0, 0))) {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Connect.route,
                    ) {
                        composable(Screen.Connect.route) {
                            ConnectScreen(
                                connection = connection,
                                onEditHost = { id -> navController.navigate(Screen.HostProfile.createRoute(id)) },
                                startupPending = startupPending,
                                onStartupHandled = { startupPending = false },
                                onOpenSessions = { openWithAccess(Screen.Home.route) },
                                verifyingAccess = verifying,
                                onDisconnect = ::onUserDisconnect,
                            )
                        }
                        composable(Screen.Home.route) {
                            if (switching || connection.currentProfile()?.id != activeId) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (connState is DshConnection.State.Error) "连接失败" else "连接中…")
                                }
                            } else key(activeProfile.computerScope()) {
                                HomeScreen(
                                    connection = connection,
                                    approvalCenter = approvalCenter,
                                    onPending = { navController.navigate(Screen.Pending.route) },
                                    onSessionClick = { id -> openWithAccess(Screen.Session.createRoute(id)) },
                                    onSettings = { openRoot(Screen.Settings) },
                                    onUpgrade = { navController.navigate(Screen.Pro.route) },
                                    onDisconnect = { onUserDisconnect() },
                                )
                            }
                        }
                        composable(
                            route = Screen.Session.route,
                            arguments = listOf(
                                navArgument("sessionId") { type = NavType.StringType },
                                navArgument("focusSeq") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) {
                            SessionScreen(
                                sessionId = it.arguments?.getString("sessionId") ?: "",
                                connection = connection,
                                approvalCenter = approvalCenter,
                                focusSeq = it.arguments?.getString("focusSeq")?.toLongOrNull(),
                                onBack = { if (!navController.popBackStack()) openRoot(Screen.Home) },
                                onPending = { navController.navigate(Screen.Pending.route) },
                            )
                        }
                        composable(Screen.Pending.route) {
                            PendingScreen(
                                center = approvalCenter,
                                connection = connection,
                                onBack = { navController.popBackStack() },
                                onOpenSession = { sid, seq -> openWithAccess(Screen.Session.createRoute(sid, seq)) },
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                connection = connection,
                                onBack = { openRoot(Screen.Connect) },
                                onConnections = { openRoot(Screen.Connect) },
                                onUpgrade = { navController.navigate(Screen.Pro.route) },
                                onOpenMarket = { navController.navigate(Screen.Market.route) },
                                onDisconnect = { onUserDisconnect() },
                            )
                        }
                        composable(Screen.Pro.route) {
                            ProScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Screen.Market.route) {
                            MarketScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Screen.HostProfile.route,
                            arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
                        ) {
                            HostProfileScreen(
                                profileId = it.arguments?.getString("profileId")?.takeIf { v -> v != "new" },
                                connection = connection,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootNavigation(rail: Boolean, route: String?, ready: Boolean, onSelect: (Screen) -> Unit) {
    val entries = listOf(
        Triple(Screen.Connect, Icons.Default.Devices, "连接"),
        Triple(Screen.Home, Icons.Default.ChatBubbleOutline, "会话"),
        Triple(Screen.Settings, Icons.Default.Settings, "设置"),
    )
    fun tag(screen: Screen) = when (screen) {
        Screen.Connect -> "nav_connections"
        Screen.Home -> "nav_sessions"
        else -> "nav_settings"
    }
    if (rail) NavigationRail(Modifier.fillMaxHeight().statusBarsPadding()) {
        Spacer(Modifier.height(8.dp))
        entries.forEach { (screen, icon, label) ->
            NavigationRailItem(modifier = Modifier.testTag(tag(screen)), selected = route == screen.route,
                enabled = screen != Screen.Home || ready, onClick = { onSelect(screen) },
                icon = { Icon(icon, null) }, label = { Text(label) })
        }
    } else NavigationBar {
        entries.forEach { (screen, icon, label) ->
            NavigationBarItem(modifier = Modifier.testTag(tag(screen)), selected = route == screen.route,
                enabled = screen != Screen.Home || ready, onClick = { onSelect(screen) },
                icon = { Icon(icon, null) }, label = { Text(label) })
        }
    }
}
