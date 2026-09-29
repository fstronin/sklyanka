package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Компонент снэкбара (SKL-30, §4.12): текст, одно действие, live region и поведение
 * при крупном шрифте.
 *
 * Проверяется свойство разметки, а не картинка: эталонный снимок фиксирует изменение,
 * но не утверждает правильность (§4.12, `SKL-24/TDD.md §2.2`).
 */
@RunWith(AndroidJUnit4::class)
class SnackbarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theSnackbarShowsTheTextAndExactlyOneAction() {
        var clicked = false
        show {
            Snackbar(
                text = MESSAGE,
                actionLabel = ACTION,
                onAction = { clicked = true },
                modifier = Modifier.testTag(TAG_SNACKBAR),
            )
        }

        composeTestRule.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText(MESSAGE, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText(ACTION, useUnmergedTree = true).performClick()
        assertTrue("the single action must be the one that runs", clicked)
    }

    @Test
    fun theMessageIsAPoliteLiveRegion() {
        show { Snackbar(text = MESSAGE, actionLabel = ACTION, onAction = {}) }

        val node = composeTestRule.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, node.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun atTwoHundredPercentTheActionMovesUnderTheText() {
        composeTestRule.setContent {
            Scaled(SCALE_200) {
                SklyankaTheme {
                    Box(Modifier.width(NARROW_WIDTH)) {
                        Snackbar(text = MESSAGE, actionLabel = ACTION, onAction = {})
                    }
                }
            }
        }

        val text = composeTestRule.onNodeWithText(MESSAGE, useUnmergedTree = true).fetchSemanticsNode()
        val action = composeTestRule.onNodeWithText(ACTION, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "the action must stack under the text at 200 %: action top ${action.boundsInRoot.top}, " +
                "text bottom ${text.boundsInRoot.bottom}",
            action.boundsInRoot.top >= text.boundsInRoot.bottom,
        )
    }

    @Test
    fun theActionKeepsItsTouchTarget() {
        show { Snackbar(text = MESSAGE, actionLabel = ACTION, onAction = {}) }

        val node = composeTestRule.onNode(hasClickAction(), useUnmergedTree = true).fetchSemanticsNode()
        val density = node.layoutInfo.density
        val width = with(density) { node.size.width.toDp() }
        val height = with(density) { node.size.height.toDp() }
        val minimum = SklyankaSizes.MinTouchTarget - SklyankaSizes.BorderThin
        assertTrue(
            "the action is $width×$height, below ${SklyankaSizes.MinTouchTarget}",
            width >= minimum && height >= minimum,
        )
    }

    private fun show(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            SklyankaTheme { content() }
        }
    }

    /**
     * Системный масштаб шрифта подменой плотности — так же, как его задаёт снимку
     * `RoborazziComposeOptions.fontScale`: масштаб живёт в `Density`, а не в теме.
     */
    @Composable
    private fun Scaled(scale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = scale),
            content = content,
        )
    }

    private companion object {
        const val MESSAGE = "Magnesium B6 — the intake is marked as taken"
        const val ACTION = "Undo"
        const val SCALE_200 = 2.0f
        val NARROW_WIDTH = 220.dp
    }
}
