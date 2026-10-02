package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class ReminderFadeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var closing by mutableStateOf(false)
    private val closed = AtomicBoolean(false)

    @Test
    fun `the canvas is readable while it fades in`() {
        show()

        composeRule.onNodeWithTag(TAG_CANVAS).assertIsDisplayed()
        composeRule.waitForIdle()

        assertFalse(closed.get())
    }

    @Test
    fun `the closing waits for the fade out and then reports`() {
        composeRule.mainClock.autoAdvance = true
        show()
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { closing = true }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        assertFalse(closed.get())
        composeRule.onNodeWithTag(TAG_CANVAS).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(150)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertTrue(closed.get())
    }

    private fun show() {
        composeRule.setContent {
            SklyankaTheme(dark = true) {
                ReminderFade(
                    visible = true,
                    closing = closing,
                    onClosed = { closed.set(true) },
                ) {
                    Box(Modifier.fillMaxSize().testTag(TAG_CANVAS))
                }
            }
        }
    }

    private companion object {
        const val TAG_CANVAS = "reminder_canvas"
    }
}
