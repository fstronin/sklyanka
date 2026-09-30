package ru.stohastika.sklyanka.startup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Кадры запуска на экранном стенде (SKL-42, ОВ-87).
 *
 * Проверяется наблюдаемое свойство механики: на экране ровно один кадр — или заставка,
 * или первый экран, и неготовый «Сегодня» (`Opening`) не показывается. Снятие системного
 * кадра — правило платформы Android 12+, и на JVM его нет; оно остаётся проверкой
 * устройства (`TDD.md` §3.1).
 *
 * Состав заставки здесь не проверяется — он закреплён `SplashScreenTest` и снимком
 * SKL-21.
 */
@RunWith(AndroidJUnit4::class)
class StartupFramesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theSplashFrameIsShownWhileTheFirstScreenIsNotReady() {
        composeTestRule.setContent {
            StartupFrames(frame = StartupFrame.AppSplash, firstScreen = { FirstScreen() })
        }

        composeTestRule.onNodeWithTag(TAG_SPLASH).assertIsDisplayed()
        assertEquals(
            "the first screen must not be composed under the splash",
            0,
            composeTestRule.onAllNodesWithTag(TAG_FIRST_SCREEN).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun theFirstScreenReplacesTheSplashWhenItIsReady() {
        composeTestRule.setContent {
            StartupFrames(frame = StartupFrame.FirstScreen, firstScreen = { FirstScreen() })
        }

        composeTestRule.onNodeWithTag(TAG_FIRST_SCREEN).assertIsDisplayed()
        assertEquals(
            "the splash must be gone the moment the first screen is ready",
            0,
            composeTestRule.onAllNodesWithTag(TAG_SPLASH).fetchSemanticsNodes().size,
        )
    }

    @Composable
    private fun FirstScreen() {
        Box(Modifier.fillMaxSize().testTag(TAG_FIRST_SCREEN))
    }

    private companion object {
        const val TAG_FIRST_SCREEN = "startup_first_screen"
    }
}
