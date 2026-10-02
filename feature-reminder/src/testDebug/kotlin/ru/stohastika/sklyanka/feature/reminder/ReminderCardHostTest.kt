package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class ReminderCardHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val closed = AtomicBoolean(false)
    private var state: ReminderUiState by mutableStateOf(card())

    @Test
    fun `the card stays in composition while the screen fades out`() {
        show()
        composeRule.onNodeWithTag(TAG_REMINDER).assertIsDisplayed()
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { state = ReminderUiState.Closed }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_REMINDER).assertIsDisplayed()
        assertFalse(closed.get())

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertTrue(closed.get())
    }

    @Test
    fun `loading shows no card and does not close`() {
        state = ReminderUiState.Loading
        show()

        composeRule.onAllNodesWithTag(TAG_REMINDER).assertCountEquals(0)
        assertFalse(closed.get())
    }

    private fun show() {
        composeRule.setContent {
            SklyankaTheme(dark = true) {
                ReminderCardHost(state = state, actions = actions, onClosed = { closed.set(true) })
            }
        }
    }

    private fun card(): ReminderUiState.Card = ReminderUiState.Card(
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

    private val actions = ReminderActions(
        onTaken = {},
        onSkipped = {},
        onTakenAll = {},
        onSkippedAll = {},
        onSnooze = {},
    )
}
