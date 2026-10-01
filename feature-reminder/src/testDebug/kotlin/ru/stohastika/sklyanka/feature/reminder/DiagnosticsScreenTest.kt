package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.reminder.ReminderDiagnostics
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalExportOutcome
import ru.stohastika.sklyanka.domain.reminder.ReminderOnTimeSummary
import ru.stohastika.sklyanka.domain.reminder.ReminderReliability
import ru.stohastika.sklyanka.domain.reminder.ReminderWeekSummary
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.Instant
import java.time.ZoneId

/**
 * Экран проверки напоминаний: два уровня и честность первого (SKL-79, ОВ-49).
 *
 * Идёт на JVM под Robolectric: состав экрана и подписи устройства не требуют. Чего прогон
 * не доказывает — что «Исправить» открывает нужный системный экран и что файл выгрузки
 * запишется: это чужое и проверяется на устройстве.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: техблок «Для поддержки» иначе не собирается, и проверка
// видела бы только первый уровень.
@Config(qualifiers = "w390dp-h4000dp-mdpi")
class DiagnosticsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun snapshot(
        reliable: Boolean = true,
        permissions: Map<ReminderPermission, PermissionState> = grantedAll(),
        week: ReminderWeekSummary = ReminderWeekSummary(fired = 12, missed = 2),
        onTime: ReminderOnTimeSummary = ReminderOnTimeSummary(fired = 13, onTime = 12, days = 7),
    ) = ReminderDiagnostics(
        lastShown = null,
        lastMedicationName = null,
        nextAt = null,
        nextIntakeId = null,
        nextMedicationName = null,
        totalScheduled = 0,
        lastReschedule = null,
        permissions = permissions,
        reliability = ReminderReliability(
            notificationsEnabled = reliable,
            exactAlarmsAllowed = reliable,
        ),
        outage = null,
        onTime = onTime,
        week = week,
        journal = emptyList(),
    )

    private fun show(
        snapshot: ReminderDiagnostics = snapshot(),
        supportExpanded: Boolean = false,
        checkOutcome: ReminderCheckOutcome? = null,
        outcome: ReminderJournalExportOutcome? = null,
        onToggleSupport: () -> Unit = {},
        onFixPermission: (ReminderPermission) -> Unit = {},
        onExportJournal: () -> Unit = {},
    ) {
        compose.setContent {
            SklyankaTheme {
                DiagnosticsScreen(
                    state = DiagnosticsUiState(
                        snapshot = snapshot,
                        zone = zone,
                        exportFileName = "sklyanka-reminder-journal-2026-09-25.csv",
                        exportOutcome = outcome,
                        supportExpanded = supportExpanded,
                        checkOutcome = checkOutcome,
                    ),
                    onCheckNow = {},
                    onExportJournal = onExportJournal,
                    onRestartSetup = null,
                    onToggleSupport = onToggleSupport,
                    onFixPermission = onFixPermission,
                    onBack = {},
                    toLocal = { instant: Instant -> instant.atZone(zone).toLocalDateTime() },
                )
            }
        }
    }

    @Test
    fun theFirstLevelIsShortWhenEverythingIsFine() {
        show()

        compose.onNodeWithTag(TAG_RELIABLE).assertIsDisplayed()
        compose.onNodeWithTag(TAG_ALL_GRANTED).assertIsDisplayed()
    }

    /** Технический отчёт не виден на первом уровне: журнала и счётчиков там нет (ОВ-49). */
    @Test
    fun theTechnicalReportIsHiddenOnTheFirstLevel() {
        show(snapshot = snapshot().copy(lastReschedule = null, journal = emptyList()))

        compose.onNodeWithTag(TAG_SUPPORT_TOGGLE).assertIsDisplayed()
        compose.onNodeWithTag(TAG_LAST_SHOWN).assertDoesNotExist()
        compose.onNodeWithTag(TAG_RESCHEDULE).assertDoesNotExist()
        compose.onNodeWithTag(TAG_JOURNAL_ROW).assertDoesNotExist()
    }

    @Test
    fun expandingSupportShowsTheJournalAndTheCounters() {
        show(supportExpanded = true)

        compose.onNodeWithTag(TAG_LAST_SHOWN).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_RESCHEDULE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_ON_TIME).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_EXPORT_JOURNAL).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingTheSupportToggleAsksForAnExpansion() {
        var toggled = 0
        show(onToggleSupport = { toggled += 1 })

        compose.onNodeWithTag(TAG_SUPPORT_TOGGLE).performScrollTo().performClick()

        assertEquals(1, toggled)
    }

    @Test
    fun aMissingPermissionIsNamedAndOffersAFix() {
        var fixed: ReminderPermission? = null
        val permissions = grantedAll() + (ReminderPermission.NOTIFICATIONS to PermissionState.DENIED)
        show(
            snapshot = snapshot(permissions = permissions),
            onFixPermission = { fixed = it },
        )

        assertTrue(
            "a missing permission must have its own row",
            compose.onAllNodesWithTag(TAG_PERMISSION_ROW, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
        compose.onAllNodesWithTag(TAG_FIX)[0].performScrollTo().performClick()

        assertEquals(ReminderPermission.NOTIFICATIONS, fixed)
    }

    /** Автозапуск EMUI программно не читается: зелёной галочки здесь быть не может (НАП-20). */
    @Test
    fun theAutostartRowSaysItIsCheckedByHand() {
        show(
            snapshot = snapshot(
                permissions = grantedAll() + (ReminderPermission.AUTOSTART to PermissionState.UNKNOWN),
            ),
        )

        compose.onNodeWithText("Автозапуск и энергосбережение").assertExists()
    }

    @Test
    fun theWeekSummaryIsAWholePhrase() {
        show()

        compose.onNodeWithTag(TAG_WEEK_SUMMARY).assertIsDisplayed()
        compose.onNodeWithText("За 7 дней: сработало 12, пропущено 2").assertIsDisplayed()
    }

    @Test
    fun aFailedCheckIsSaidOutLoud() {
        show(checkOutcome = ReminderCheckOutcome.FAILED)

        compose.onNodeWithTag(TAG_CHECK_OUTCOME).assertIsDisplayed()
        compose.onNodeWithText("Проверить не удалось: напоминания не перепланированы").assertIsDisplayed()
    }

    @Test
    fun aSuccessfulCheckIsSaidOutLoud() {
        show(checkOutcome = ReminderCheckOutcome.SUCCESS)

        compose.onNodeWithText("Проверка выполнена — напоминания поставлены").assertIsDisplayed()
    }

    @Test
    fun theJournalCanBeExportedFromTheSupportBlock() {
        var requested = 0
        show(supportExpanded = true, onExportJournal = { requested += 1 })

        compose.onNodeWithTag(TAG_EXPORT_JOURNAL).performScrollTo().performClick()

        assertEquals(1, requested)
    }

    @Test
    fun theOutcomeOfAnExportIsNeverSilent() {
        show(supportExpanded = true, outcome = ReminderJournalExportOutcome.Saved(records = 12, bytes = 2_048))

        compose.onNodeWithTag(TAG_EXPORT_OUTCOME).performScrollTo().assertIsDisplayed()
    }

    private companion object {
        fun grantedAll(): Map<ReminderPermission, PermissionState> =
            ReminderPermission.entries.associateWith { PermissionState.GRANTED }
    }
}
