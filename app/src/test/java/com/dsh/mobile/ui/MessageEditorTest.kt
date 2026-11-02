package com.dsh.mobile.ui

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dsh.mobile.ui.components.MessageEditor
import com.dsh.mobile.ui.theme.DshTheme
import com.dsh.mobile.ui.theme.ThemeRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h568dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modelButtonWrapsItsLabelAndEmptySpaceDoesNotOpenMenu() {
        val label = mutableStateOf("模型 A")
        var opened = 0
        compose.setContent {
            DshTheme(ThemeRegistry.warm) {
                Surface {
                    MessageEditor("draft", {}, "", RoundedCornerShape(28.dp), true, false, false, {}, {},
                        modelLabel = label.value, onModel = { opened++ })
                }
            }
        }
        val button = compose.onNodeWithContentDescription("切换模型")
        val short = button.fetchSemanticsNode().boundsInRoot
        assertTrue(short.width >= 48f && short.width < 128f)
        compose.onRoot().performTouchInput { click(Offset(short.left - 8f, short.center.y)) }
        assertEquals(0, opened)
        button.performClick()
        assertEquals(1, opened)
        compose.runOnIdle { label.value = "A very long model name with reasoning effort" }
        val long = button.fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("message_action").fetchSemanticsNode().boundsInRoot
        assertTrue(long.width > short.width && long.right <= action.left)
        assertEquals(short.height, long.height, 0.5f)
        button.performClick()
        assertEquals(2, opened)
    }

    @Test fun toolbarStaysBelowMultilineInputWithLargeText() {
        val input = mutableStateOf("")
        val running = mutableStateOf(false)
        val attachment = mutableStateOf(false)
        var sent = 0
        var stopped = 0
        var selected = ""
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.4f)) {
                DshTheme(ThemeRegistry.warm) {
                    Surface {
                        MessageEditor(input.value, { input.value = it }, "描述你的任务…", RoundedCornerShape(22.dp),
                            input.value.isNotBlank() || attachment.value, false, running.value, { sent++ }, { stopped++ },
                            permissionLabel = "工作区可写", modelLabel = "DeepSeek-V4-Flash",
                            onPermissions = { selected = "permission" }, onModel = { selected = "model" },
                            onAdd = { selected = "add" }, onVoice = { selected = "voice" })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("发送").assertDoesNotExist()
        compose.onNodeWithContentDescription("语音输入").assertIsDisplayed().performClick()
        assertEquals("voice", selected)
        compose.onNodeWithTag("message_editor").performTextInput("第一行\n第二行\n第三行")
        val field = compose.onNodeWithTag("message_editor").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("message_action").fetchSemanticsNode().boundsInRoot
        assertTrue(action.right <= field.right)
        assertTrue(action.top >= field.bottom)
        listOf("添加" to "add", "审查严格度（访问模式）" to "permission", "切换模型" to "model").forEach { (description, expected) ->
            val control = compose.onNodeWithContentDescription(description)
            control.assertIsDisplayed().performClick()
            assertEquals(expected, selected)
            val bounds = control.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.top >= field.bottom && bounds.right <= action.left)
            assertTrue(bounds.width >= 48f && bounds.height >= 48f)
        }
        assertTrue(field.width >= 240f)
        compose.onNodeWithContentDescription("语音输入").assertDoesNotExist()
        compose.onNodeWithContentDescription("发送").assertIsDisplayed().performClick()
        assertEquals(1, sent)
        compose.runOnIdle { running.value = true }
        compose.onNodeWithContentDescription("停止运行").assertIsDisplayed().performClick()
        assertEquals(1, stopped)
        compose.runOnIdle { running.value = false; input.value = ""; attachment.value = true }
        compose.onNodeWithContentDescription("发送").assertIsDisplayed()
        compose.runOnIdle { attachment.value = false }
        compose.onNodeWithContentDescription("语音输入").assertIsDisplayed()
        compose.onNodeWithContentDescription("发送").assertDoesNotExist()

    }
}
