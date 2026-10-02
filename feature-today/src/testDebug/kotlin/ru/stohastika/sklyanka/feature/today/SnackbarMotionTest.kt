package ru.stohastika.sklyanka.feature.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

@RunWith(AndroidJUnit4::class)
class SnackbarMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private var state: TodayUiState by mutableStateOf(TodayFixtures.plainDay().copy(undo = null))

    @Test
    fun `the snackbar rises with the undo notice and leaves after cancelling`() {
        show()
        composeRule.onAllNodesWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertCountEquals(0)

        composeRule.runOnIdle {
            state = TodayFixtures.plainDay().copy(undo = TodayFixtures.undoNotice())
        }
        composeRule.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.today_undo_action), useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun show() {
        composeRule.setContent {
            SklyankaTheme {
                TodayScreen(
                    state = state,
                    onIntent = { intent ->
                        if (intent is TodayIntent.UndoMark) {
                            state = (state as TodayUiState.Content).copy(undo = null)
                        }
                    },
                )
            }
        }
    }
}
