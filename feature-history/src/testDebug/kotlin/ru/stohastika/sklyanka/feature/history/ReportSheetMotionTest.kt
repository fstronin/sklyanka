package ru.stohastika.sklyanka.feature.history

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
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.R as UiR

@RunWith(AndroidJUnit4::class)
class ReportSheetMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private var state: HistoryUiState by mutableStateOf(HistoryStates.september())

    @Test
    fun `the report sheet slides in over the history and leaves after cancelling`() {
        show()
        composeRule.onAllNodesWithTag(TAG_REPORT_SHEET).assertCountEquals(0)

        composeRule.onNodeWithTag(TAG_REPORT_ACTION).performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_REPORT_SHEET).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(UiR.string.action_cancel)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(TAG_REPORT_SHEET).assertCountEquals(0)

        composeRule.onNodeWithTag(TAG_REPORT_ACTION).performScrollTo().performClick()
        composeRule.onNodeWithTag(TAG_REPORT_SHEET).assertIsDisplayed()
    }

    private fun show() {
        composeRule.setContent {
            SklyankaTheme {
                HistoryScreen(
                    state = state,
                    onIntent = { intent ->
                        when (intent) {
                            HistoryIntent.ReportRequested -> state = HistoryStates.withReport()
                            HistoryIntent.ReportDismissed -> state = state.copy(report = null)
                            else -> Unit
                        }
                    },
                )
            }
        }
    }
}
