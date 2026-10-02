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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.feature.reminder.ReminderActions
import ru.stohastika.sklyanka.feature.reminder.ReminderCardHost
import ru.stohastika.sklyanka.feature.reminder.ReminderRow
import ru.stohastika.sklyanka.feature.reminder.ReminderUiState
import ru.stohastika.sklyanka.feature.reminder.TAG_REMINDER
import ru.stohastika.sklyanka.ui.motion.MotionPhase
import ru.stohastika.sklyanka.ui.motion.motionTiming
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalTestApi::class)
class Sk64ReminderScreenSteps {

    @Допустим("^полноэкранное напоминание собрано на стенде SKL-64$")
    fun theReminderIsOnTheStand() = Unit

    @Когда("^проходит (\\d+) ms$")
    fun theTimePasses(ms: Int) = Unit

    @Когда("^напоминание закрывается$")
    fun theReminderCloses() = Unit

    @Тогда("^напоминание показано$")
    fun theReminderIsShown() = runComposeUiTest {
        setContent {
            SklyankaTheme(dark = true) {
                ReminderCardHost(state = card(), actions = actions, onClosed = {})
            }
        }
        mainClock.advanceTimeByFrame()
        waitForIdle()

        onNodeWithTag(TAG_REMINDER).assertIsDisplayed()
        assertEquals(300, motionTiming(MotionPhase.ReminderEnter).durationMillis)
    }

    @Тогда("^через (\\d+) ms оно (не закрыто|закрыто)$")
    fun theReminderStateAfter(ms: Int, state: String) = runComposeUiTest {
        val closed = AtomicBoolean(false)
        var current: ReminderUiState by mutableStateOf(card())
        setContent {
            SklyankaTheme(dark = true) {
                ReminderCardHost(state = current, actions = actions, onClosed = { closed.set(true) })
            }
        }
        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag(TAG_REMINDER).assertIsDisplayed()

        mainClock.autoAdvance = false
        runOnIdle { current = ReminderUiState.Closed }
        waitForIdle()
        mainClock.advanceTimeByFrame()
        waitForIdle()

        onNodeWithTag(TAG_REMINDER).assertIsDisplayed()

        mainClock.advanceTimeBy(ms.toLong())
        waitForIdle()
        if (state == "закрыто") {
            var extra = 0
            while (!closed.get() && extra < 200) {
                mainClock.advanceTimeByFrame()
                waitForIdle()
                extra += 16
            }
            assertTrue(closed.get())
            assertEquals(motionTiming(MotionPhase.ReminderExit).durationMillis, ms)
        } else {
            assertFalse(closed.get())
            assertEquals(motionTiming(MotionPhase.ReminderExit).durationMillis - 1, ms)
        }

        onAllNodesWithTag(TAG_REMINDER).assertCountEquals(1)
    }

    private companion object {
        val actions = ReminderActions(
            onTaken = {},
            onSkipped = {},
            onTakenAll = {},
            onSkippedAll = {},
            onSnooze = {},
        )

        fun card(): ReminderUiState.Card = ReminderUiState.Card(
            scheduledAt = LocalDateTime.of(2026, 9, 25, 14, 0),
            items = listOf(
                ReminderRow(
                    intakeId = "intake-1",
                    name = "Магний B6",
                    form = MedicationForm.TABLET,
                    doseAmount = BigDecimal("2"),
                    strength = BigDecimal("48"),
                    strengthUnit = "мг",
                ),
            ),
            snoozeMinutes = 15,
            mirroredToWatch = false,
            foreignProfileName = null,
        )
    }
}
