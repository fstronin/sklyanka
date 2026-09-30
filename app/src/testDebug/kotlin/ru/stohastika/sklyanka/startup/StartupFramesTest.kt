package ru.stohastika.sklyanka.startup

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * Проверяются два наблюдаемых свойства механики: на экране ровно один кадр — или
 * заставка, или первый экран, — и системный кадр снимается по **отрисовке** первого
 * кадра приложения, а не по композиции. Второе доказывается настоящей отрисовкой
 * корня: до неё оповещения нет, после — ровно одно, повторная отрисовка его не повторяет.
 *
 * Состав заставки здесь не проверяется — он закреплён `SplashScreenTest` и снимком
 * SKL-21.
 */
@RunWith(AndroidJUnit4::class)
class StartupFramesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theSplashFrameIsShownWhileTheFirstScreenIsNotReady() {
        composeTestRule.setContent {
            StartupFrames(frame = StartupFrame.AppSplash, onFirstFrameDrawn = {}, firstScreen = { FirstScreen() })
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
            StartupFrames(frame = StartupFrame.FirstScreen, onFirstFrameDrawn = {}, firstScreen = { FirstScreen() })
        }

        composeTestRule.onNodeWithTag(TAG_FIRST_SCREEN).assertIsDisplayed()
        assertEquals(
            "the splash must be gone the moment the first screen is ready",
            0,
            composeTestRule.onAllNodesWithTag(TAG_SPLASH).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun theFirstOwnFrameIsReportedOnceAfterDrawing() {
        var reported = 0
        composeTestRule.setContent {
            StartupFrames(
                frame = StartupFrame.AppSplash,
                onFirstFrameDrawn = { reported++ },
                firstScreen = { FirstScreen() },
            )
        }
        composeTestRule.waitForIdle()
        assertEquals("composition alone must not report the frame", 0, reported)

        drawRoot()
        assertEquals("the drawn frame must be reported exactly once", 1, reported)

        drawRoot()
        assertEquals("a repeated draw must not report the frame again", 1, reported)
    }

    /** Отрисовать корень так, как это делает система: одной настоящей отрисовкой. */
    private fun drawRoot() {
        val view = composeTestRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
    }

    @Composable
    private fun FirstScreen() {
        Box(Modifier.fillMaxSize().testTag(TAG_FIRST_SCREEN))
    }

    private companion object {
        const val TAG_FIRST_SCREEN = "startup_first_screen"
    }
}
