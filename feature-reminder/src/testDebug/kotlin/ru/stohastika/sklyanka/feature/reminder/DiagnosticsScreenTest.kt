package ru.stohastika.sklyanka.feature.reminder

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.ReminderDiagnostics
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalExportOutcome
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.ReminderOnTimeSummary
import ru.stohastika.sklyanka.domain.reminder.ReminderReliability
import ru.stohastika.sklyanka.domain.reminder.ReminderWeekSummary
import ru.stohastika.sklyanka.domain.reminder.SuppressReason
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
// видела бы только первый уровень. Язык — часть условий: подписи со склонениями читаются
// по правилам русского, а не языка процесса (НФТ-44).
@Config(qualifiers = "ru-rRU-w390dp-h4000dp-mdpi")
class DiagnosticsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun snapshot(
        reliable: Boolean = true,
        permissions: Map<ReminderPermission, PermissionState> = grantedAll(),
        week: ReminderWeekSummary = ReminderWeekSummary(fired = 12, missed = 2),
        onTime: ReminderOnTimeSummary = ReminderOnTimeSummary(fired = 13, onTime = 12, days = 7),
        measurementAvailable: Boolean = false,
        alarmKind: AlarmKind = AlarmKind.ALARM_CLOCK,
        nextAt: Instant? = null,
        deliveryCheck: DeliveryCheckState = DeliveryCheckState.None,
    ) = ReminderDiagnostics(
        lastShown = null,
        lastMedicationName = null,
        nextAt = nextAt,
        nextIntakeId = null,
        nextMedicationName = null,
        totalScheduled = 0,
        lastReschedule = null,
        permissions = permissions,
        reliability = ReminderReliability(
            notificationsEnabled = reliable,
            channelEnabled = reliable,
            exactAlarmsAllowed = reliable,
            fullScreenIntentAllowed = reliable,
        ),
        outage = null,
        onTime = onTime,
        week = week,
        journal = emptyList(),
        alarmKind = alarmKind,
        measurementAvailable = measurementAvailable,
        deliveryCheck = deliveryCheck,
    )

    private fun show(
        snapshot: ReminderDiagnostics = snapshot(),
        supportExpanded: Boolean = false,
        checkOutcome: ReminderCheckOutcome? = null,
        outcome: ReminderJournalExportOutcome? = null,
        fixFallback: ReminderPermission? = null,
        checkSheetOpen: Boolean = false,
        onToggleSupport: () -> Unit = {},
        onFixPermission: (ReminderPermission) -> Unit = {},
        onExportJournal: () -> Unit = {},
        onAlarmKindChange: (AlarmKind) -> Unit = {},
        onCheckDelivery: () -> Unit = {},
        onDismissCheckSheet: () -> Unit = {},
        onStartDeliveryCheck: () -> Unit = {},
        onCancelDeliveryCheck: () -> Unit = {},
        onRepeatDeliveryCheck: () -> Unit = {},
        onFixDeliveryCheck: () -> Unit = {},
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
                        fixFallback = fixFallback,
                        checkSheetOpen = checkSheetOpen,
                    ),
                    onCheckNow = {},
                    onExportJournal = onExportJournal,
                    onRestartSetup = null,
                    onToggleSupport = onToggleSupport,
                    onFixPermission = onFixPermission,
                    onAlarmKindChange = onAlarmKindChange,
                    onCheckDelivery = onCheckDelivery,
                    onDismissCheckSheet = onDismissCheckSheet,
                    onStartDeliveryCheck = onStartDeliveryCheck,
                    onCancelDeliveryCheck = onCancelDeliveryCheck,
                    onRepeatDeliveryCheck = onRepeatDeliveryCheck,
                    onFixDeliveryCheck = onFixDeliveryCheck,
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

    @Test
    fun aDisabledChannelIsNamedInTheWarnings() {
        show(
            snapshot = snapshot(
                permissions = grantedAll() + (ReminderPermission.NOTIFICATION_CHANNEL to PermissionState.DENIED),
            ).copy(
                reliability = ReminderReliability(
                    notificationsEnabled = true,
                    channelEnabled = false,
                    exactAlarmsAllowed = true,
                    fullScreenIntentAllowed = true,
                ),
            ),
        )

        compose.onNodeWithTag(TAG_WARNING, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_warning_channel), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun aMissingFullScreenRightIsNamedInTheWarnings() {
        show(
            snapshot = snapshot(
                permissions = grantedAll() + (ReminderPermission.FULL_SCREEN_INTENT to PermissionState.DENIED),
            ).copy(
                reliability = ReminderReliability(
                    notificationsEnabled = true,
                    channelEnabled = true,
                    exactAlarmsAllowed = true,
                    fullScreenIntentAllowed = false,
                ),
            ),
        )

        compose.onNodeWithText(context.getString(R.string.diagnostics_warning_full_screen), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun aFailedFixShowsTheWrittenInstructionsUnderTheRow() {
        val permissions = grantedAll() + (ReminderPermission.AUTOSTART to PermissionState.UNKNOWN)
        show(
            snapshot = snapshot(permissions = permissions),
            fixFallback = ReminderPermission.AUTOSTART,
        )

        // Системный экран не открылся — экран не молчит: под строкой остаётся ручной путь (НФТ-8).
        compose.onNodeWithTag(TAG_FIX_FALLBACK, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_fallback_app_startup), useUnmergedTree = true)
            .assertExists()
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

    /** Подавленное по порогу срабатывание названо словами, а не безликим «показ подавлен» (ОВ-109). */
    @Test
    fun aSuppressedLateFiringIsSignedInTheJournal() {
        val entry = suppressed(SuppressReason.TOO_LATE, 3_600)
        show(snapshot = snapshot().copy(journal = listOf(entry)), supportExpanded = true)

        compose.onNodeWithText("сработало с опозданием на 60 минут — не показано").assertExists()
        compose.onNodeWithText("Отклонение 3600 с").assertExists()
    }

    /** Обычная сборка переключателя не показывает вовсе: планирование им не затрагивается (ПМ-20). */
    @Test
    fun theMeasurementSwitchIsAbsentInANormalBuild() {
        show(supportExpanded = true)

        compose.onNodeWithTag(TAG_MEASUREMENT).assertDoesNotExist()
    }

    @Test
    fun theMeasuringBuildShowsTheVariantAndSwitchesIt() {
        var chosen: AlarmKind? = null
        show(
            snapshot = snapshot(measurementAvailable = true, alarmKind = AlarmKind.ALARM_CLOCK),
            supportExpanded = true,
            onAlarmKindChange = { chosen = it },
        )

        compose.onNodeWithTag(TAG_MEASUREMENT).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Системный будильник").assertExists()
        compose.onNodeWithText("Точный будильник").performScrollTo().performClick()

        assertEquals(AlarmKind.EXACT_IDLE, chosen)
    }

    @Test
    fun theAlarmClockIndicatorIsExplainedUnderTheNextReminder() {
        show(
            snapshot = snapshot(nextAt = Instant.parse("2026-09-26T05:00:00Z")),
            supportExpanded = true,
        )

        compose.onNodeWithTag(TAG_NEXT).performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("Это время телефон показывает в статус-баре как будильник — так и должно быть.")
            .assertExists()
    }

    @Test
    fun otherSuppressionsKeepTheirOwnLabel() {
        show(
            snapshot = snapshot().copy(journal = listOf(suppressed(SuppressReason.WINDOW_EXPIRED, null))),
            supportExpanded = true,
        )

        compose.onNodeWithText("показ подавлен").assertExists()
    }

    // --- Самопроверка доставки (SKL-86, ОВ-108) ------------------------------------------------

    @Test
    fun theCheckBlockOffersTheDeliveryCheckUnderTheCheckNowButton() {
        show()

        compose.onNodeWithTag(TAG_CHECK_NOW).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_CHECK_DELIVERY).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_action_check_delivery)).assertExists()
    }

    @Test
    fun aRunningCheckShowsTheInstructionAndTheQuietCancel() {
        val dueAt = Instant.parse("2026-09-25T07:01:30Z")
        show(snapshot = snapshot(deliveryCheck = DeliveryCheckState.Running(dueAt)))

        compose.onNodeWithTag(TAG_CHECK_RUNNING).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_check_running)).assertExists()
        compose.onNodeWithTag(TAG_CHECK_CANCEL).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_CHECK_DELIVERY).assertDoesNotExist()
    }

    @Test
    fun aSuccessfulCheckOffersToRepeatIt() {
        show(snapshot = snapshot(deliveryCheck = finished(DeliveryCheckOutcome.ON_TIME)))

        compose.onNodeWithTag(TAG_CHECK_RESULT).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_check_on_time)).assertExists()
        compose.onNodeWithTag(TAG_CHECK_REPEAT).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aLateCheckNamesTheMinutesAndOffersAFix() {
        show(
            snapshot = snapshot(
                deliveryCheck = finished(DeliveryCheckOutcome.LATE, lateMinutes = 12),
            ),
        )

        compose
            .onNodeWithText(
                context.getString(
                    R.string.diagnostics_check_late,
                    context.resources.getQuantityString(R.plurals.diagnostics_late, 12, 12),
                ),
            )
            .assertExists()
        compose.onNodeWithTag(TAG_CHECK_FIX).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aCheckThatNeverArrivedOffersAFix() {
        show(snapshot = snapshot(deliveryCheck = finished(DeliveryCheckOutcome.NOT_DELIVERED)))

        compose.onNodeWithText(context.getString(R.string.diagnostics_check_not_delivered)).assertExists()
        compose.onNodeWithTag(TAG_CHECK_FIX).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aCheckWithTheAppOpenSaysSoAndOffersToRepeatIt() {
        show(snapshot = snapshot(deliveryCheck = finished(DeliveryCheckOutcome.NOT_COMPLETED)))

        compose.onNodeWithText(context.getString(R.string.diagnostics_check_not_completed)).assertExists()
        compose.onNodeWithTag(TAG_CHECK_REPEAT).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theCheckSheetCarriesTheTitleInstructionAndBothActions() {
        show(checkSheetOpen = true)

        compose.onNodeWithTag(TAG_CHECK_SHEET).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_check_sheet_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.diagnostics_check_sheet_text)).assertExists()
        compose.onNodeWithTag(TAG_CHECK_START).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CHECK_SHEET_CANCEL).assertIsDisplayed()
    }

    @Test
    fun theSheetActionsReachTheirOwners() {
        var started = 0
        var dismissed = 0
        show(
            checkSheetOpen = true,
            onStartDeliveryCheck = { started += 1 },
            onDismissCheckSheet = { dismissed += 1 },
        )

        compose.onNodeWithTag(TAG_CHECK_START).performClick()
        compose.onNodeWithTag(TAG_CHECK_SHEET_CANCEL).performClick()

        assertEquals(1, started)
        assertEquals(1, dismissed)
    }

    @Test
    fun theBlockFixActionReachesItsOwner() {
        var fixed = 0
        show(
            snapshot = snapshot(deliveryCheck = finished(DeliveryCheckOutcome.NOT_DELIVERED)),
            onFixDeliveryCheck = { fixed += 1 },
        )

        compose.onNodeWithTag(TAG_CHECK_FIX).performScrollTo().performClick()

        assertEquals(1, fixed)
    }

    @Test
    fun theBlockCancelActionReachesItsOwner() {
        var cancelled = 0
        show(
            snapshot = snapshot(deliveryCheck = DeliveryCheckState.Running(Instant.parse("2026-09-25T07:01:30Z"))),
            onCancelDeliveryCheck = { cancelled += 1 },
        )

        compose.onNodeWithTag(TAG_CHECK_CANCEL).performScrollTo().performClick()

        assertEquals(1, cancelled)
    }

    @Test
    fun theBlockRepeatActionReachesItsOwner() {
        var repeated = 0
        show(
            snapshot = snapshot(deliveryCheck = finished(DeliveryCheckOutcome.ON_TIME)),
            onRepeatDeliveryCheck = { repeated += 1 },
        )

        compose.onNodeWithTag(TAG_CHECK_REPEAT).performScrollTo().performClick()

        assertEquals(1, repeated)
    }

    @Test
    fun theBlockCheckActionReachesItsOwner() {
        var checked = 0
        show(onCheckDelivery = { checked += 1 })

        compose.onNodeWithTag(TAG_CHECK_DELIVERY).performScrollTo().performClick()

        assertEquals(1, checked)
    }

    private fun finished(outcome: DeliveryCheckOutcome, lateMinutes: Long? = null) = DeliveryCheckState.Finished(
        outcome = outcome,
        dueAt = Instant.parse("2026-09-25T07:01:30Z"),
        resolvedAt = Instant.parse("2026-09-25T07:02:00Z"),
        lateMinutes = lateMinutes,
    )

    private fun suppressed(reason: SuppressReason, deviation: Long?) = ReminderLogEntry(
        occurredAt = Instant.parse("2026-09-25T12:00:00Z"),
        event = ReminderEvent.SUPPRESSED,
        intakeId = "intake-1",
        scheduledAt = Instant.parse("2026-09-25T11:00:00Z"),
        firedAt = Instant.parse("2026-09-25T12:00:00Z"),
        deviationSeconds = deviation,
        delivered = false,
        reason = reason.code,
    )

    private companion object {
        fun grantedAll(): Map<ReminderPermission, PermissionState> =
            ReminderPermission.entries.associateWith { PermissionState.GRANTED }
    }
}
