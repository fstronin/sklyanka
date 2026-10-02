package ru.stohastika.sklyanka.acceptance.steps.sk64

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import ru.stohastika.sklyanka.feature.today.TodayFixtures
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import ru.stohastika.sklyanka.ui.motion.MotionPhase
import ru.stohastika.sklyanka.ui.motion.motionTiming
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

@OptIn(ExperimentalTestApi::class)
class Sk64SnackbarScreenSteps {

    @Допустим("^экран «Сегодня» собран на стенде SKL-64$")
    fun theTodayScreenIsOnTheStand() = Unit

    @Когда("^появляется последнее действие$")
    fun theLastActionAppears() = Unit

    @Когда("^последнее действие отменяют$")
    fun theLastActionIsCancelled() = Unit

    @Тогда("^снэкбар виден$")
    fun theSnackbarIsVisible() = runComposeUiTest {
        setContent {
            SklyankaTheme {
                TodayScreen(
                    state = TodayFixtures.plainDay().copy(undo = TodayFixtures.undoNotice()),
                    onIntent = {},
                )
            }
        }

        onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertIsDisplayed()
    }

    @Тогда("^через (\\d+) ms снэкбара нет$")
    fun theSnackbarIsGone(ms: Int) = runComposeUiTest {
        var state: TodayUiState by mutableStateOf(
            TodayFixtures.plainDay().copy(undo = TodayFixtures.undoNotice()),
        )
        setContent {
            SklyankaTheme {
                TodayScreen(state = state, onIntent = {})
            }
        }
        onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertIsDisplayed()

        runOnIdle { state = TodayFixtures.plainDay().copy(undo = null) }
        mainClock.autoAdvance = true
        waitForIdle()

        onAllNodesWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(motionTiming(MotionPhase.SnackbarExit).durationMillis, ms)
    }
}
