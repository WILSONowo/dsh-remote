package com.dsh.mobile.ui

import android.app.Application
import java.io.File
import java.nio.file.Files
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.rememberNavController
import com.dsh.mobile.data.*
import com.dsh.mobile.ui.navigation.AppNavigation
import com.dsh.mobile.ui.theme.DshTheme
import com.dsh.mobile.ui.theme.ThemeRegistry
import com.dsh.mobile.ui.theme.ThemeStyle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import okio.buffer
import okio.source
import org.json.JSONObject
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

// Robolectric can reuse the Kotlin process singleton for Preferences DataStore across
// activities while deleting each test application's data directory. Give this suite
// one temporary files directory for the same lifetime as that singleton.
class NavigationFixtureApplication : Application() {
    override fun getFilesDir(): File = directory
    companion object {
        val directory: File by lazy { Files.createTempDirectory("dsh-navigation-fixture-").toFile() }
    }
}

/** Real Compose layouts, synthetic saved computers, no external server or LAN discovery. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-mdpi", application = NavigationFixtureApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationUiTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var checkIntervalMs = 120_000L
    private val connection by lazy { DshConnection(RuntimeEnvironment.getApplication(), accessCheckIntervalMs = checkIntervalMs) }

    @Before fun resetSyntheticProfiles() = runBlocking {
        val store = SettingsStore(RuntimeEnvironment.getApplication())
        store.profiles.first().forEach { store.deleteProfile(it.id) }
        store.setActiveProfile(null)
    }

    @After fun cleanup() { connection.disconnect(); scope.cancel() }

    private fun launch(fontScale: Float = 1f) {
        val context = RuntimeEnvironment.getApplication()
        runBlocking {
            val store = SettingsStore(context)
            store.setBackgroundNotify(false)
            store.upsertProfile(HostProfile("fixture-a", "办公室电脑 · 长名称显示测试", "http://192.0.2.10:7941"))
            store.upsertProfile(HostProfile("fixture-b", "家里的电脑", "http://192.0.2.11:7941"))
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                DshTheme(ThemeRegistry.warm) {
                    Surface {
                        AppNavigation(rememberNavController(), connection, remember { ApprovalCenter(connection, scope) })
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("添加电脑").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun settingsAndSavedComputersRemainReachableWhileDisconnected() {
        launch()
        compose.onNodeWithText("添加电脑").assertIsDisplayed()
        compose.onNodeWithTag("nav_sessions").assertIsEnabled().performClick()
        compose.onNodeWithText("最近会话").assertIsDisplayed()
        compose.onNodeWithText("未连接").assertIsDisplayed()
        compose.onNodeWithText("连接电脑").assertIsDisplayed()
        compose.onNodeWithTag("nav_connections").performClick()
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithText("主题模式").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("提醒").performClick()
        compose.onNodeWithTag("nav_connections").performClick()
        compose.onNodeWithText("添加电脑").assertIsDisplayed()
        compose.onNodeWithText("家里的电脑").assertIsDisplayed()
        Assert.assertTrue(connection.state.value is DshConnection.State.Disconnected)
    }

    @Test fun lastKnownVersionSurvivesStaleProfileWritesAndUnknownVersions() = runBlocking {
        val store = SettingsStore(RuntimeEnvironment.getApplication())
        val profile = HostProfile("version-a", "Computer A", "http://192.0.2.20:7941")
        store.upsertProfile(profile)
        store.rememberHostVersion(profile, "0.2.1-rc.5")
        store.rememberHostVersion(profile, VersionPolicy.PLACEHOLDER_VERSION)
        store.upsertProfile(profile.copy(remark = "Renamed"))
        assertVersion(store, "0.2.1-rc.5")
        val otherEndpoint = profile.copy(url = "http://192.0.2.21:7941")
        store.upsertProfile(otherEndpoint)
        store.rememberHostVersion(profile, "1.0.0")
        assertVersion(store, null)
    }

    private suspend fun assertVersion(store: SettingsStore, expected: String?) {
        Assert.assertEquals(expected, store.profiles.first().single { it.id == "version-a" }.lastHostVersion)
    }

    @Test fun numberedMarkdownAfterParagraphKeepsItsNumbersAndLiteralText() {
        compose.setContent {
            com.dsh.mobile.ui.components.MarkdownText("开头\n1. 第一项\n12) 第二项\n\nd.s 普通文本")
        }
        compose.onNodeWithText("1.  ").assertExists()
        compose.onNodeWithText("12.  ").assertExists()
        compose.onNodeWithText("d.s 普通文本").assertExists()
    }

    @Test fun workspaceAndPinnedSessionsAreIndependentForEachComputer() = runBlocking {
        val store = SettingsStore(RuntimeEnvironment.getApplication())
        store.setWorkspaceId("workspace-a", "scope-a")
        store.setWorkspaceId("workspace-b", "scope-b")
        store.setPinned("same-session", true, "scope-a")
        store.setPinned("same-session", false, "scope-b")
        Assert.assertEquals("workspace-a", store.workspaceId("scope-a").first())
        Assert.assertEquals("workspace-b", store.workspaceId("scope-b").first())
        Assert.assertTrue("same-session" in store.pinnedSessionIds("scope-a").first())
        Assert.assertFalse("same-session" in store.pinnedSessionIds("scope-b").first())
    }

    @Test fun connectedComputerCanReturnToConnectionsAndSwitchToAnotherComputer() = exerciseConnectedHome()

    @Test @Config(qualifiers = "w667dp-h400dp-land-mdpi")
    fun landscapeKeepsHomeComposerAndWorkspaceAboveNavigation() = exerciseConnectedHome()

    @Test
    fun revokedDeviceCannotEnterSessions() = exerciseConnectedHome(revoke = "before")

    @Test
    fun idleRevocationReturnsToPairing() = exerciseConnectedHome(revoke = "idle")

    @Test fun temporaryCheckFailureKeepsPairingAndRecoversInsideSessions() = exerciseConnectedHome(revoke = "offline")

    @Test fun firstTaskUsesPresetOverrideBeforePrompt() = exerciseConnectedHome(firstTask = "manual")

    @Test fun firstTaskUsesComputerDefaultWithAdaptiveModel() = exerciseConnectedHome(firstTask = "adaptive")

    private fun exerciseConnectedHome(revoke: String? = null, firstTask: String? = null) {
        if (revoke != null) checkIntervalMs = 300L
        val calls = java.util.concurrent.CopyOnWriteArrayList<JSONObject>()
        val archived = java.util.concurrent.atomic.AtomicBoolean(true)
        val archivedId = "session-abcdef01-example"
        val unavailable = java.util.concurrent.atomic.AtomicBoolean(false)
        val authorized = java.util.concurrent.atomic.AtomicBoolean(true)
        val accessReply = CountDownLatch(1)
        val holdAccess = java.util.concurrent.atomic.AtomicBoolean(false)
        val workspaceReply = java.util.concurrent.atomic.AtomicReference(CountDownLatch(1))
        val accessChecks = java.util.concurrent.atomic.AtomicInteger()
        val release = CountDownLatch(1)
        val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable).apply { isDaemon = true } }
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        executor.execute {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                executor.execute {
                    socket.use { client ->
                        val reader = client.getInputStream().source().buffer()
                        val request = reader.readUtf8Line().orEmpty()
                        var contentLength = 0
                        while (true) {
                            val header = reader.readUtf8Line() ?: break
                            if (header.isEmpty()) break
                            if (header.startsWith("Content-Length:", true)) contentLength = header.substringAfter(':').trim().toInt()
                        }
                        if (contentLength > 0) calls.add(JSONObject(reader.readUtf8(contentLength.toLong())))
                        val output = client.getOutputStream()
                        if (request.contains("/pair/check?")) {
                            accessChecks.incrementAndGet()
                            if (holdAccess.get()) accessReply.await()
                            val payload = """{"ok":true,"paired":${authorized.get()}}""".toByteArray()
                            val status = if (unavailable.get()) "503 Unavailable" else "200 OK"
                            output.write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            output.write(payload)
                            output.flush()
                        } else if (request.contains("/api/events")) {
                            output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n: fixture\n\n".toByteArray())
                            output.flush()
                            release.await()
                        } else {
                            val value = if (request.contains("describe")) """{"version":"0.2.1-rc.5"}"""
                                else if (request.contains("/api/agentPreset.list ")) """{"presets":[{"id":"cordis","name":"Creator"},{"id":"standard","name":"Standard","isDefault":true},{"id":"custom","name":"Custom preset"}]}"""
                                else if (request.contains("/api/session.models ")) """{"current":{"provider":"fixture","model":"pro"},"groups":[{"id":"fixture","models":[{"id":"flash","name":"Fixture Flash"},{"id":"pro","name":"Fixture Pro","reasoning":{"efforts":[{"id":"high","name":"高"}]}}]}]}"""
                                else if (request.contains("/api/workspace.list ")) {
                                    workspaceReply.get().await()
                                    val ids = if (archived.get()) """["$archivedId"]""" else "[]"
                                    """{"items":[],"archivedSessionIds":$ids}"""
                                } else if (request.contains("/api/workspace.archiveSession ")) {
                                    archived.set(true)
                                    "{}"
                                } else if (request.contains("/api/workspace.unarchiveSession ")) {
                                    archived.set(false)
                                    "{}"
                                } else if (request.contains("/api/session.create ")) """{"sessionId":"new-fixture"}"""
                                else if (request.contains("/api/session.list ") && firstTask != null) """{"items":[]}"""
                                else if (request.contains("/api/session.list ")) {
                                    val items = (1..13).joinToString(",") { n ->
                                        """{"sessionId":"recent-$n","updatedAt":${1000 - n},"projections":{"values":{"title":"Recent ${n.toString().padStart(2, '0')}"}}}"""
                                    }
                                    """{"items":[{"sessionId":"$archivedId","updatedAt":9999},$items]}"""
                                } else """{"items":[],"events":[],"archivedSessionIds":[]}"""
                            val payload = """{"type":"server-response","rpcId":"fixture","result":{"ok":true,"value":$value}}""".toByteArray()
                            output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            output.write(payload)
                            output.flush()
                        }
                    }
                }
            }
        }
        try {
            val store = SettingsStore(RuntimeEnvironment.getApplication())
            runBlocking {
                store.setBackgroundNotify(false)
                store.setAutoModel(firstTask == "adaptive")
                listOf("a", "b").forEach { id ->
                    store.upsertProfile(HostProfile("paired-$id", "Computer $id", "http://127.0.0.1:${server.localPort}", paired = true))
                }
            }
            val style = mutableStateOf(ThemeStyle.STANDARD)
            val bluePreview = mutableStateOf(false)
            lateinit var hostView: android.view.View
            val coordinator = if (revoke != null) PairingCoordinator(connection, store, scope).also { it.start() } else null
            compose.setContent {
                hostView = androidx.compose.ui.platform.LocalView.current
                DshTheme(if (bluePreview.value) ThemeRegistry.blue else ThemeRegistry.warm.copy(style = style.value)) {
                    Surface { AppNavigation(rememberNavController(), connection, remember { ApprovalCenter(connection, scope) }) }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Computer a").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Computer a").performClick()
            compose.waitUntil("Connected fixture: ${connection.state.value}", 10_000) {
                val state = connection.state.value
                check(state !is DshConnection.State.Error) { state.toString() }
                compose.onAllNodesWithText("断开连接").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("添加电脑").assertIsDisplayed()
            compose.onNodeWithText("切换").assertDoesNotExist()
            val verifiedChecks = accessChecks.get()
            holdAccess.set(revoke != null)
            if (revoke == "before") authorized.set(false)
            if (revoke == "offline") unavailable.set(true)
            compose.onNodeWithTag("nav_sessions").assertIsEnabled().performClick()
            compose.onNodeWithText("切换").assertIsDisplayed()
            compose.onNodeWithText("最近会话").assertIsDisplayed()
            compose.onNodeWithTag("home_recent_list").assertIsDisplayed()
            if (revoke == null) {
                compose.waitUntil(5_000) { calls.any { it.optString("method") == "session.list" } }
                compose.onNodeWithTag("session_page_loading").assertIsDisplayed()
                compose.onNodeWithText("会话 abcdef01").assertDoesNotExist()
                compose.onNodeWithText("已连接").assertIsDisplayed()
                Assert.assertEquals(verifiedChecks, accessChecks.get())
            }
            workspaceReply.get().countDown()
            if (firstTask == "manual") compose.onNodeWithTag("message_editor").performTextInput("draft")
            accessReply.countDown()
            if (revoke == "offline") {
                compose.waitUntil(10_000) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    connection.state.value is DshConnection.State.Error
                }
                Assert.assertTrue(runBlocking { store.profiles.first().single { it.id == "paired-a" }.paired })
                unavailable.set(false)
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("message_editor").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("切换").assertIsDisplayed()
                return
            }
            if (revoke == "idle") {
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("message_editor").fetchSemanticsNodes().isNotEmpty() }
            }
            if (revoke != null) {
                authorized.set(false)
                compose.waitUntil(10_000) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    coordinator?.awaitingDecision?.value == "paired-a"
                }
                compose.onNodeWithText("切换").assertDoesNotExist()
                compose.onNodeWithText("添加电脑").assertIsDisplayed()
                val saved = runBlocking { store.profiles.first() }
                Assert.assertFalse(saved.single { it.id == "paired-a" }.paired)
                Assert.assertTrue(saved.single { it.id == "paired-b" }.paired)
                return
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("message_editor").fetchSemanticsNodes().isNotEmpty() }
            if (firstTask != null) {
                compose.waitUntil(10_000) { compose.onAllNodesWithText("已连接").fetchSemanticsNodes().isNotEmpty() }
                if (firstTask == "manual") {
                    compose.onNodeWithTag("message_editor").assertTextContains("draft").performTextClearance()
                    compose.onNodeWithTag("nav_settings").performClick()
                    compose.onNode(hasText("会话") and !hasTestTag("nav_sessions")).performClick()
                    compose.onNodeWithText("默认预设").performClick()
                    compose.waitUntil(5_000) { compose.onAllNodesWithText("Custom preset").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("Custom preset").performClick()
                    compose.onNodeWithTag("nav_sessions").performClick()
                    compose.waitUntil(5_000) { compose.onAllNodesWithText("Custom preset").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithContentDescription("切换预设").performClick()
                    compose.onNodeWithText("Creator").performClick()
                    compose.onNodeWithContentDescription("切换预设").assertTextContains("Creator")
                    Assert.assertEquals("custom", runBlocking { store.defaultPreset(connection.currentProfile().computerScope()).first() })
                } else {
                    compose.waitUntil(5_000) { compose.onAllNodesWithText("Standard").fetchSemanticsNodes().isNotEmpty() }
                }
                Assert.assertFalse(calls.any { it.optString("method") == "session.models" })
                Assert.assertFalse(calls.any { it.optString("method") == "session.create" })
                compose.onNodeWithTag("message_editor").performTextInput("hello")
                compose.onNodeWithTag("message_action").performClick()
                compose.waitUntil(10_000) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    calls.any { it.optString("method") == "session.prompt" }
                }
                val writes = calls.filter { it.optString("method") in setOf("session.create", "session.selectModel", "session.prompt") }
                val expected = if (firstTask == "manual") listOf("session.create", "session.prompt")
                    else listOf("session.create", "session.selectModel", "session.prompt")
                Assert.assertEquals(expected, writes.map { it.getString("method") })
                val create = writes.first().getJSONObject("payload")
                if (firstTask == "manual") Assert.assertEquals("cordis", create.getString("agentPreset"))
                else {
                    Assert.assertFalse(create.has("agentPreset"))
                    Assert.assertEquals("flash", writes[1].getJSONObject("payload").getString("model"))
                }
                compose.waitUntil(10_000) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    compose.onAllNodesWithTag("nav_sessions").fetchSemanticsNodes().isEmpty()
                }
                compose.waitForIdle()
                return
            }
            if (RuntimeEnvironment.getApplication().resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                val nav = compose.onNodeWithTag("nav_sessions")
                val before = nav.fetchSemanticsNode().boundsInRoot
                val dockNode = compose.onNodeWithTag("home_task_dock")
                val dockBefore = dockNode.fetchSemanticsNode().boundsInRoot
                val tools = compose.onNodeWithTag("recent_session_tools")
                val toolsTop = tools.fetchSemanticsNode().boundsInRoot.top
                val original = androidx.core.view.ViewCompat.getRootWindowInsets(hostView)
                val ime = androidx.core.view.WindowInsetsCompat.Type.ime()
                fun deliverIme(height: Int) {
                    compose.runOnUiThread {
                        val insets = androidx.core.view.WindowInsetsCompat.Builder(original ?: androidx.core.view.WindowInsetsCompat.CONSUMED)
                            .setInsets(ime, androidx.core.graphics.Insets.of(0, 0, 0, height))
                            .setVisible(ime, height > 0).build()
                        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(hostView, insets)
                    }
                }
                deliverIme(220)
                nav.assertIsDisplayed()
                Assert.assertEquals(before, nav.fetchSemanticsNode().boundsInRoot)
                Assert.assertTrue(dockNode.fetchSemanticsNode().boundsInRoot.bottom < dockBefore.bottom)
                Assert.assertEquals(toolsTop, tools.fetchSemanticsNode().boundsInRoot.top, 1f)
                deliverIme(0)
                nav.assertIsDisplayed()
                Assert.assertEquals(before, nav.fetchSemanticsNode().boundsInRoot)
                Assert.assertEquals(dockBefore, dockNode.fetchSemanticsNode().boundsInRoot)
            }
            ThemeStyle.entries.forEach { theme ->
                compose.runOnIdle { style.value = theme }
                compose.onNodeWithTag("nav_connections").assertIsDisplayed()
                compose.onNodeWithTag("nav_settings").assertIsDisplayed()
                compose.onNodeWithText("切换").assertIsDisplayed()
            }
            compose.runOnIdle { style.value = ThemeStyle.STANDARD }
            compose.runOnIdle { bluePreview.value = true }
            compose.onNodeWithTag("workspace_theme_decoration", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("鲸鱼娘").assertIsDisplayed()
            compose.runOnIdle { bluePreview.value = false }
            compose.onNodeWithTag("workspace_theme_decoration", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("鲸鱼娘").assertIsDisplayed()
            // Compact windows show the greeting first; scroll the list independently of the dock.
            compose.onNodeWithTag("home_recent_list").performScrollToIndex(0)
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Recent 01").fetchSemanticsNodes().isNotEmpty() }
            val dock = compose.onNodeWithTag("home_task_dock").fetchSemanticsNode().boundsInRoot
            val editor = compose.onNodeWithTag("message_editor").fetchSemanticsNode().boundsInRoot
            val workspace = compose.onNodeWithText("默认工作区").fetchSemanticsNode().boundsInRoot
            Assert.assertTrue(workspace.bottom <= editor.top || workspace.right <= editor.left)
            Assert.assertTrue(compose.onNodeWithTag("home_recent_list").fetchSemanticsNode().boundsInRoot.bottom <= dock.top)
            if (RuntimeEnvironment.getApplication().resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                val tools = compose.onNodeWithTag("recent_session_tools")
                val topBefore = tools.fetchSemanticsNode().boundsInRoot.top
                val recentList = compose.onNodeWithTag("home_recent_list")
                recentList.performTouchInput { swipeUp() }
                val pinned = tools.fetchSemanticsNode().boundsInRoot.top
                Assert.assertTrue(pinned < topBefore)
                recentList.performTouchInput { swipeUp() }
                Assert.assertEquals(pinned, tools.fetchSemanticsNode().boundsInRoot.top, 1f)
                repeat(3) { recentList.performTouchInput { swipeDown() } }
                Assert.assertEquals(topBefore, tools.fetchSemanticsNode().boundsInRoot.top, 1f)
            }
            compose.onNodeWithText("默认工作区").performClick()
            compose.onAllNodesWithText("默认工作区").onLast().performClick()
            val list = compose.onNode(hasScrollToIndexAction())
            Assert.assertTrue(compose.onNodeWithTag("home_workspace_picker").fetchSemanticsNode().boundsInRoot.height >= 44f)
            compose.onNodeWithContentDescription("搜索会话").performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("home_session_search")))
                .performTextInput("Recent 13")
            list.performScrollToNode(hasText("Recent 13") and !hasSetTextAction())
            compose.onNode(hasText("Recent 13") and !hasSetTextAction()).assertIsDisplayed()
            val date = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(987))
            compose.onNodeWithText(date).assertExists()
            compose.onNodeWithContentDescription("关闭搜索").performClick()
            list.performScrollToNode(hasText("显示更多"))
            compose.onNodeWithText("Recent 06").assertDoesNotExist()
            compose.onNodeWithText("显示更多").performClick()
            val toolbar = compose.onNodeWithTag("recent_session_tools").fetchSemanticsNode().boundsInRoot
            list.performScrollToNode(hasText("Recent 10"))
            compose.onNodeWithText("Recent 10").assertIsDisplayed()
            Assert.assertEquals(toolbar, compose.onNodeWithTag("recent_session_tools").fetchSemanticsNode().boundsInRoot)
            Assert.assertEquals(dock.top, compose.onNodeWithTag("home_task_dock").fetchSemanticsNode().boundsInRoot.top, 0.5f)
            compose.onNodeWithTag("message_editor").assertIsDisplayed()
            list.performScrollToNode(hasText("显示更多"))
            compose.onNodeWithText("Recent 11").assertDoesNotExist()
            compose.onNodeWithText("显示更多").performClick()
            list.performScrollToNode(hasText("Recent 13"))
            compose.onNodeWithText("Recent 13").assertIsDisplayed()
            compose.onNodeWithText("显示更多").assertDoesNotExist()
            list.performScrollToNode(hasText("收起"))
            compose.onNodeWithText("收起").performClick()
            compose.onNodeWithText("Recent 01").assertIsDisplayed()
            compose.onNodeWithText("Recent 06").assertDoesNotExist()
            compose.onNodeWithText("收起").assertDoesNotExist()
            list.performScrollToNode(hasText("显示更多"))
            compose.onNodeWithText("显示更多").performClick()
            list.performScrollToNode(hasText("Recent 10"))
            compose.onNodeWithText("Recent 10").assertIsDisplayed()
            // Select another paired computer directly, without visiting the connection page.
            compose.onNodeWithText("切换").performClick()
            compose.onNodeWithText("Computer b").performClick()
            compose.waitUntil(10_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                connection.currentProfile()?.id == "paired-b" && compose.onAllNodesWithText("Recent 01").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("添加电脑").assertDoesNotExist()
            compose.onNodeWithTag("home_recent_list").performScrollToNode(hasText("显示更多"))
            compose.onNodeWithText("Recent 06").assertDoesNotExist()
            compose.onNodeWithTag("nav_connections").performClick()
            compose.onNodeWithText("添加电脑").assertIsDisplayed()
            Assert.assertEquals("paired-b", connection.currentProfile()?.id)
            compose.onNode(hasText("版本 0.2.1-rc.5") and hasAnyAncestor(hasTestTag("active_computer"))).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("发送文件到电脑").assertDoesNotExist()
            val sessions = compose.onNode(hasText("会话") and !hasTestTag("nav_sessions"))
            val edit = compose.onNodeWithText("编辑")
            val disconnect = compose.onNodeWithText("断开连接")
            sessions.assertIsDisplayed()
            edit.assertIsDisplayed()
            disconnect.assertIsDisplayed()
            val sessionBounds = sessions.fetchSemanticsNode().boundsInRoot
            val editBounds = edit.fetchSemanticsNode().boundsInRoot
            val disconnectBounds = disconnect.fetchSemanticsNode().boundsInRoot
            Assert.assertTrue(sessionBounds.right <= editBounds.left)
            Assert.assertTrue(editBounds.right < disconnectBounds.left)
            Assert.assertEquals(sessionBounds.center.y, disconnectBounds.center.y, 1f)
            edit.performClick()
            compose.onNodeWithText("保存").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("断开连接").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("断开连接").performScrollTo().performClick()
            compose.waitUntil(5_000) { connection.state.value is DshConnection.State.Disconnected }
            compose.onNode(hasText("版本 0.2.1-rc.5") and hasAnyAncestor(hasTestTag("active_computer"))).assertExists()
            compose.waitUntil(5_000) {
                runBlocking { store.profiles.first() }.singleOrNull { it.id == "paired-b" }?.lastHostVersion == "0.2.1-rc.5"
            }
            compose.onNodeWithTag("nav_sessions").assertIsEnabled().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Recent 01").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("已连接").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("已归档（点击恢复）").assertDoesNotExist()
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNode(hasText("会话") and !hasTestTag("nav_sessions")).performClick()
            compose.onNodeWithText("归档会话").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("会话 abcdef01").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("恢复").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("暂无归档会话").fetchSemanticsNodes().isNotEmpty() }
            val restore = calls.single { it.optString("method") == "workspace.unarchiveSession" }.getJSONObject("payload")
            Assert.assertEquals(archivedId, restore.getString("sessionId"))
            Assert.assertFalse(restore.has("workspaceId"))
            Assert.assertFalse(calls.any { it.optString("method") == "workspace.insertSessionBefore" })
            val checksBeforeReturn = accessChecks.get()
            compose.onNodeWithTag("nav_sessions").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("会话 abcdef01").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("会话 abcdef01").performTouchInput { longClick() }
            compose.onNodeWithText("归档").performClick()
            compose.onNodeWithText("归档").performClick()
            compose.waitUntil(5_000) { archived.get() && compose.onAllNodesWithText("会话 abcdef01").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("nav_connections").performClick()
            workspaceReply.set(CountDownLatch(1))
            val listCount = calls.count { it.optString("method") == "session.list" }
            compose.onNodeWithTag("nav_sessions").performClick()
            compose.waitUntil(5_000) { calls.count { it.optString("method") == "session.list" } > listCount }
            compose.onNodeWithTag("session_page_loading").assertIsDisplayed()
            compose.onNodeWithText("会话 abcdef01").assertDoesNotExist()
            workspaceReply.get().countDown()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Recent 01").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("会话 abcdef01").assertDoesNotExist()
            Assert.assertEquals(checksBeforeReturn, accessChecks.get())
        } finally {
            if (firstTask != null) {
                val reset = scope.async(Dispatchers.IO) { SettingsStore(RuntimeEnvironment.getApplication()).setAutoModel(false) }
                compose.waitUntil(5_000) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    reset.isCompleted
                }
                runBlocking { reset.await() }
            }
            connection.disconnect()
            workspaceReply.get().countDown()
            accessReply.countDown()
            release.countDown()
            server.close()
            executor.shutdownNow()
        }
    }

    @Test @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun smallScreenAndLargeTextKeepNavigationAndFormActionsReachable() {
        launch(1.4f)
        compose.onNodeWithText("添加电脑").assertIsDisplayed()
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNodeWithText("添加电脑").performClick()
        compose.onNodeWithText("IP 直连").assertIsDisplayed()
        compose.onNodeWithText("保存并连接").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("安全").performClick()
        compose.onNodeWithText("设备名称").performScrollTo().assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w667dp-h400dp-land-mdpi")
    fun landscapeUsesSideNavigationWithoutHidingSettingsOrComputerActions() {
        launch()
        val navigation = compose.onNodeWithTag("nav_connections").fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(navigation.left < 80f)
        Assert.assertTrue(navigation.top < 200f)
        compose.onNodeWithText("添加电脑").assertIsDisplayed()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("提醒").assertIsDisplayed().performClick()
        compose.onNodeWithTag("nav_connections").performClick()
        compose.onNodeWithText("添加电脑").assertIsDisplayed()
    }

    @Test fun automaticModelStartsOffAndBelongsToSessionSettings() {
        launch()
        val store = SettingsStore(RuntimeEnvironment.getApplication())
        Assert.assertFalse(runBlocking { store.autoModel.first() })
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNode(hasText("会话") and !hasTestTag("nav_sessions")).performClick()
        compose.onNode(isToggleable()).assertIsOff().performClick()
        compose.waitUntil(5_000) { runBlocking { store.autoModel.first() } }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithText("自适应模型").assertDoesNotExist()
    }

    @Test fun appearanceSubpageReturnsToItsParentAndPersistsSliderChanges() {
        launch()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithText("更多外观设置").performClick()
        compose.onNodeWithContentDescription("模糊").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(12f) }
        compose.waitUntil(5_000) {
            runBlocking { SettingsStore(RuntimeEnvironment.getApplication()).appearance.first().bgBlur == 12f }
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("主题模式").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("安全").assertIsDisplayed()
        compose.onNodeWithTag("nav_connections").performClick()
        compose.onNodeWithText("添加电脑").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun settingValuesStayOnOneLineAndChoicesKeepTheFullName() {
        launch(1.4f)
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("外观").performClick()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("默认（Monospace）", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        Assert.assertEquals(1, layouts.single().lineCount)
        compose.onNodeWithText("代码字体").performClick()
        compose.onNode(hasText("默认（Monospace）") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("衬线等宽（serif-monospace）").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("主题模式").assertIsDisplayed()
    }
}
