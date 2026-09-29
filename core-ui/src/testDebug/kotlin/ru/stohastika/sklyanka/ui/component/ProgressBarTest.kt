package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Ссылка в строке подписи полосы прогресса (ОВ-84, SKL-31 §8.1).
 *
 * Проверяется свойство разметки: слово, клик, тег строки и невидимо расширенная цель.
 * Неизменность полосы без действия стерегут эталонные снимки каталога: параметр со
 * значением по умолчанию `null` оставляет прежнюю разметку.
 */
@RunWith(AndroidJUnit4::class)
class ProgressBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theActionShowsTheWordAndRunsTheClick() {
        var clicked = false
        show(action = ProgressAction(label = ACTION, onClick = { clicked = true }))

        composeTestRule.onNodeWithText(ACTION, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag(TAG_PROGRESS_LINK, useUnmergedTree = true).performClick()
        assertTrue("the link must run its action", clicked)
    }

    @Test
    fun theLinkRowReachesTheMinimumTouchTarget() {
        show(action = ProgressAction(label = ACTION, onClick = {}))

        val node = composeTestRule
            .onNodeWithTag(TAG_PROGRESS_LINK, useUnmergedTree = true)
            .fetchSemanticsNode()
        val density = node.layoutInfo.density
        val width = with(density) { node.size.width.toDp() }
        val height = with(density) { node.size.height.toDp() }
        val minimum = SklyankaSizes.MinTouchTarget - SklyankaSizes.BorderThin
        assertTrue(
            "the link row is $width×$height, below ${SklyankaSizes.MinTouchTarget}",
            width >= minimum && height >= minimum,
        )
    }

    @Test
    fun withoutAnActionThereIsNoLinkRow() {
        show(action = null)

        composeTestRule.onNodeWithTag(TAG_PROGRESS_LINK, useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText(LABEL, useUnmergedTree = true).assertExists()
    }

    private fun show(action: ProgressAction?) {
        composeTestRule.setContent {
            SklyankaTheme {
                SklyankaProgressBar(progress = PROGRESS, label = LABEL, action = action)
            }
        }
    }

    private companion object {
        const val LABEL = "Taken 11 of 30 intakes"
        const val ACTION = "History"
        const val PROGRESS = 0.5f
    }
}
