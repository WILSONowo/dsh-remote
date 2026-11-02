package com.dsh.mobile.ui

import android.app.Application
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
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** Real Compose layouts, synthetic saved computers, no external server or LAN discovery. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationUiTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val connection = DshConnection()

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

    private fun exerciseConnectedHome() {
        val release = CountDownLatch(1)
        val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable).apply { isDaemon = true } }
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        executor.execute {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                executor.execute {
                    socket.use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        val request = reader.readLine().orEmpty()
                        var contentLength = 0
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                            if (header.startsWith("Content-Length:", true)) contentLength = header.substringAfter(':').trim().toInt()
                        }
                        repeat(contentLength) { reader.read() }
                        val output = client.getOutputStream()
                        if (request.contains("/api/events")) {
                            output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n: fixture\n\n".toByteArray())
                            output.flush()
                            release.await()
                        } else {
                            val value = if (request.contains("describe")) """{"version":"0.2.1-rc.5"}"""
                                else if (request.contains("/api/session.list ")) {
                                    val items = (1..13).joinToString(",") { n ->
                                        """{"sessionId":"recent-$n","updatedAt":${1000 - n},"projections":{"values":{"title":"Recent ${n.toString().padStart(2, '0')}"}}}"""
                                    }
                                    """{"items":[$items]}"""
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
                listOf("a", "b").forEach { id ->
                    store.upsertProfile(HostProfile("paired-$id", "Computer $id", "http://127.0.0.1:${server.localPort}", paired = true))
                }
            }
            val style = mutableStateOf(ThemeStyle.STANDARD)
            val bluePreview = mutableStateOf(false)
            compose.setContent {
                DshTheme(if (bluePreview.value) ThemeRegistry.blue else ThemeRegistry.warm.copy(style = style.value)) {
                    Surface { AppNavigation(rememberNavController(), connection, remember { ApprovalCenter(connection, scope) }) }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Computer a").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Computer a").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("切换").fetchSemanticsNodes().isNotEmpty() }
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
            compose.onNodeWithTag("home_recent_list").performScrollToIndex(2)
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Recent 01").fetchSemanticsNodes().isNotEmpty() }
            val dock = compose.onNodeWithTag("home_task_dock").fetchSemanticsNode().boundsInRoot
            val editor = compose.onNodeWithTag("message_editor").fetchSemanticsNode().boundsInRoot
            val workspace = compose.onNodeWithText("默认工作区").fetchSemanticsNode().boundsInRoot
            Assert.assertTrue(workspace.bottom <= editor.top)
            Assert.assertTrue(compose.onNodeWithTag("home_recent_list").fetchSemanticsNode().boundsInRoot.bottom <= dock.top)
            compose.onNodeWithText("默认工作区").performClick()
            compose.onAllNodesWithText("默认工作区").onLast().performClick()
            val list = compose.onNode(hasScrollToIndexAction())
            Assert.assertTrue(compose.onNodeWithTag("home_workspace_picker").fetchSemanticsNode().boundsInRoot.height >= 44f)
            list.performScrollToNode(hasContentDescription("搜索会话"))
            compose.onNodeWithContentDescription("搜索会话").performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("home_session_search")))
                .performTextInput("Recent 13")
            list.performScrollToNode(hasText("Recent 13") and !hasSetTextAction())
            compose.onNode(hasText("Recent 13") and !hasSetTextAction()).assertIsDisplayed()
            val date = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(987))
            compose.onNodeWithText(date).assertExists()
            list.performScrollToNode(hasContentDescription("关闭搜索"))
            compose.onNodeWithContentDescription("关闭搜索").performClick()
            list.performScrollToNode(hasText("显示更多"))
            compose.onNodeWithText("Recent 06").assertDoesNotExist()
            compose.onNodeWithText("显示更多").performClick()
            list.performScrollToNode(hasText("Recent 10"))
            compose.onNodeWithText("Recent 10").assertIsDisplayed()
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
                connection.currentProfile()?.id == "paired-b" && compose.onAllNodesWithText("切换").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("添加电脑").assertDoesNotExist()
            list.performScrollToNode(hasText("显示更多"))
            compose.onNodeWithText("Recent 06").assertDoesNotExist()
            compose.onNodeWithTag("nav_connections").performClick()
            compose.onNodeWithText("添加电脑").assertIsDisplayed()
            Assert.assertEquals("paired-b", connection.currentProfile()?.id)
            compose.onNodeWithText("版本 0.2.1-rc.5").performScrollTo().assertIsDisplayed()
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
            compose.onNodeWithText("断开连接").performScrollTo().performClick()
            compose.waitUntil(5_000) { connection.state.value is DshConnection.State.Disconnected }
        } finally {
            connection.disconnect()
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
