package ru.stohastika.sklyanka.feature.reminder

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
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
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.ReminderOnTimeSummary
import ru.stohastika.sklyanka.domain.reminder.ReminderOutage
import ru.stohastika.sklyanka.domain.reminder.ReminderReliability
import ru.stohastika.sklyanka.domain.reminder.ReminderWeekSummary
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.ui.component.TAG_BANNER
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.token.lightColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экран проверки напоминаний: финальный первый уровень (SKL-102, ОВ-126).
 *
 * Идёт на JVM под Robolectric: состав экрана и подписи устройства не требуют. Чего прогон
 * не доказывает — что «Исправить» открывает нужный системный экран и что отчёт уходит
 * по сети: это чужое и проверяется на устройстве.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: иначе нижние секции не собираются, и проверка видела бы
// только верх. Язык — часть условий: подписи со склонениями читаются по правилам русского
// (НФТ-44).
@Config(qualifiers = "ru-rRU-w390dp-h4000dp-mdpi")
class DiagnosticsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private val today: LocalDate = LocalDate.of(2026, 9, 25)

    private fun snapshot(
        reliable: Boolean = true,
        permissions: Map<ReminderPermission, PermissionState> = grantedAll(),
        week: ReminderWeekSummary = ReminderWeekSummary(fired = 14, missed = 2),
        onTime: ReminderOnTimeSummary = ReminderOnTimeSummary(fired = 16, onTime = 14, days = 7),
        nextAt: Instant? = null,
        lastShown: ReminderLogEntry? = null,
        lastMedicationName: String? = null,
        nextMedicationName: String? = null,
        outage: ReminderOutage? = null,
        deliveryCheck: DeliveryCheckState = DeliveryCheckState.None,
        deliveryCheckAcknowledged: Boolean = false,
    ) = ReminderDiagnostics(
        lastShown = lastShown,
        lastMedicationName = lastMedicationName,
        nextAt = nextAt,
        nextIntakeId = null,
        nextMedicationName = nextMedicationName,
        totalScheduled = 0,
        lastReschedule = null,
        permissions = permissions,
        reliability = ReminderReliability(
            notificationsEnabled = reliable,
            channelEnabled = reliable,
            exactAlarmsAllowed = reliable,
            fullScreenIntentAllowed = reliable,
        ),
        outage = outage,
        onTime = onTime,
        week = week,
        journal = emptyList(),
        alarmKind = AlarmKind.ALARM_CLOCK,
        measurementAvailable = false,
        deliveryCheck = deliveryCheck,
        deliveryCheckAcknowledged = deliveryCheckAcknowledged,
    )

    private fun show(
        snapshot: ReminderDiagnostics = snapshot(),
        fixFallback: ReminderPermission? = null,
        checkSheetOpen: Boolean = false,
        reportOpen: Boolean = false,
        reportComment: String = "",
        reportOutcome: DeveloperReportOutcome? = null,
        onRestartSetup: (() -> Unit)? = null,
        onFixPermission: (ReminderPermission) -> Unit = {},
        onCheckDelivery: () -> Unit = {},
        onDismissCheckSheet: () -> Unit = {},
        onStartDeliveryCheck: () -> Unit = {},
        onCancelDeliveryCheck: () -> Unit = {},
        onAcknowledgeDeliveryCheck: () -> Unit = {},
        onFixDeliveryCheck: () -> Unit = {},
        onReportOpen: () -> Unit = {},
        onReportCommentChange: (String) -> Unit = {},
        onReportSend: () -> Unit = {},
        onReportDismiss: () -> Unit = {},
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                SklyankaTheme {
                    DiagnosticsScreen(
                        state = DiagnosticsUiState(
                            snapshot = snapshot,
                            zone = zone,
                            today = today,
                            fixFallback = fixFallback,
                            checkSheetOpen = checkSheetOpen,
                            reportOpen = reportOpen,
                            reportComment = reportComment,
                            reportOutcome = reportOutcome,
                        ),
                        onRestartSetup = onRestartSetup,
                        onFixPermission = onFixPermission,
                        onCheckDelivery = onCheckDelivery,
                        onDismissCheckSheet = onDismissCheckSheet,
                        onStartDeliveryCheck = onStartDeliveryCheck,
                        onCancelDeliveryCheck = onCancelDeliveryCheck,
                        onAcknowledgeDeliveryCheck = onAcknowledgeDeliveryCheck,
                        onFixDeliveryCheck = onFixDeliveryCheck,
                        onReportOpen = onReportOpen,
                        onReportCommentChange = onReportCommentChange,
                        onReportSend = onReportSend,
                        onReportDismiss = onReportDismiss,
                        onBack = {},
                        toLocal = { instant: Instant -> instant.atZone(zone).toLocalDateTime() },
                    )
                }
            }
        }
    }

    // --- Порядок и вердикт (AC-1…AC-7) ---------------------------------------------------------

    @Test
    fun theSectionsKeepTheirOrderOnTheFirstLevel() {
        show(snapshot = snapshot(nextAt = Instant.parse("2026-09-26T05:00:00Z")))

        val verdict = compose.onNodeWithTag(TAG_VERDICT).fetchSemanticsNode().boundsInRoot.top
        val permissions = compose.onNodeWithTag(TAG_PERMISSIONS).fetchSemanticsNode().boundsInRoot.top
        val delivery = compose.onNodeWithTag(TAG_CHECK_BLOCK).fetchSemanticsNode().boundsInRoot.top
        val reminders = compose.onNodeWithTag(TAG_WEEK_SUMMARY).fetchSemanticsNode().boundsInRoot.top
        val footer = compose.onNodeWithTag(TAG_REPORT_FOOTER).fetchSemanticsNode().boundsInRoot.top

        assertTrue("verdict must be above permissions", verdict < permissions)
        assertTrue("permissions must be above delivery", permissions < delivery)
        assertTrue("delivery must be above reminders", delivery < reminders)
        assertTrue("reminders must be above the footer", reminders < footer)
    }

    @Test
    fun anOkVerdictIsAWordAndAnExplanation() {
        show(snapshot = snapshot(nextAt = Instant.parse("2026-09-26T05:00:00Z")))

        compose.onNodeWithTag(TAG_VERDICT).assertIsDisplayed()
        compose.onNodeWithText("Всё в порядке").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_verdict_ok_sub)).assertIsDisplayed()
        compose.onNodeWithTag(TAG_WARNING).assertDoesNotExist()
    }

    @Test
    fun aMissingReminderNamesTheNearestScheduledOne() {
        show(snapshot = snapshot(nextAt = null))

        compose.onNodeWithText(context.getString(R.string.diagnostics_verdict_ok_no_next)).assertIsDisplayed()
    }

    @Test
    fun aMissingReminderKeepsTheHealthyExplanationAndAddsWhatIsNotPlanned() {
        show(snapshot = snapshot(nextAt = null))

        // Макет состояния 8 и AC-28: «Напоминания приходят вовремя.» + «Новых пока не запланировано.».
        compose.onNodeWithText(context.getString(R.string.diagnostics_verdict_ok_sub)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_verdict_ok_no_next)).assertIsDisplayed()
    }

    @Test
    fun aDeniedDoNotDisturbAccessIsNamedAsAManualCheck() {
        show(
            snapshot = snapshot(
                permissions = grantedAll() + (ReminderPermission.DND_POLICY_ACCESS to PermissionState.DENIED),
            ),
        )

        compose.onNodeWithText(context.getString(R.string.diagnostics_state_unknown)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_state_denied)).assertDoesNotExist()
    }

    @Test
    fun aDisabledChannelIsNamedByTheVerdictBanner() {
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
        compose.onNodeWithTag(TAG_VERDICT).assertDoesNotExist()
    }

    @Test
    fun anOutageIsASeparateBannerThatSuppressesTheOkLine() {
        var fixed = 0
        show(
            snapshot = snapshot(outage = ReminderOutage(Instant.parse("2026-10-01T19:40:00Z"), missed = 3)),
            onRestartSetup = { fixed += 1 },
        )

        compose.onNodeWithTag(TAG_OUTAGE, useUnmergedTree = true).assertIsDisplayed()
        compose
            .onNodeWithText(
                context.getString(R.string.diagnostics_outage, "1 октября, 22:40"),
                useUnmergedTree = true,
            )
            .assertExists()
        compose.onNodeWithTag(TAG_VERDICT).assertDoesNotExist()

        compose.onNodeWithText(context.getString(R.string.diagnostics_fix), useUnmergedTree = true).performClick()
        assertEquals(1, fixed)
    }

    // --- Разрешения (AC-8…AC-14) ---------------------------------------------------------------

    @Test
    fun aMissingPermissionIsARowWithMarkerAndConsequence() {
        var fixed: ReminderPermission? = null
        show(
            snapshot = snapshot(
                permissions = grantedAll() + (ReminderPermission.EXACT_ALARMS to PermissionState.DENIED),
            ),
            onFixPermission = { fixed = it },
        )

        compose.onNodeWithTag(TAG_PERMISSION_ROW, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TAG_PERMISSION_MARKER, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals()
        compose.onNodeWithText(context.getString(R.string.diagnostics_state_denied)).assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.diagnostics_consequence_exact_alarms))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_permission_rest_granted)).assertExists()

        compose.onNodeWithTag(TAG_FIX, useUnmergedTree = true).performClick()
        assertEquals(ReminderPermission.EXACT_ALARMS, fixed)
    }

    @Test
    fun theRowsAreSplitIntoTwoGroupsByConsequence() {
        show(
            snapshot = snapshot(
                permissions = grantedAll() +
                    (ReminderPermission.EXACT_ALARMS to PermissionState.DENIED) +
                    (ReminderPermission.DND_POLICY_ACCESS to PermissionState.DENIED),
            ),
        )

        compose.onNodeWithText(context.getString(R.string.diagnostics_permission_group_fix_first)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_permission_group_can_defer)).assertIsDisplayed()
    }

    @Test
    fun thePermissionsCardSeparatesRowsAndTheFooterWithHairlines() {
        show(
            snapshot = snapshot(
                permissions = grantedAll() +
                    (ReminderPermission.NOTIFICATIONS to PermissionState.DENIED) +
                    (ReminderPermission.EXACT_ALARMS to PermissionState.DENIED),
            ),
            onRestartSetup = {},
        )

        // Две строки одной группы: линия между строками и линия над футером
        // (`.pitem + .pitem`, `.pfoot-action{border-top}`).
        compose.onAllNodesWithTag(TAG_PERMISSION_SEPARATOR, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun thePermissionRowsKeepTwelveDpOfAirAroundTheSeparator() {
        show(
            snapshot = snapshot(
                permissions = grantedAll() +
                    (ReminderPermission.NOTIFICATIONS to PermissionState.DENIED) +
                    (ReminderPermission.EXACT_ALARMS to PermissionState.DENIED),
            ),
        )

        // `.pitem{padding:12px 0}`: волосяная линия стоит между строками, а не прижимается
        // к тексту. Иначе строка читается как подчёркнутая, и карточка «слипается».
        val rows = compose.onAllNodesWithTag(TAG_PERMISSION_ROW, useUnmergedTree = true).fetchSemanticsNodes()
        val line = compose.onAllNodesWithTag(TAG_PERMISSION_SEPARATOR, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first()
            .boundsInRoot
        assertEquals(12f, line.top - rows[0].boundsInRoot.bottom, 1f)
        assertEquals(12f, rows[1].boundsInRoot.top - line.bottom, 1f)
    }

    @Test
    fun theAutostartRowIsNeutralAndSaysItIsCheckedByHand() {
        show(
            snapshot = snapshot(
                permissions = grantedAll() + (ReminderPermission.AUTOSTART to PermissionState.UNKNOWN),
            ),
        )

        compose.onNodeWithText(context.getString(R.string.diagnostics_permission_autostart)).assertExists()
        compose.onNodeWithText(context.getString(R.string.diagnostics_state_unknown)).assertExists()
        compose.onNodeWithText(context.getString(R.string.diagnostics_consequence_autostart)).assertExists()
    }

    @Test
    fun issuedPermissionsCollapseToASingleLine() {
        show()

        compose.onNodeWithTag(TAG_ALL_GRANTED).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_all_granted)).assertIsDisplayed()
        compose.onNodeWithTag(TAG_PERMISSION_ROW, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aFailedFixShowsTheWrittenInstructionsUnderTheRow() {
        show(
            snapshot = snapshot(
                permissions = grantedAll() + (ReminderPermission.AUTOSTART to PermissionState.UNKNOWN),
            ),
            fixFallback = ReminderPermission.AUTOSTART,
        )

        compose.onNodeWithTag(TAG_FIX_FALLBACK, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_fallback_app_startup)).assertExists()
    }

    @Test
    fun theRestartSetupRowReachesItsOwner() {
        var restarted = 0
        show(onRestartSetup = { restarted += 1 })

        compose.onNodeWithTag(TAG_RESTART_SETUP).performScrollTo().performClick()

        assertEquals(1, restarted)
    }

    // --- Проверка доставки (AC-15…AC-24) -------------------------------------------------------

    @Test
    fun theCheckBlockExplainsAndOffersTheCheck() {
        show()

        compose.onNodeWithTag(TAG_CHECK_BLOCK).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_CHECK_DELIVERY).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_delivery_explanation)).assertExists()
    }

    @Test
    fun aRunningCheckShowsTheInstructionAndTheQuietCancel() {
        show(snapshot = snapshot(deliveryCheck = DeliveryCheckState.Running(Instant.parse("2026-09-25T07:01:30Z"))))

        compose.onNodeWithTag(TAG_CHECK_RUNNING).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_CHECK_CANCEL).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_CHECK_DELIVERY).assertDoesNotExist()
    }

    @Test
    fun aSuccessfulCheckIsClosedWithGotItAndHasNoRepeat() {
        var acknowledged = 0
        show(
            snapshot = snapshot(deliveryCheck = finished(DeliveryCheckOutcome.ON_TIME)),
            onAcknowledgeDeliveryCheck = { acknowledged += 1 },
        )

        compose.onNodeWithText(context.getString(R.string.diagnostics_check_delivered)).assertExists()
        compose.onNodeWithTag(TAG_CHECK_RESULT_ACKNOWLEDGE).performScrollTo().performClick()
        assertEquals(1, acknowledged)
        // Повтора проверки нет: у исхода ровно одно действие, «Исправить» не показывается.
        compose.onNodeWithTag(TAG_CHECK_FIX).assertDoesNotExist()
    }

    @Test
    fun aLateCheckNamesTheMinutesAndOffersAFix() {
        var fixed = 0
        show(
            snapshot = snapshot(deliveryCheck = finished(DeliveryCheckOutcome.LATE, lateMinutes = 12)),
            onFixDeliveryCheck = { fixed += 1 },
        )

        compose
            .onNodeWithText(
                context.getString(
                    R.string.diagnostics_check_late,
                    context.resources.getQuantityString(R.plurals.diagnostics_late, 12, 12),
                ),
            )
            .assertExists()
        compose.onNodeWithTag(TAG_CHECK_FIX).performScrollTo().performClick()
        assertEquals(1, fixed)
    }

    @Test
    fun aFailedCheckIsNamedByTheSingleVerdictBanner() {
        show(snapshot = snapshot(deliveryCheck = finished(DeliveryCheckOutcome.NOT_DELIVERED)))

        compose.onNodeWithTag(TAG_WARNING, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_check_failed_verdict), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithTag(TAG_CHECK_FIX).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_VERDICT).assertDoesNotExist()
    }

    @Test
    fun anAcknowledgedFailureReturnsToTheButtonWithoutABanner() {
        show(
            snapshot = snapshot(
                deliveryCheck = finished(DeliveryCheckOutcome.NOT_DELIVERED),
                deliveryCheckAcknowledged = true,
            ),
        )

        compose.onNodeWithTag(TAG_CHECK_DELIVERY).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_WARNING).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.diagnostics_check_not_delivered)).assertDoesNotExist()
    }

    @Test
    fun aCheckThatNeverHappenedIsShownInTheBlockWithoutAVerdictBanner() {
        show(snapshot = snapshot(deliveryCheck = finished(DeliveryCheckOutcome.NOT_COMPLETED)))

        compose.onNodeWithText(context.getString(R.string.diagnostics_check_not_completed)).assertExists()
        compose.onNodeWithTag(TAG_CHECK_RESULT_ACKNOWLEDGE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_WARNING).assertDoesNotExist()
    }

    @Test
    fun theCheckSheetCarriesTheTitleInstructionAndBothActions() {
        var started = 0
        var dismissed = 0
        show(
            checkSheetOpen = true,
            onStartDeliveryCheck = { started += 1 },
            onDismissCheckSheet = { dismissed += 1 },
        )

        compose.onNodeWithTag(TAG_CHECK_SHEET).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_check_sheet_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.diagnostics_check_sheet_text)).assertExists()
        compose.onNodeWithTag(TAG_CHECK_START).performClick()
        compose.onNodeWithTag(TAG_CHECK_SHEET_CANCEL).performClick()
        assertEquals(1, started)
        assertEquals(1, dismissed)
    }

    // --- «Напоминания» (AC-25…AC-28) -----------------------------------------------------------

    @Test
    fun theWeekSummaryIsAWholePhrase() {
        show(snapshot = snapshot(week = ReminderWeekSummary(fired = 14, missed = 2)))

        compose.onNodeWithTag(TAG_WEEK_SUMMARY).assertIsDisplayed()
        compose.onNodeWithText("За 7 дней сработало 14 напоминаний, пропущено 2 приёма").assertIsDisplayed()
    }

    @Test
    fun theWeekSummaryNeverShowsZeroOfZero() {
        show(snapshot = snapshot(week = ReminderWeekSummary(fired = 0, missed = 0)))

        compose.onNodeWithText(context.getString(R.string.diagnostics_week_none)).assertIsDisplayed()
        compose.onNodeWithText("0 из 0", substring = true).assertDoesNotExist()
    }

    @Test
    fun theLastReminderIsNamedWithItsTimeAndAWord() {
        show(
            snapshot = snapshot(
                lastShown = shown(),
                lastMedicationName = "Магний B6",
            ),
        )

        compose.onNodeWithText("Магний B6 — 8:00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Показано в 8:00 · вовремя").assertIsDisplayed()
    }

    @Test
    fun theNextReminderIsNamedAsAPlan() {
        show(
            snapshot = snapshot(
                nextAt = Instant.parse("2026-09-26T05:00:00Z"),
                nextMedicationName = "Эутирокс",
            ),
        )

        compose.onNodeWithText("Эутирокс — завтра, 8:00 · план").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.diagnostics_next_alarm_indicator))
            .assertExists()
    }

    @Test
    fun noUpcomingReminderExplainsItself() {
        show(snapshot = snapshot(nextAt = null))

        compose.onNodeWithText(context.getString(R.string.diagnostics_next_none)).performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.diagnostics_next_none_explanation))
            .assertIsDisplayed()
    }

    // --- Отчёт разработчику (AC-29…AC-35) ------------------------------------------------------

    @Test
    fun theReportFooterIsVisibleAndOpensTheSheet() {
        var opened = 0
        show(onReportOpen = { opened += 1 })

        compose.onNodeWithTag(TAG_REPORT_FOOTER).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_report_open)).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun theReportFooterNoteFillsTheWidthAndCentersItsLines() {
        show()

        val note = context.getString(R.string.diagnostics_report_footer_note)
        compose.onNodeWithTag(TAG_REPORT_FOOTER).performScrollTo()
        val footerWidth = compose.onNodeWithTag(TAG_REPORT_FOOTER).fetchSemanticsNode().boundsInRoot.width
        val noteNode = compose.onNodeWithText(note, useUnmergedTree = true).fetchSemanticsNode()

        assertEquals("the note must fill the footer width", footerWidth, noteNode.boundsInRoot.width, 0.5f)
        assertEquals(
            "each wrapped line of the note must be centered",
            TextAlign.Center,
            firstTextLayout(noteNode).layoutInput.style.textAlign,
        )
    }

    @Test
    fun theSheetCarriesTheExplanationOptionalFieldAndSend() {
        show(reportOpen = true)

        compose.onNodeWithTag(TAG_REPORT_SHEET).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_report_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.diagnostics_report_explanation)).assertExists()
        compose
            .onNodeWithText(context.getString(R.string.diagnostics_report_comment_label).uppercase())
            .assertExists()
        compose.onNodeWithText(context.getString(R.string.diagnostics_report_comment_optional)).assertExists()
        compose.onNodeWithTag(TAG_REPORT_COMMENT).assertIsDisplayed()
        compose.onNodeWithTag(TAG_REPORT_SEND).assertIsDisplayed()
        // Объяснение — плашка шторки, а не баннер §4.6 (ОВ-142).
        compose.onNodeWithTag(TAG_BANNER, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun anEmptyCommentCanBeSent() {
        var sent = 0
        show(reportOpen = true, onReportSend = { sent += 1 })

        compose.onNodeWithTag(TAG_REPORT_SEND).performClick()

        assertEquals(1, sent)
    }

    @Test
    fun aSentReportShowsSuccess() {
        show(reportOpen = true, reportComment = "текст", reportOutcome = DeveloperReportOutcome.SENT)

        compose.onNodeWithTag(TAG_REPORT_SENT).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_report_sent_title)).assertIsDisplayed()
        compose.onNodeWithTag(TAG_REPORT_SEND).assertDoesNotExist()
    }

    /**
     * Выход из успеха — подтверждающее действие шапки, а не отмена: «Готово» набрано
     * `BodyDefaultStrong` 15/600 `Accent`, тогда как «Отмена» — `BodyDefault` 15/400
     * `TextMuted` (§4.9, ОВ-146). Иначе серое «Готово» читается как отмена и иерархия
     * действий теряется (дефект визревью 04.10.2026).
     */
    @Test
    fun theDoneExitOfWorkCarriesTheConfirmingRoleOfTheHeader() {
        show(reportOpen = true, reportOutcome = DeveloperReportOutcome.SENT)

        val style = textStyleOf(UiR.string.action_done)
        assertEquals(SklyankaType.BodyDefaultStrong.fontWeight, style.fontWeight)
        assertEquals(SklyankaType.BodyDefaultStrong.fontSize.value, style.fontSize.value, 0.01f)
        assertEquals(lightColors().accent, style.color)
    }

    /** «Отмена» при вводе и ошибке остаётся приглушённой: 15/400 `TextMuted` (§4.9, ОВ-146). */
    @Test
    fun theCancelExitOfWorkKeepsTheMutedRoleOfTheHeader() {
        show(reportOpen = true)

        val style = textStyleOf(UiR.string.action_cancel)
        assertEquals(SklyankaType.BodyDefault.fontWeight, style.fontWeight)
        assertEquals(SklyankaType.BodyDefault.fontSize.value, style.fontSize.value, 0.01f)
        assertEquals(lightColors().textMuted, style.color)
    }

    @Test
    fun aFailedReportOffersRetryAndKeepsTheText() {
        var sent = 0
        show(
            reportOpen = true,
            reportComment = "введённый текст",
            reportOutcome = DeveloperReportOutcome.FAILED,
            onReportSend = { sent += 1 },
        )

        compose.onNodeWithTag(TAG_REPORT_FAILED).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.diagnostics_report_failed)).assertExists()
        compose.onNodeWithTag(TAG_REPORT_RETRY).performClick()
        assertEquals(1, sent)
        compose.onNodeWithText("введённый текст").assertExists()
    }

    @Test
    fun theSheetDismissActionReachesItsOwner() {
        var dismissed = 0
        show(reportOpen = true, onReportDismiss = { dismissed += 1 })

        compose.onNodeWithTag(TAG_REPORT_DISMISS).performClick()

        assertEquals(1, dismissed)
    }

    // --- Крупный шрифт (AC-42, НФТ-31) ---------------------------------------------------------

    @Test
    fun atOneHundredThirtyPercentTheFixActionStaysOnOneLine() {
        show(
            snapshot = snapshot(
                permissions = grantedAll() + (ReminderPermission.EXACT_ALARMS to PermissionState.DENIED),
            ),
            fontScale = 1.3f,
        )

        assertEquals("«Исправить» must not break by letters at 130%", 1, lineCountOfTag(TAG_FIX))
    }

    @Test
    fun atTwoHundredPercentTheFixActionStaysOnOneLine() {
        show(
            snapshot = snapshot(
                permissions = grantedAll() + (ReminderPermission.EXACT_ALARMS to PermissionState.DENIED),
            ),
            fontScale = 2f,
        )

        assertEquals("«Исправить» must not break by letters at 200%", 1, lineCountOfTag(TAG_FIX))
    }

    @Test
    fun atTwoHundredPercentTheSheetTitleStaysOnOneLine() {
        show(reportOpen = true, fontScale = 2f)

        assertEquals(
            "«Отчёт разработчику» must not break by letters at 200%",
            1,
            lineCountOfText(context.getString(R.string.diagnostics_report_title)),
        )
    }

    /**
     * При крупном шрифте шапка шторки двухстрочная: действие остаётся в своей строке,
     * название уходит под него. «Готово» — по-прежнему одно достижимое действие с тегом,
     * без невидимого дубля, который перехватывал бы нажатие (§6.3, AC-42).
     */
    @Test
    fun atTwoHundredPercentTheSuccessExitStaysTheOnlyAction() {
        show(reportOpen = true, reportOutcome = DeveloperReportOutcome.SENT, fontScale = 2f)

        compose.onAllNodesWithTag(TAG_REPORT_DISMISS).assertCountEquals(1)
        compose.onAllNodesWithText(context.getString(UiR.string.action_done)).assertCountEquals(1)
    }

    private fun textStyleOf(text: Int): TextStyle = firstTextLayout(
        compose.onNodeWithText(context.getString(text)).fetchSemanticsNode(),
    ).layoutInput.style

    private fun lineCountOfTag(tag: String): Int = firstTextLayout(
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode(),
    ).lineCount

    private fun lineCountOfText(text: String): Int = firstTextLayout(
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode(),
    ).lineCount

    private fun firstTextLayout(node: SemanticsNode): TextLayoutResult {
        val found = mutableListOf<TextLayoutResult>()
        fun walk(current: SemanticsNode) {
            val results = mutableListOf<TextLayoutResult>()
            current.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            found += results
            current.children.forEach(::walk)
        }
        walk(node)
        return requireNotNull(found.firstOrNull()) { "no text layout under the node" }
    }

    private fun finished(outcome: DeliveryCheckOutcome, lateMinutes: Long? = null) = DeliveryCheckState.Finished(
        outcome = outcome,
        dueAt = Instant.parse("2026-09-25T07:01:30Z"),
        resolvedAt = Instant.parse("2026-09-25T07:02:00Z"),
        lateMinutes = lateMinutes,
    )

    private fun shown() = ReminderLogEntry(
        occurredAt = Instant.parse("2026-09-25T05:00:37Z"),
        event = ReminderEvent.SHOWN,
        intakeId = "intake-1",
        scheduledAt = Instant.parse("2026-09-25T05:00:00Z"),
        firedAt = Instant.parse("2026-09-25T05:00:37Z"),
        deviationSeconds = 37,
        delivered = true,
    )

    private companion object {
        fun grantedAll(): Map<ReminderPermission, PermissionState> =
            ReminderPermission.entries.associateWith { PermissionState.GRANTED }
    }
}
