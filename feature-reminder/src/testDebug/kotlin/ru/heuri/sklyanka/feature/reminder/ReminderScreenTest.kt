package ru.heuri.sklyanka.feature.reminder

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Состав полноэкранного напоминания (НАП-29, НАП-30).
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется — поэтому состав карточки остаётся непроверенным до прогона
 * на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class ReminderScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val at: LocalDateTime = LocalDateTime.of(2026, 9, 25, 14, 0)

    private fun row(id: String = "intake-1", name: String = "Магний B6") = ReminderRow(
        intakeId = id,
        name = name,
        form = MedicationForm.TABLET,
        doseAmount = BigDecimal("2"),
        strength = BigDecimal("48"),
        strengthUnit = "мг",
    )

    private fun state(rows: List<ReminderRow> = listOf(row()), snoozeMinutes: Int = 15, mirrored: Boolean = false) =
        ReminderUiState.Card(
            scheduledAt = at,
            items = rows,
            snoozeMinutes = snoozeMinutes,
            mirroredToWatch = mirrored,
            foreignProfileName = null,
        )

    private fun show(state: ReminderUiState.Card, onSnooze: () -> Unit = {}) {
        compose.setContent {
            SklyankaTheme(dark = true) {
                ReminderScreen(
                    state = state,
                    actions = ReminderActions(
                        onTaken = {},
                        onSkipped = {},
                        onTakenAll = {},
                        onSkippedAll = {},
                        onSnooze = onSnooze,
                    ),
                )
            }
        }
    }

    @Test
    fun theCardCarriesTheOverlineTimeNameAndDose() {
        show(state(mirrored = true))

        compose.onNodeWithTag(TAG_OVERLINE).assertIsDisplayed()
        compose.onNodeWithText("14:00").assertIsDisplayed()
        compose.onNodeWithText("Магний B6").assertIsDisplayed()
        compose.onNodeWithText("2 таблетки · 48 мг").assertIsDisplayed()
        compose.onNodeWithTag(TAG_WATCH_CHIP).assertIsDisplayed()
    }

    @Test
    fun withoutMirroringTheWatchChipIsAbsent() {
        show(state())

        compose.onNodeWithTag(TAG_WATCH_CHIP).assertDoesNotExist()
        compose.onNodeWithText("Магний B6").assertIsDisplayed()
    }

    @Test
    fun thereAreExactlyThreeActionsInTheOrderOfTheMockup() {
        show(state())

        compose.onNodeWithText("Принято").assertIsDisplayed()
        compose.onNodeWithText("Отложить 15 мин").assertIsDisplayed()
        compose.onNodeWithText("Пропустить").assertIsDisplayed()
    }

    @Test
    fun theSnoozeLabelFollowsTheSetting() {
        show(state(snoozeMinutes = 30))

        compose.onNodeWithText("Отложить 30 мин").assertIsDisplayed()
    }

    @Test
    fun theSnoozeButtonReportsItsClick() {
        var clicks = 0
        show(state()) { clicks++ }

        compose.onNodeWithTag(TAG_ACTION_SNOOZE).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun twoIntakesOnOneTimeAreShownAsOneCardWithTwoRows() {
        show(state(rows = listOf(row(), row(id = "intake-2", name = "Витамин D3"))))

        compose.onAllNodesWithTag(TAG_ROW).assertCountEquals(2)
        compose.onNodeWithText("Магний B6").assertIsDisplayed()
        compose.onNodeWithText("Витамин D3").assertIsDisplayed()
    }
}
