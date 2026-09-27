package ru.heuri.sklyanka.feature.reminder

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.domain.reminder.ReminderDiagnostics
import ru.heuri.sklyanka.domain.reminder.ReminderJournalExportOutcome
import ru.heuri.sklyanka.domain.reminder.ReminderOnTimeSummary
import ru.heuri.sklyanka.domain.reminder.ReminderReliability
import ru.heuri.sklyanka.domain.reminder.port.PermissionState
import ru.heuri.sklyanka.domain.reminder.port.ReminderPermission
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import java.time.Instant
import java.time.ZoneId

/**
 * Состав экрана диагностики в части, добавленной по НАП-37 и НФТ-51: сводка «вовремя
 * за 7 дней», выгрузка журнала файлом и вход в настройку заново.
 *
 * Идёт на JVM под Robolectric (стенд SKL-24): состав экрана и подписи кнопок устройства
 * не требуют. Чего этот прогон **не** доказывает — что системный выбор места действительно
 * откроется и что файл запишется: это чужая активность и чужое хранилище, и проверяются
 * они на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun snapshot(onTime: ReminderOnTimeSummary) = ReminderDiagnostics(
        lastShown = null,
        nextAt = null,
        nextIntakeId = null,
        totalScheduled = 0,
        lastReschedule = null,
        permissions = ReminderPermission.entries.associateWith { PermissionState.GRANTED },
        reliability = ReminderReliability(notificationsEnabled = true, exactAlarmsAllowed = true),
        outage = null,
        onTime = onTime,
        journal = emptyList(),
    )

    private fun show(
        onTime: ReminderOnTimeSummary = ReminderOnTimeSummary(fired = 13, onTime = 12, days = 7),
        outcome: ReminderJournalExportOutcome? = null,
        onExportJournal: () -> Unit = {},
        onRestartSetup: (() -> Unit)? = {},
    ) {
        compose.setContent {
            SklyankaTheme {
                DiagnosticsScreen(
                    state = DiagnosticsUiState(
                        snapshot = snapshot(onTime),
                        zone = zone,
                        exportFileName = "sklyanka-reminder-journal-2026-09-25.csv",
                        exportOutcome = outcome,
                    ),
                    onCheckNow = {},
                    onTestReminder = {},
                    onExportJournal = onExportJournal,
                    onRestartSetup = onRestartSetup,
                    onBack = {},
                    toLocal = { instant: Instant -> instant.atZone(zone).toLocalDateTime() },
                )
            }
        }
    }

    @Test
    fun theSevenDaySummaryIsAWholeLineWithTheCountBesideTheShare() {
        show()

        compose.onNodeWithTag(TAG_ON_TIME).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Вовремя за 7 дней").assertIsDisplayed()
        // Число срабатываний рядом с долей: «100 %» на одном срабатывании ничего не доказывает.
        compose.onNodeWithText("12 из 13 · 92 %").assertIsDisplayed()
    }

    @Test
    fun withoutFiringsTheSummarySaysSoInsteadOfShowingZero() {
        show(onTime = ReminderOnTimeSummary(fired = 0, onTime = 0, days = 7))

        compose.onNodeWithText("Срабатываний за это время не было").assertIsDisplayed()
    }

    @Test
    fun theJournalCanBeExportedToAFile() {
        var requested = 0
        show(onExportJournal = { requested += 1 })

        compose.onNodeWithTag(TAG_EXPORT_JOURNAL).performScrollTo().performClick()

        assertEquals(1, requested)
    }

    @Test
    fun theOutcomeOfAnExportIsNeverSilent() {
        show(outcome = ReminderJournalExportOutcome.Saved(records = 12, bytes = 2_048))

        compose.onNodeWithTag(TAG_EXPORT_OUTCOME).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Журнал выгружен · 12 записей").assertIsDisplayed()
    }

    @Test
    fun aFailedExportIsSaidOutLoud() {
        show(outcome = ReminderJournalExportOutcome.Failed)

        compose.onNodeWithText("Журнал не выгружен: запись не удалась").assertIsDisplayed()
    }

    @Test
    fun theSetupCanBeTakenAgainFromHere() {
        var opened = 0
        show(onRestartSetup = { opened += 1 })

        compose.onNodeWithTag(TAG_RESTART_SETUP).performScrollTo().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun withoutASetupScreenThereIsNoButtonLeadingNowhere() {
        show(onRestartSetup = null)

        compose.onNodeWithTag(TAG_RESTART_SETUP).assertDoesNotExist()
    }
}
