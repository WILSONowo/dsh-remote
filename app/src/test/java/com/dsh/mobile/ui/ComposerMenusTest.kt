package com.dsh.mobile.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dsh.mobile.data.ModelSelection
import com.dsh.mobile.data.SessionModelsValue
import com.dsh.mobile.ui.components.*
import com.dsh.mobile.ui.theme.DshTheme
import com.dsh.mobile.ui.theme.ThemeRegistry
import kotlinx.serialization.json.Json
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
class ComposerMenusTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(qualifiers = "w1000dp-h600dp-land-xhdpi")
    fun landscapeMenuStaysInWindowAndLongListsRemainScrollable() {
        val open = mutableStateOf(false)
        var selected = -1
        compose.setContent {
            DshTheme(ThemeRegistry.blue) {
                Surface {
                    Column(Modifier.fillMaxSize().testTag("window")) {
                        Spacer(Modifier.weight(1f))
                        MessageEditor("draft", {}, "", RoundedCornerShape(28.dp), true, false, false, {}, {},
                            onModel = { open.value = true }, modelMenu = {
                                ComposerPopup(open.value, { open.value = false }) {
                                    repeat(30) { index ->
                                        ComposerMenuItem(text = { androidx.compose.material3.Text("Model $index") },
                                            onClick = { selected = index; open.value = false })
                                    }
                                }
                            })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("切换模型").performClick()
        val window = compose.onNodeWithTag("window").fetchSemanticsNode().boundsInWindow
        val popup = compose.onNode(isPopup()).fetchSemanticsNode().boundsInWindow
        val toolbar = compose.onNodeWithTag("message_toolbar").fetchSemanticsNode().boundsInWindow
        assertTrue(popup.left >= window.left && popup.right <= window.right)
        assertTrue(popup.top >= window.top && popup.bottom <= toolbar.top)
        compose.onNodeWithText("Model 29").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(29, selected)
        compose.onNode(isPopup()).assertDoesNotExist()
    }

    @Test fun modelSubmenuUsesServerCapabilitiesAndClearsEffortForAnotherModel() {
        val info = mutableStateOf(SessionModelsValue(ModelSelection("example", "a", "low"), groups = Json.parseToJsonElement("""
            [{"id":"example","name":"Example provider","models":[
                {"id":"a","name":"Model A","reasoning":{"efforts":[{"id":"low","name":"Low"},{"id":"high","name":"High"}]}},
                {"id":"deepseek-v4-flash","name":"DeepSeek-V4-Flash (custom provider)"}
            ]}]
        """)))
        val open = mutableStateOf(false)
        compose.setContent {
            DshTheme(ThemeRegistry.warm) {
                Surface {
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.weight(1f))
                        MessageEditor("draft", {}, "", RoundedCornerShape(26.dp), true, false, false, {}, {},
                            modelLabel = composerModelLabel(info.value), onModel = { open.value = true },
                            modelMenu = {
                                ModelPickerMenu(open.value, info.value, false, false, { open.value = false }, {},
                                    onApply = { provider, model, effort ->
                                        info.value = info.value.copy(current = ModelSelection(provider, model, effort))
                                        open.value = false
                                    })
                            })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("切换模型").performClick()
        compose.onNodeWithText("High").assertIsDisplayed().performClick()
        assertEquals("high", info.value.current.reasoningEffort)
        compose.onNodeWithText("Model A High").assertIsDisplayed()
        compose.onNodeWithContentDescription("切换模型").performClick()
        compose.onNodeWithText("模型").performScrollTo().performClick()
        val flash = compose.onNodeWithText("V4-Flash (custom provider)", useUnmergedTree = true)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        flash.performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        compose.onNodeWithText("V4-Flash (custom provider)").performClick()
        assertEquals(ModelSelection("example", "deepseek-v4-flash", null), info.value.current)
        compose.onNodeWithContentDescription("切换模型").performClick()
        compose.onNodeWithText("思考程度").assertDoesNotExist()
        compose.onNodeWithText("模型").assertIsDisplayed()
    }

    @Test fun permissionMenuShowsCurrentSelectionAndOnlySupportedModes() {
        val current = mutableStateOf("read-only")
        val open = mutableStateOf(false)
        compose.setContent {
            DshTheme(ThemeRegistry.warm) {
                Surface {
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.weight(1f))
                        MessageEditor("draft", {}, "", RoundedCornerShape(26.dp), true, false, false, {}, {},
                            onPermissions = { open.value = true }, permissionMenu = {
                                AccessModeMenu(open.value, current.value, false, { open.value = false }) {
                                    current.value = it; open.value = false
                                }
                            })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("审查严格度（访问模式）").performClick()
        compose.onNodeWithText("只读").assertIsDisplayed()
        compose.onNodeWithText("工作区可写").assertIsDisplayed().performClick()
        assertEquals("workspace-write", current.value)
        compose.onNodeWithText("只读").assertDoesNotExist()
        compose.onNodeWithContentDescription("审查严格度（访问模式）").performClick()
        compose.onNodeWithContentDescription("已选择").assertExists()
    }
}
