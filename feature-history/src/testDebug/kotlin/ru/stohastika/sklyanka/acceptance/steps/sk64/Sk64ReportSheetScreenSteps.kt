package ru.stohastika.sklyanka.acceptance.steps.sk64

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.platform.app.InstrumentationRegistry
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.feature.history.HistoryIntent
import ru.stohastika.sklyanka.feature.history.HistoryScreen
import ru.stohastika.sklyanka.feature.history.HistoryStates
import ru.stohastika.sklyanka.feature.history.HistoryUiState
import ru.stohastika.sklyanka.feature.history.TAG_HISTORY_SCREEN
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_ACTION
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHEET
import ru.stohastika.sklyanka.ui.motion.MotionPhase
import ru.stohastika.sklyanka.ui.motion.motionTiming
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.R as UiR

@OptIn(ExperimentalTestApi::class)
class Sk64ReportSheetScreenSteps {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Допустим("^экран «История» собран на стенде SKL-64$")
    fun theHistoryScreenIsOnTheStand() = Unit

    @Когда("^человек открывает «Отчёт врачу»$")
    fun theReportOpens() = Unit

    @Когда("^человек закрывает «Отчёт врачу»$")
    fun theReportCloses() = Unit

    @Тогда("^в середине перехода видны и история, и шторка$")
    fun bothAreVisibleInTheMiddle() = runComposeUiTest {
        var state: HistoryUiState by mutableStateOf(HistoryStates.september())
        setContent {
            SklyankaTheme {
                HistoryScreen(state = state, onIntent = { intent -> state = state.after(intent) })
            }
        }

        onNodeWithTag(TAG_REPORT_ACTION).performScrollTo().performClick()
        mainClock.advanceTimeByFrame()
        waitForIdle()

        onAllNodesWithTag(TAG_HISTORY_SCREEN).assertCountEquals(1)
        onAllNodesWithTag(TAG_REPORT_SHEET).assertCountEquals(1)
    }

    @Тогда("^через (\\d+) ms шторка встала на место, а история осталась под затемнением$")
    fun onlyTheSheetStays(ms: Int) = runComposeUiTest {
        var state: HistoryUiState by mutableStateOf(HistoryStates.september())
        setContent {
            SklyankaTheme {
                HistoryScreen(state = state, onIntent = { intent -> state = state.after(intent) })
            }
        }
        onNodeWithTag(TAG_REPORT_ACTION).performScrollTo().performClick()
        mainClock.autoAdvance = true
        waitForIdle()

        val root = onRoot().fetchSemanticsNode()
        val sheet = onNodeWithTag(TAG_REPORT_SHEET).fetchSemanticsNode()
        // Настоящая нижняя шторка: во всю ширину, но высотой по содержимому — экран
        // под затемнением не исчезает (ОВ-125).
        assertEquals(root.size.width, sheet.size.width)
        assertTrue("the sheet must not fill the screen", sheet.size.height < root.size.height)
        onNodeWithTag(TAG_HISTORY_SCREEN).assertIsDisplayed()
        assertEquals(motionTiming(MotionPhase.SheetEnter).durationMillis, ms)
    }

    @Тогда("^через (\\d+) ms остаётся только история$")
    fun onlyTheHistoryStays(ms: Int) = runComposeUiTest {
        var state: HistoryUiState by mutableStateOf(HistoryStates.september())
        setContent {
            SklyankaTheme {
                HistoryScreen(state = state, onIntent = { intent -> state = state.after(intent) })
            }
        }
        onNodeWithTag(TAG_REPORT_ACTION).performScrollTo().performClick()
        mainClock.autoAdvance = true
        waitForIdle()

        onNodeWithText(context.getString(UiR.string.action_cancel)).performClick()
        waitForIdle()

        onAllNodesWithTag(TAG_REPORT_SHEET).assertCountEquals(0)
        onNodeWithTag(TAG_HISTORY_SCREEN).assertIsDisplayed()
        assertEquals(motionTiming(MotionPhase.SheetExit).durationMillis, ms)
    }

    private fun HistoryUiState.after(intent: HistoryIntent): HistoryUiState = when (intent) {
        HistoryIntent.ReportRequested -> HistoryStates.withReport()
        HistoryIntent.ReportDismissed -> copy(report = null)
        else -> this
    }
}
