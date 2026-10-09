package ru.stohastika.sklyanka.acceptance.diagnostics

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.ReminderPermissionGroups
import ru.stohastika.sklyanka.domain.reminder.ReminderWarning
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.TAG_ALL_GRANTED
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_BLOCK
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_CANCEL
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_DELIVERY
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_FIX
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_NOTICE
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_RESULT
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_RESULT_ACKNOWLEDGE
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_RUNNING
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_SHEET
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_SHEET_CANCEL
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_START
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_FIX
import ru.stohastika.sklyanka.feature.reminder.TAG_JOURNAL_EXPORT
import ru.stohastika.sklyanka.feature.reminder.TAG_LAST_SHORT
import ru.stohastika.sklyanka.feature.reminder.TAG_NEXT_SHORT
import ru.stohastika.sklyanka.feature.reminder.TAG_OUTAGE
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSIONS
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSION_CONSEQUENCE
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSION_GROUP
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSION_MARKER
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSION_REST_GRANTED
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSION_ROW
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSION_STATE
import ru.stohastika.sklyanka.feature.reminder.TAG_RESTART_SETUP
import ru.stohastika.sklyanka.feature.reminder.TAG_VERDICT
import ru.stohastika.sklyanka.feature.reminder.TAG_WARNING
import ru.stohastika.sklyanka.feature.reminder.TAG_WEEK_SUMMARY
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

/**
 * Экранные шаги приёмки «Проверки напоминаний» (SKL-102, группы `@screen`).
 *
 * Шаги живут в `feature-reminder`: говорят об экране, собранном настоящим
 * `DiagnosticsScreen` на мире сценария ([DiagnosticsStand]). Тексты читаются из ресурсов —
 * дословно те же, что видит человек; литеры сценария — лишь ожидание.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class Sk102DiagnosticsSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Снимок вердикта и разрешений до запуска проверки: «такие же, какими были» (AC-16). */
    private var permissionsBeforeCheck: Map<ReminderPermission, PermissionState>? = null
    private var reliabilityBeforeCheck: Any? = null

    // --- Предыстория: исправность --------------------------------------------------------------

    @Допустим("^все разрешения выданы, перерыва нет, последняя проверка доставки не провалена$")
    fun everythingIsHealthy() {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        world.outageSince = null
    }

    @Допустим("^следующие приёмы запланированы$")
    fun upcomingIntakesArePlanned() {
        planNextReminder(medication = "Магний B6", date = "25 сентября 2026", time = "20:00")
    }

    @Допустим("^уведомления отключены, остальные разрешения выданы$")
    fun notificationsAreOff() {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        world.system.deny(ReminderPermission.NOTIFICATIONS)
    }

    @Допустим("^последняя проверка доставки не прошла$")
    fun theLastCheckFailed() {
        startCheck()
        expireCheck()
        DiagnosticsStand.refresh(world)
    }

    @Допустим("^разрешение «(.+)» не в порядке$")
    fun oneReliabilityPermissionIsBroken(label: String) {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        world.system.deny(permissionOfReason(label))
    }

    @Допустим("^часть разрешений выдана, а часть требует действия$")
    fun somePermissionsRequireAction() {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        world.system.deny(ReminderPermission.EXACT_ALARMS, ReminderPermission.FULL_SCREEN_INTENT)
    }

    @Допустим("^не выданы точные будильники и нет доступа к политике «Не беспокоить»$")
    fun exactAlarmsAndDndAreDenied() {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        world.system.deny(ReminderPermission.EXACT_ALARMS, ReminderPermission.DND_POLICY_ACCESS)
    }

    @Допустим("^строка разрешения «(.+)» в состоянии «(.+)»$")
    fun aPermissionRowIsInState(label: String, state: String) {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        val permission = permissionOfLabel(label)
        world.system.set(permission, stateOf(state))
    }

    @Допустим("^в карточке несколько строк, требующих действия$")
    fun severalRowsRequireAction() {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        world.system.deny(ReminderPermission.EXACT_ALARMS, ReminderPermission.DND_POLICY_ACCESS)
    }

    @Допустим("^не выданы уведомления и точные будильники$")
    fun notificationsAndExactAlarmsAreDenied() {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        world.system.deny(ReminderPermission.NOTIFICATIONS, ReminderPermission.EXACT_ALARMS)
    }

    @Допустим("^не выдано одно разрешение$")
    fun exactlyOnePermissionIsDenied() {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        world.system.deny(ReminderPermission.EXACT_ALARMS)
    }

    @Допустим("^несколько разрешений требуют действия$")
    fun severalPermissionsRequireAction() {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        world.system.deny(ReminderPermission.EXACT_ALARMS, ReminderPermission.FULL_SCREEN_INTENT)
    }

    // --- Предыстория: проверка доставки --------------------------------------------------------

    @Допустим("^проверка доставки запущена$")
    fun theCheckIsRunning() {
        startCheck()
        saveBeforeCheck()
    }

    @Допустим("^проверка доставки идёт$")
    fun theCheckIsUnderway() {
        startCheck()
        saveBeforeCheck()
    }

    @Допустим("^приложение было закрыто в момент срабатывания, тестовое напоминание дошло вовремя$")
    fun theAppWasClosedAndTheCheckArrivedOnTime() {
        startCheck()
        fireCheck(plusSeconds = 0)
        DiagnosticsStand.refresh(world)
    }

    @Допустим("^результат «Дошло вовремя» показан на экране$")
    fun theOnTimeResultIsOnScreen() {
        startCheck()
        fireCheck(plusSeconds = 0)
        DiagnosticsStand.refresh(world)
    }

    @Допустим("^тестовое напоминание дошло с опозданием на (\\d+) минут$")
    fun theCheckArrivedLate(minutes: Int) {
        startCheck()
        fireCheck(plusSeconds = minutes.toLong() * SECONDS_IN_MINUTE)
        DiagnosticsStand.refresh(world)
    }

    @Допустим("^тестовое напоминание не пришло$")
    fun theCheckNeverArrived() {
        startCheck()
        expireCheck()
        DiagnosticsStand.refresh(world)
    }

    @Допустим("^приложение было открыто в момент срабатывания$")
    fun theAppWasOpenWhenTheCheckFired() {
        startCheck()
        world.reminders.appForeground = true
        fireCheck(plusSeconds = 0)
        DiagnosticsStand.refresh(world)
    }

    @Допустим("^проверка доставки завершилась неудачей$")
    fun theCheckEndedInFailure() {
        startCheck()
        expireCheck()
        DiagnosticsStand.refresh(world)
    }

    @Допустим("^показан любой исход проверки доставки$")
    fun anyCheckOutcomeIsShown() {
        startCheck()
        fireCheck(plusSeconds = 0)
        DiagnosticsStand.refresh(world)
    }

    @Допустим("^тестовое напоминание сработало$")
    fun theTestReminderFired() {
        startCheck()
        fireCheck(plusSeconds = 0)
    }

    // --- Предыстория: сводка, последнее и следующее --------------------------------------------

    @Допустим("^за 7 дней сработало (\\d+) напоминаний и пропущено (\\d+) приёмов$")
    fun theWeekSummary(fired: Int, missed: Int) {
        val now = world.clock.now()
        repeat(fired) { index ->
            record(
                ReminderLogEntry(
                    occurredAt = now.minusSeconds((index + 1).toLong()),
                    event = ReminderEvent.SHOWN,
                    intakeId = "intake-week-$index",
                    scheduledAt = now.minusSeconds((index + 1).toLong()),
                    firedAt = now.minusSeconds((index + 1).toLong()),
                    deviationSeconds = 0,
                    channel = ReminderChannel.REGULAR,
                    delivered = true,
                ),
            )
        }
        repeat(missed) { index ->
            record(
                ReminderLogEntry(
                    occurredAt = now.minusSeconds((index + 1).toLong()),
                    event = ReminderEvent.MISSED_SWEEP,
                    intakeId = "intake-missed-$index",
                ),
            )
        }
    }

    @Допустим("^последнее напоминание о «(.+)» было запланировано на (.+?), (\\d{2}:\\d{2}) и показано вовремя$")
    fun theLastReminderWasShownOnTime(name: String, date: String, time: String) {
        val medicationId = medication(name, DAILY_TIME)
        val planned = world.clock.momentOf(world.localMoment(date, time))
        record(
            ReminderLogEntry(
                occurredAt = planned,
                event = ReminderEvent.SHOWN,
                intakeId = LAST_INTAKE,
                medicationId = medicationId,
                scheduledAt = planned,
                firedAt = planned,
                deviationSeconds = 0,
                channel = ReminderChannel.REGULAR,
                delivered = true,
            ),
        )
    }

    @Допустим("^последнее напоминание о «(.+)» показано с опозданием на (\\d+) минут$")
    fun theLastReminderWasShownLate(name: String, minutes: Int) {
        val medicationId = medication(name, DAILY_TIME)
        val planned = world.clock.momentOf(world.localMoment("25 сентября 2026", DAILY_TIME))
        val lateSeconds = minutes.toLong() * SECONDS_IN_MINUTE
        record(
            ReminderLogEntry(
                occurredAt = planned.plusSeconds(lateSeconds),
                event = ReminderEvent.SHOWN,
                intakeId = LAST_INTAKE,
                medicationId = medicationId,
                scheduledAt = planned,
                firedAt = planned.plusSeconds(lateSeconds),
                deviationSeconds = lateSeconds,
                channel = ReminderChannel.REGULAR,
                delivered = true,
            ),
        )
    }

    @Допустим("^следующее напоминание — о «(.+)» (.+?), (\\d{2}:\\d{2})$")
    fun theNextReminderIsFor(name: String, date: String, time: String) {
        planNextReminder(name, date, time)
    }

    @Допустим("^активных лекарств с расписанием нет$")
    fun thereAreNoActiveMedications() = Unit

    // --- Действия -------------------------------------------------------------------------------

    @Когда("^человек нажимает «Пройти настройку заново» под карточкой «Разрешения»$")
    fun thePersonRestartsSetup() {
        DiagnosticsStand.actRaw(world) { onNodeWithTag(TAG_RESTART_SETUP).performScrollTo().performClick() }
    }

    @Тогда("^открывается «Настроить надёжность»$")
    fun theSetupScreenOpens() {
        assertTrue(DiagnosticsStand.restartSetupRequested, "«Пройти настройку заново» must lead to the setup")
    }

    @Когда("^человек выдаёт точные будильники в системных настройках и возвращается$")
    fun thePersonGrantsExactAlarmsAndReturns() {
        world.system.grant(ReminderPermission.EXACT_ALARMS)
        DiagnosticsStand.refresh(world)
    }

    @Когда("^человек выдаёт его в системных настройках и возвращается$")
    fun thePersonGrantsTheOnlyOneAndReturns() {
        world.system.grant(ReminderPermission.EXACT_ALARMS)
        DiagnosticsStand.refresh(world)
    }

    @Когда("^человек смотрит на «Проверку доставки»$")
    fun thePersonLooksAtTheDeliveryCheck() {
        assertTrue(hasTag(TAG_CHECK_BLOCK), "the delivery check block must be on the screen")
    }

    @Когда("^человек нажимает «Отменить проверку»$")
    fun thePersonCancelsTheCheck() {
        DiagnosticsStand.actRaw(world) { onNodeWithTag(TAG_CHECK_CANCEL).performScrollTo().performClick() }
    }

    @Когда("^человек нажимает «Понятно»$")
    fun thePersonAcknowledges() {
        DiagnosticsStand.actRaw(world) {
            onNodeWithTag(TAG_CHECK_RESULT_ACKNOWLEDGE).performScrollTo().performClick()
        }
        DiagnosticsStand.refresh(world)
    }

    @Когда("^человек нажимает «Назад»$")
    fun thePersonPressesBack() {
        // Возврат — шеврон в цели 44 dp; слова «Назад» на экране нет, и цель ищется
        // по доступному имени `cd_back`, а не по тексту (12-design §7).
        val back = context.getString(ru.stohastika.sklyanka.ui.R.string.cd_back)
        DiagnosticsStand.actRaw(world) { onNodeWithContentDescription(back).performClick() }
    }

    @Когда("^человек ушёл из-за «Исправить» в системные настройки$")
    fun thePersonLeavesThroughFix() {
        DiagnosticsStand.actRaw(world) { onNodeWithTag(TAG_FIX).performScrollTo().performClick() }
    }

    @Когда("^он возвращается на «Проверку напоминаний»$")
    fun heReturnsToTheCheck() {
        DiagnosticsStand.refresh(world)
    }

    @Когда("^человек уходит с экрана$")
    fun thePersonLeavesTheScreen() = Unit

    @Когда("^человек смотрит вниз экрана$")
    fun thePersonLooksAtTheBottom() {
        assertTrue(hasTag(TAG_JOURNAL_EXPORT), "the journal export row must be at the bottom")
    }

    @Когда("^человек смотрит на карточку проверки$")
    fun thePersonLooksAtTheCheckCard() {
        DiagnosticsStand.showDeliveryCheckCard(world, lastDeliveryCheckMoment()) {
            onNodeWithTag(ru.stohastika.sklyanka.feature.reminder.TAG_DELIVERY_CHECK).assertExists()
        }
    }

    @Когда("^человек целится в любой интерактивный элемент$")
    fun thePersonAimsAtAnyInteractiveElement() = Unit

    // --- Проверки: вердикт ---------------------------------------------------------------------

    @Тогда("^сверху вниз идут вердикт, «Разрешения», «Проверка доставки», «Напоминания»$")
    fun theSectionsAreInOrder() {
        val verdictTop = topOfFirst(TAG_VERDICT, TAG_WARNING)
        val permissionsTop = topOfFirst(TAG_PERMISSIONS)
        val checkTop = topOfFirst(TAG_CHECK_BLOCK)
        val remindersTop = topOfFirst(TAG_WEEK_SUMMARY)
        assertTrue(verdictTop < permissionsTop, "the verdict must be above the permissions")
        assertTrue(permissionsTop < checkTop, "the permissions must be above the check")
        assertTrue(checkTop < remindersTop, "the check must be above the reminders")
    }

    @Тогда("^внизу экрана стоит «Выгрузить журнал»$")
    fun theFooterIsAtTheBottom() {
        val footerTop = topOfFirst(TAG_JOURNAL_EXPORT)
        val remindersTop = topOfFirst(TAG_WEEK_SUMMARY)
        assertTrue(footerTop > remindersTop, "the journal export row must be below the reminders")
        assertTrue(hasText(string(R.string.diagnostics_journal_export)), "the footer must name the action")
    }

    @Тогда("^первым элементом идёт строка «Всё в порядке»$")
    fun theFirstElementIsAllGood() {
        assertTrue(hasTag(TAG_VERDICT), "the «Всё в порядке» line must be shown")
        val verdictTop = topOfFirst(TAG_VERDICT)
        val permissionsTop = topOfFirst(TAG_PERMISSIONS)
        assertTrue(verdictTop < permissionsTop, "the verdict must be the first block")
    }

    @Тогда("^под ней сказано «(.+)»$")
    fun underItSays(text: String) {
        assertTrue(hasText(text), "the screen must say «$text»")
    }

    @Тогда("^баннера над строкой нет$")
    fun thereIsNoBannerAbove() {
        assertFalse(hasTag(TAG_WARNING), "there must be no banner above the healthy line")
    }

    @Тогда("^сверху стоит баннер-предупреждение$")
    fun aWarningBannerIsOnTop() {
        assertTrue(hasTag(TAG_WARNING), "the warning banner must be shown")
        val warningTop = topOfFirst(TAG_WARNING)
        val permissionsTop = topOfFirst(TAG_PERMISSIONS)
        assertTrue(warningTop < permissionsTop, "the banner must be above the permissions")
    }

    @Тогда("^он называет одну старшую причину словами: «(.+)»$")
    fun theBannerNamesOneReason(text: String) {
        assertTrue(hasText(text), "the banner must name «$text»")
    }

    @Тогда("^строки «Всё в порядке» нет$")
    fun thereIsNoAllGoodLine() {
        assertFalse(hasTag(TAG_VERDICT), "the healthy line must not be shown")
    }

    @Тогда("^у баннера действия нет$")
    fun theBannerHasNoAction() {
        assertFalse(
            tagHasAction(TAG_WARNING),
            "the warning banner must carry no action (the action lives in the permissions row)",
        )
    }

    @Тогда("^в строке «Разрешения» у причины есть «Исправить»$")
    fun thePermissionsRowOffersFix() {
        assertTrue(hasTag(TAG_FIX), "the permissions row must offer «Исправить»")
    }

    @Тогда("^отдельным баннером сказано «(.+)»$")
    fun aSeparateBannerSays(text: String) {
        assertTrue(hasTag(TAG_OUTAGE), "the outage must be a separate banner")
        assertTrue(hasText(text), "the outage banner must say «$text»")
    }

    @Тогда("^у баннера есть действие «Исправить»$")
    fun theBannerCarriesFix() {
        assertTrue(hasTag(TAG_OUTAGE), "the outage banner must be present")
        assertTrue(hasText(string(R.string.diagnostics_fix)), "the outage banner must offer «Исправить»")
    }

    @Тогда("^постоянной строки «был перерыв» в других состояниях нет$")
    fun thereIsNoPermanentOutageLine() {
        // Перерыв — баннер, а не постоянная секция: он идёт отдельным элементом,
        // а не строкой списка, и заводится только самим состоянием.
        assertTrue(hasTag(TAG_OUTAGE), "the outage must be a separate banner")
        assertFalse(
            visibleTexts().any { it.contains("был перерыв", ignoreCase = true) },
            "there must be no permanent outage line",
        )
    }

    @Тогда("^результат проверки назван в блоке «Проверка доставки» один раз$")
    fun theResultIsNamedOnceInTheBlock() {
        assertEquals(1, countTag(TAG_CHECK_RESULT), "the check result must be named exactly once")
    }

    @Тогда("^вердикт не повторяет текст блока$")
    fun theVerdictDoesNotRepeatTheBlock() {
        val block = textOfTag(TAG_CHECK_RESULT)
        val verdict = textOfTag(TAG_WARNING)
        assertTrue(verdict.isNotEmpty(), "the verdict banner must be shown")
        assertFalse(verdict == block, "the verdict must not repeat the block text")
    }

    @Тогда("^заголовок секции не повторяется заголовком карточки внутри неё$")
    fun theSectionTitleIsNotRepeated() {
        // Надстрочник секции — единственное вхождение: внутри карточки блока своего
        // заголовка больше нет (ОВ-126).
        assertEquals(
            1,
            countTextExact(string(R.string.diagnostics_section_delivery).uppercase()),
            "the delivery section title must appear once",
        )
    }

    // --- Проверки: разрешения ------------------------------------------------------------------

    @Тогда("^баннер называет именно эту причину$")
    fun theBannerNamesThatReason() {
        val warning = requireNotNull(DiagnosticsStand.state(world).snapshot?.reliability?.warning) {
            "the premise must leave one senior reliability cause"
        }
        val expected = when (warning) {
            ReminderWarning.NOTIFICATIONS_OFF -> R.string.diagnostics_warning_notifications
            ReminderWarning.NOTIFICATION_CHANNEL_OFF -> R.string.diagnostics_warning_channel
            ReminderWarning.EXACT_ALARMS_OFF -> R.string.diagnostics_warning_exact_alarms
            ReminderWarning.FULL_SCREEN_INTENT_OFF -> R.string.diagnostics_warning_full_screen
            ReminderWarning.BOTH_OFF -> R.string.diagnostics_warning_both
        }
        assertTrue(hasTag(TAG_WARNING), "the warning banner must be shown")
        assertTrue(hasText(string(expected)), "the banner must name the senior cause")
    }

    @Тогда("^в карточке «Разрешения» стоят только требующие действия$")
    fun onlyActionableRowsAreShown() {
        val state = DiagnosticsStand.state(world)
        val expected = ReminderPermission.entries.count {
            ReminderPermissionGroups.requiresAction(it, state.snapshot?.permissions?.get(it) ?: PermissionState.UNKNOWN)
        }
        assertEquals(expected, countTag(TAG_PERMISSION_ROW), "only rows requiring action must be shown")
        assertTrue(hasTag(TAG_PERMISSION_REST_GRANTED), "the rest-granted footer must be shown")
    }

    @Тогда("^ниже сказано «Остальные разрешения выданы»$")
    fun belowSaysTheRestGranted() {
        assertTrue(hasText(string(R.string.diagnostics_permission_rest_granted)), "the footer must be shown")
    }

    @Тогда("^выданные разрешения отдельными строками не раскрываются$")
    fun grantedPermissionsAreNotExpanded() {
        val state = DiagnosticsStand.state(world)
        val granted = ReminderPermission.entries.filter {
            state.snapshot?.permissions?.get(it) == PermissionState.GRANTED
        }
        granted.forEach { permission ->
            assertFalse(
                hasText(string(permissionLabel(permission))),
                "granted permission $permission must not have its own row",
            )
        }
    }

    @Тогда("^в секции «Разрешения» стоит одна строка «Разрешения выданы»$")
    fun thePermissionsCollapseToOneLine() {
        assertTrue(hasTag(TAG_ALL_GRANTED), "the permissions must collapse to a single line")
        assertTrue(hasText(string(R.string.diagnostics_all_granted)), "the line must say «Разрешения выданы»")
    }

    @Тогда("^строки разделены на две группы$")
    fun theRowsAreSplitIntoTwoGroups() {
        assertEquals(2, countTag(TAG_PERMISSION_GROUP), "the rows must form two groups")
    }

    @Тогда("^первая группа озаглавлена «(.+)»$")
    fun theFirstGroupIsTitled(title: String) {
        assertEquals(title, groupTitles().firstOrNull(), "the first group header")
    }

    @Тогда("^вторая группа озаглавлена «(.+)»$")
    fun theSecondGroupIsTitled(title: String) {
        assertEquals(title, groupTitles().getOrNull(1), "the second group header")
    }

    @Тогда("^у строки есть маркер и под ним сказано «(.+)»$")
    fun theRowHasAMarkerAndSays(state: String) {
        assertTrue(hasTag(TAG_PERMISSION_MARKER), "the row must carry a marker")
        assertTrue(
            permissionStateWords().contains(state),
            "the row must say «$state» in visible words under the name",
        )
    }

    @Тогда("^последствие названо словами: «(.+)»$")
    fun theConsequenceIsInWords(consequence: String) {
        assertTrue(hasText(consequence), "the consequence must be said in words: «$consequence»")
        assertTrue(hasTag(TAG_PERMISSION_CONSEQUENCE), "the consequence line must be on the screen")
    }

    @Тогда("^у каждой строки одно тихое действие «Исправить»$")
    fun everyRowHasOneFix() {
        assertEquals(countTag(TAG_PERMISSION_ROW), countTag(TAG_FIX), "each row must have exactly one fix")
        assertTrue(hasText(string(R.string.diagnostics_fix)), "the action must be named «Исправить»")
    }

    @Тогда("^подписи и шевроны действий стоят на одной вертикали$")
    fun theActionsAreAligned() {
        val lefts = leftsOf(TAG_FIX)
        assertTrue(lefts.size >= 2, "the premise must leave at least two actions")
        assertTrue(lefts.all { abs(it - lefts.first()) < ALIGNMENT_TOLERANCE_PX }, "the action labels must align")
    }

    @Тогда("^у действия есть запас нажатия не меньше 44 dp$")
    fun theActionHasATouchTarget() {
        val minHeight = minHeightOf(TAG_FIX)
        assertTrue(
            minHeight >= dp(SklyankaSizes.MinTouchTarget),
            "the action touch target must be at least 44 dp, was $minHeight px",
        )
    }

    @Тогда("^исправленная строка исчезла, оставшиеся строки и футер на месте$")
    fun theFixedRowIsGone() {
        assertFalse(
            hasText(string(permissionLabel(ReminderPermission.EXACT_ALARMS))),
            "the fixed exact-alarms row must be gone",
        )
        assertTrue(hasText(string(permissionLabel(ReminderPermission.NOTIFICATIONS))), "the remaining row stays")
        assertTrue(hasTag(TAG_PERMISSION_REST_GRANTED), "the footer stays")
    }

    @Тогда("^вердикт пересчитан по старшей оставшейся причине$")
    fun theVerdictIsRecomputed() {
        val warning = requireNotNull(DiagnosticsStand.state(world).snapshot?.reliability?.warning)
        assertEquals(ReminderWarning.NOTIFICATIONS_OFF, warning, "notifications are the senior remaining cause")
        assertTrue(hasText(string(R.string.diagnostics_warning_notifications)), "the banner must name it")
    }

    @Тогда("^вердикт — «Всё в порядке», если ничего другого не требует действия$")
    fun theVerdictIsAllGood() {
        assertTrue(hasTag(TAG_VERDICT), "the healthy line must be shown")
        assertTrue(hasText(string(R.string.diagnostics_reliable)), "the verdict must be «Всё в порядке»")
    }

    @Тогда("^на экране нет «требуют внимания: N»$")
    fun thereIsNoAttentionCounter() {
        assertFalse(visibleTexts().any { it.contains("требуют внимания") }, "no attention counter on the screen")
    }

    @Тогда("^полосы прогресса починки на экране нет$")
    fun thereIsNoProgressBar() {
        // Полосы прогресса в системе нет как компонента; проверяем отсутствие самого слова-обещания.
        assertFalse(visibleTexts().any { it.contains("прогресс", ignoreCase = true) }, "no repair progress bar")
    }

    // --- Проверки: проверка доставки -----------------------------------------------------------

    @Тогда("^открывается шторка «Проверка доставки»$")
    fun theCheckSheetOpens() {
        assertTrue(hasTag(TAG_CHECK_SHEET), "the check sheet must open")
        assertTrue(hasText(string(R.string.diagnostics_check_sheet_title)), "the sheet must be titled")
    }

    @И("^в ней есть инструкция «(.+)»$")
    fun theSheetCarriesInstruction(text: String) {
        assertTrue(hasText(text), "the sheet must carry the instruction word for word")
    }

    @И("^есть действия «(.+)» и «(.+)»$")
    fun theSheetCarriesActions(start: String, cancel: String) {
        assertTrue(hasTag(TAG_CHECK_START), "the primary action must be there")
        assertTrue(hasTag(TAG_CHECK_SHEET_CANCEL), "the cancel must be there")
        assertTrue(hasText(start), "the primary action must say «$start»")
        assertTrue(hasText(cancel), "the cancel must say «$cancel»")
    }

    @И("^проверка ещё не запущена$")
    fun theCheckIsNotStartedYet() {
        assertNull(world.reminders.deliveryCheckAlarm(), "no test firing may be armed")
    }

    @Тогда("^в блоке стоит плашка «(.+)»$")
    fun theBlockShowsRunning(text: String) {
        assertTrue(hasTag(TAG_CHECK_RUNNING), "the running plaque must be shown")
        assertTrue(hasText(text), "the plaque must say «$text»")
    }

    @И("^есть тихая «Отменить проверку»$")
    fun theQuietCancelIsThere() {
        assertTrue(hasTag(TAG_CHECK_CANCEL), "the quiet cancel must be there")
        assertTrue(hasText(string(R.string.diagnostics_check_cancel)), "the cancel must be named")
    }

    @Но("^вердикт и «Разрешения» такие же, какими были при запуске$")
    fun theVerdictAndPermissionsAreUnchanged() {
        val state = DiagnosticsStand.state(world)
        assertEquals(permissionsBeforeCheck, state.snapshot?.permissions, "permissions must not change while running")
        assertEquals(reliabilityBeforeCheck, state.snapshot?.reliability, "the verdict must not change while running")
    }

    @Тогда("^тестовое срабатывание снято$")
    fun theTestFiringIsCancelled() {
        assertNull(world.reminders.deliveryCheckAlarm(), "the test firing must be cancelled")
    }

    @И("^блок вернулся к кнопке «Проверить доставку»$")
    fun theBlockReturnedToTheButton() {
        assertTrue(hasTag(TAG_CHECK_DELIVERY), "the launch button must be back")
        assertFalse(hasTag(TAG_CHECK_RESULT), "no result may remain")
    }

    @Но("^результата проверки не сохранилось$")
    fun noResultWasKept() {
        assertTrue(
            DiagnosticsStand.state(world).snapshot?.deliveryCheck == DeliveryCheckState.None,
            "«Отмена» must leave no result",
        )
    }

    @Тогда("^в блоке сказано «(.+)»$")
    fun theBlockPlainlySays(text: String) {
        assertTrue(hasTag(TAG_CHECK_RESULT), "the block must show the result")
        assertTrue(hasText(text), "the block must say «$text», was: ${textOfTag(TAG_CHECK_RESULT)}")
    }

    @И("^есть тихое «Понятно»$")
    fun theQuietAcknowledgeIsThere() {
        assertTrue(hasTag(TAG_CHECK_RESULT_ACKNOWLEDGE), "the quiet «Понятно» must be there")
    }

    @Тогда("^вердикт — строка «Всё в порядке»$")
    fun theVerdictIsTheHealthyLine() {
        assertTrue(hasTag(TAG_VERDICT), "the healthy line must be shown")
        assertTrue(hasText(string(R.string.diagnostics_reliable)), "the verdict must be «Всё в порядке»")
    }

    @Тогда("^результат подтверждён и скрыт$")
    fun theResultIsAcknowledgedAndHidden() {
        assertTrue(DiagnosticsStand.state(world).snapshot?.deliveryCheckAcknowledged == true, "the result is read")
        assertFalse(hasTag(TAG_CHECK_RESULT), "the result must be hidden after «Понятно»")
    }

    @И("^блок вернулся к пояснению и кнопке «Проверить доставку»$")
    fun theBlockReturnedToExplanationAndButton() {
        assertTrue(hasTag(TAG_CHECK_DELIVERY), "the launch button must be back")
        assertTrue(hasText(string(R.string.diagnostics_delivery_explanation)), "the explanation must be back")
    }

    @И("^есть второстепенная кнопка «Исправить», ведущая в «Настроить надёжность»$")
    fun thereIsASecondaryFixLeadingToSetup() {
        assertTrue(hasTag(TAG_CHECK_FIX), "the secondary fix must be there")
        assertTrue(hasText(string(R.string.diagnostics_fix)), "the fix must be named «Исправить»")
        DiagnosticsStand.actRaw(world) { onNodeWithTag(TAG_CHECK_FIX).performScrollTo().performClick() }
        assertTrue(DiagnosticsStand.fixedSetup, "the fix must lead to the setup")
    }

    @И("^есть кнопка «Исправить», ведущая в «Настроить надёжность»$")
    fun thereIsAFixLeadingToSetup() {
        thereIsASecondaryFixLeadingToSetup()
    }

    @Тогда("^баннер-вердикт называет результат: «(.+)»$")
    fun theVerdictBannerNamesTheResult(text: String) {
        assertTrue(hasTag(TAG_WARNING), "the verdict banner must be shown")
        assertTrue(hasText(text), "the verdict must say «$text»")
    }

    @Тогда("^прямо в блоке сказано «(.+)»$")
    fun theBlockSaysDirectly(text: String) {
        assertTrue(hasTag(TAG_CHECK_RESULT), "the block must show the result")
        assertTrue(hasText(text), "the block must say «$text»")
    }

    @Но("^баннера при входе не показано$")
    fun noEntryBanner() {
        assertFalse(hasTag(TAG_CHECK_NOTICE), "«не состоялась» must not be announced")
        assertFalse(hasTag(TAG_WARNING), "and it must not create a verdict banner")
    }

    @Тогда("^результат показан баннером один раз$")
    fun theResultIsShownOnceByBanner() {
        assertEquals(1, countTag(TAG_CHECK_NOTICE), "the result must be announced exactly once")
    }

    @И("^он хранится в блоке до «Понятно»$")
    fun itIsKeptInTheBlock() {
        assertTrue(hasTag(TAG_CHECK_RESULT), "the result must be kept in the block")
    }

    @Но("^повторное открытие экрана не создаёт второго сообщения$")
    fun reopeningDoesNotCreateASecondMessage() {
        DiagnosticsStand.enter(world)
        assertEquals(1, countTag(TAG_CHECK_NOTICE), "reopening must not create a second announcement")
    }

    @Тогда("^отдельной кнопки «Повторить проверку» нет$")
    fun thereIsNoRepeatButton() {
        // У исхода ровно одно действие: «Понятно» или «Исправить». Второй кнопки,
        // которой повторяли бы проверку, в блоке нет.
        val actions = countTag(TAG_CHECK_RESULT_ACKNOWLEDGE) + countTag(TAG_CHECK_FIX)
        assertEquals(1, actions, "the block must offer exactly one action")
    }

    @Тогда("^у карточки надзаголовок «(.+)»$")
    fun theCardCarriesOverline(text: String) {
        var found = false
        DiagnosticsStand.showDeliveryCheckCard(world, lastDeliveryCheckMoment()) {
            found = onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(found, "the card must carry the overline «$text»")
    }

    @И("^подпись «(.+)»$")
    fun theCaptionSays(text: String) {
        if (hasText(text)) return
        // Подпись может быть частью карточки тестового напоминания: она живёт в своей
        // композиции, и её проверяет тот же шаг, что подпись постоянного низа.
        var found = false
        DiagnosticsStand.showDeliveryCheckCard(world, lastDeliveryCheckMoment()) {
            found = onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(found, "the caption must say «$text»")
    }

    @Но("^записей приёма, истории и остатка проверка не создала$")
    fun theCheckCreatedNoIntakes() {
        assertTrue(world.intakes.all().isEmpty(), "the check must not create intakes")
    }

    // --- Проверки: сводка, последнее, следующее -------------------------------------------------

    @Тогда("^сказано «(.+)»$")
    fun itSays(text: String) {
        assertTrue(hasText(text), "the screen must say «$text»")
    }

    @Но("^«0 из 0» и «0 %» на экране нет$")
    fun noZeroOfZero() {
        assertFalse(visibleTexts().any { it.contains("0 из 0") }, "no «0 из 0»")
        assertFalse(visibleTexts().any { it.contains("0 %") }, "no «0 %»")
    }

    @Тогда("^карточка «Последнее напоминание» называет «(.+)»$")
    fun theLastCardNames(text: String) {
        assertTrue(hasTag(TAG_LAST_SHORT), "the last reminder card must be shown")
        // Мир приёмки хранит лекарства без человеческих имён, а Gherkin называет их
        // в косвенном падеже; проверяем формат «Имя — время»: имя занимает свою позицию,
        // время названо точно (НФТ-42).
        val timePart = text.substringAfter(" — ", missingDelimiterValue = text)
        val card = textOfTag(TAG_LAST_SHORT)
        assertTrue(card.contains("— $timePart"), "the card must name «$text», was: $card")
    }

    @Тогда("^под карточкой «Последнее напоминание» отклонение названо словом «(.+)»$")
    fun theLastCardNamesTheDeviation(word: String) {
        assertTrue(hasTag(TAG_LAST_SHORT), "the last reminder card must be shown")
        assertTrue(hasText(word), "the deviation must be said as «$word»")
    }

    @Тогда("^карточка «Следующее напоминание» называет «(.+)»$")
    fun theNextCardNames(text: String) {
        assertTrue(hasTag(TAG_NEXT_SHORT), "the next reminder card must be shown")
        val timePart = text.substringAfter(" — ", missingDelimiterValue = text)
        val card = textOfTag(TAG_NEXT_SHORT)
        assertTrue(card.contains("— $timePart"), "the card must name «$text», was: $card")
    }

    @Тогда("^карточка «Следующее напоминание» говорит «Пока не запланировано»$")
    fun theNextCardSaysNothingPlanned() {
        assertTrue(hasTag(TAG_NEXT_SHORT), "the next reminder card must be shown")
        assertTrue(hasText(string(R.string.diagnostics_next_none)), "the card must say «Пока не запланировано»")
    }

    @И("^объясняет «(.+)»$")
    fun itExplains(text: String) {
        assertTrue(hasText(text), "the screen must explain «$text»")
    }

    @Но("^вердикт остаётся «Всё в порядке» и уточняет «(.+)»$")
    fun theVerdictStaysHealthyAndExplains(text: String) {
        assertTrue(hasTag(TAG_VERDICT), "the healthy line must be shown")
        assertTrue(hasText(string(R.string.diagnostics_reliable)), "the verdict must be «Всё в порядке»")
        assertTrue(hasText(text), "the verdict must explain «$text»")
    }

    // --- Проверки: переходы --------------------------------------------------------------------

    @И("^состояние «Проверки напоминаний» при возврате не теряется$")
    fun theStateSurvivesBack() {
        assertTrue(hasTag(TAG_DIAGNOSTICS), "the screen must still be composed")
    }

    @Тогда("^разрешения и вердикт перечитаны без перезапуска приложения$")
    fun theScreenIsRereadWithoutRestart() {
        assertNotNull(DiagnosticsStand.state(world).snapshot, "the snapshot must be reread")
    }

    @Но("^ничего не зафиксировано молча$")
    fun nothingIsRecordedSilently() {
        assertNotNull(DiagnosticsStand.fixed, "the fix action must have been handled")
    }

    @И("^тестовое срабатывание не отменяется$")
    fun theTestFiringIsNotCancelled() {
        assertNotNull(world.reminders.deliveryCheckAlarm(), "leaving the screen must keep the firing")
    }

    @И("^результат появится при следующем входе$")
    fun theResultAppearsOnTheNextEntry() {
        fireCheck(plusSeconds = 0)
        DiagnosticsStand.enter(world)
        assertTrue(hasTag(TAG_CHECK_RESULT), "the result must appear on the next entry")
    }

    // --- Проверки: доступность -----------------------------------------------------------------

    @Допустим("^уведомления не выданы, а последняя проверка дошла с опозданием$")
    fun notificationsOffAndTheCheckCameLate() {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        startCheck()
        fireCheck(plusSeconds = 12L * SECONDS_IN_MINUTE)
        // Разрешение отзывается после проверки: исход «опоздание» уже записан, а причина
        // ненадёжности старше исхода и называется словами (ОВ-107).
        world.system.deny(ReminderPermission.NOTIFICATIONS)
        DiagnosticsStand.refresh(world)
    }

    @Допустим("^на экране есть строки «Исправить», «Пройти настройку заново», кнопки и «Выгрузить журнал»$")
    fun theInteractiveElementsArePresent() {
        world.system.grant(*ReminderPermission.entries.toTypedArray())
        upcomingIntakesArePlanned()
        world.system.deny(ReminderPermission.EXACT_ALARMS)
        DiagnosticsStand.refresh(world)
        assertTrue(hasTag(TAG_FIX), "the fix action must be present")
        assertTrue(hasTag(TAG_RESTART_SETUP), "«Пройти настройку заново» must be present")
        assertTrue(hasTag(TAG_CHECK_DELIVERY), "a button must be present")
        assertTrue(hasTag(TAG_JOURNAL_EXPORT), "the journal export action must be present")
    }

    @Тогда("^его цель нажатия — не меньше 44 dp$")
    fun theTouchTargetsAreLargeEnough() {
        val minimum = dp(SklyankaSizes.MinTouchTarget)
        listOf(TAG_FIX, TAG_RESTART_SETUP, TAG_CHECK_DELIVERY, TAG_JOURNAL_EXPORT).forEach { tag ->
            val height = minHeightOf(tag)
            assertTrue(height >= minimum, "$tag must be at least 44 dp, was $height px")
        }
    }

    @Тогда("^состояние разрешения названо словами «не выдано» или «проверьте вручную»$")
    fun thePermissionStateIsInWords() {
        assertTrue(hasTag(TAG_PERMISSION_MARKER), "the permission row must carry a marker")
        val words = permissionStateWords()
        assertTrue(
            words.contains(string(R.string.diagnostics_state_denied)) ||
                words.contains(string(R.string.diagnostics_state_unknown)),
            "the state must be named in visible words, not by colour alone",
        )
    }

    @И("^исход доставки назван словом «опоздание», а не только цветом$")
    fun theOutcomeIsInWords() {
        assertTrue(hasTag(TAG_CHECK_RESULT), "the outcome must be shown")
        assertTrue(
            visibleTexts().any { it.contains("опоздан", ignoreCase = true) },
            "the outcome must be named as a lateness in words",
        )
    }

    @Но("^вердикт назван текстом «Всё в порядке» или текстом причины$")
    fun theVerdictIsInWords() {
        val healthy = hasTag(TAG_VERDICT) && hasText(string(R.string.diagnostics_reliable))
        val cause = hasTag(TAG_WARNING) && hasText(string(R.string.diagnostics_warning_notifications))
        assertTrue(healthy || cause, "the verdict must be named in words")
    }

    @Тогда("^минимальный текст на экране — не меньше 12 px$")
    fun theSmallestTextIsReadable() {
        assertTrue(
            SklyankaType.CaptionSmall.fontSize.value >= MIN_TEXT_SIZE_SP,
            "the smallest interface text must be at least 12 sp",
        )
    }

    @И("^строки-действия подписаны словами, а не одной иконкой$")
    fun theActionsAreLabelledInWords() {
        assertTrue(hasText(string(R.string.diagnostics_fix)), "the fix action must be labelled")
        assertTrue(
            hasText(string(R.string.diagnostics_action_setup)),
            "the setup action must be labelled",
        )
    }

    // --- Обвязка --------------------------------------------------------------------------------

    private fun startCheck() {
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
        DiagnosticsStand.refresh(world)
    }

    private fun fireCheck(plusSeconds: Long) {
        val dueAt = requireNotNull(world.reminders.deliveryCheckAlarm()) { "the check must be armed" }
        world.clock.moveTo(dueAt.atZone(world.clock.zone).toLocalDateTime().plusSeconds(plusSeconds))
        world.reminders.firedDeliveryCheck()
    }

    private fun expireCheck() {
        val dueAt = requireNotNull(world.reminders.deliveryCheckAlarm()) { "the check must be armed" }
        world.clock.moveTo(dueAt.atZone(world.clock.zone).toLocalDateTime().plusSeconds(5))
    }

    private fun saveBeforeCheck() {
        val snapshot = DiagnosticsStand.state(world).snapshot
        permissionsBeforeCheck = snapshot?.permissions
        reliabilityBeforeCheck = snapshot?.reliability
    }

    private fun planNextReminder(medication: String, date: String, time: String) {
        medication(medication, time)
        world.intakes.pending(medication, world.localMoment(date, time))
        world.reminders.reschedule(RescheduleReason.MANUAL)
        DiagnosticsStand.refresh(world)
    }

    private fun medication(name: String, time: String): String {
        if (!world.medications.has(name)) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = name,
                times = listOf(LocalTime.parse(time)),
            )
        }
        // Мир сценария хранит лекарства без названий: имя — ключ Gherkin. Там, где экран
        // обязан назвать напоминание словами, сценарий даёт лекарству его имя.
        world.medications.update(name) { it.copy(name = name) }
        return world.medications.get(name).id
    }

    private fun record(entry: ReminderLogEntry) {
        runBlocking { world.reminders.journal.record(entry) }
    }

    private fun query(block: ComposeUiTest.() -> Float): Float {
        var result = 0f
        DiagnosticsStand.ask(world) { result = block() }
        return result
    }

    private fun hasText(text: String): Boolean {
        var found = false
        DiagnosticsStand.ask(world) {
            found = onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun hasTag(tag: String): Boolean {
        var found = false
        DiagnosticsStand.ask(world) {
            found = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun countTag(tag: String): Int {
        var count = 0
        DiagnosticsStand.ask(world) {
            count = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
        }
        return count
    }

    private fun countTextIgnoringCase(text: String): Int {
        var count = 0
        DiagnosticsStand.ask(world) {
            count = onAllNodesWithText(text, substring = true, ignoreCase = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        }
        return count
    }

    private fun countTextExact(text: String): Int {
        var count = 0
        DiagnosticsStand.ask(world) {
            count = onAllNodesWithText(text, substring = false, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        }
        return count
    }

    private fun lastDeliveryCheckMoment(): LocalDateTime = requireNotNull(
        world.reminders.deliveryCheckShows().lastOrNull(),
    ) { "the delivery check card must have been shown" }

    private fun textOfTag(tag: String): String {
        var text = ""
        DiagnosticsStand.ask(world) {
            text = onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap(::textsOf)
                .joinToString(" ")
        }
        return text
    }

    private fun topOfFirst(vararg tags: String): Float {
        var top = Float.MAX_VALUE
        DiagnosticsStand.ask(world) {
            tags.forEach { tag ->
                onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
                    .firstOrNull()?.let { top = minOf(top, it.boundsInRoot.top) }
            }
        }
        return top
    }

    private fun leftsOf(tag: String): List<Float> {
        var lefts: List<Float> = emptyList()
        DiagnosticsStand.ask(world) {
            lefts = onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot.left }
        }
        return lefts
    }

    private fun minHeightOf(tag: String): Float {
        var height = 0f
        DiagnosticsStand.ask(world) {
            height = onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .minOfOrNull { it.boundsInRoot.height } ?: 0f
        }
        return height
    }

    private fun dp(value: androidx.compose.ui.unit.Dp): Float = query { with(density) { value.toPx() } }

    private fun permissionStateWords(): List<String> {
        var words: List<String> = emptyList()
        DiagnosticsStand.ask(world) {
            words = onAllNodesWithTag(TAG_PERMISSION_STATE, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap(::textsOf)
        }
        return words
    }

    private fun tagHasAction(tag: String): Boolean {
        var found = false
        DiagnosticsStand.ask(world) {
            found = onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { node -> node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick) != null }
        }
        return found
    }

    private fun groupTitles(): List<String> {
        var titles: List<String> = emptyList()
        DiagnosticsStand.ask(world) {
            titles = onAllNodesWithTag(TAG_PERMISSION_GROUP, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { node -> textsOf(node).firstOrNull().orEmpty() }
        }
        return titles
    }

    private fun visibleTexts(): List<String> {
        var texts: List<String> = emptyList()
        DiagnosticsStand.ask(world) {
            texts = onAllNodesWithTag(TAG_DIAGNOSTICS, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap(::textsOf)
        }
        return texts
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            .orEmpty().map { it.text } + node.children.flatMap(::textsOf)

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun permissionLabel(permission: ReminderPermission): Int = when (permission) {
        ReminderPermission.NOTIFICATIONS -> R.string.diagnostics_permission_notifications
        ReminderPermission.NOTIFICATION_CHANNEL -> R.string.diagnostics_permission_channel
        ReminderPermission.EXACT_ALARMS -> R.string.diagnostics_permission_exact_alarms
        ReminderPermission.FULL_SCREEN_INTENT -> R.string.diagnostics_permission_full_screen
        ReminderPermission.DND_POLICY_ACCESS -> R.string.diagnostics_permission_dnd
        ReminderPermission.BOOT_RECEIVER -> R.string.diagnostics_permission_boot
        ReminderPermission.AUTOSTART -> R.string.diagnostics_permission_autostart
    }

    private fun permissionOfLabel(label: String): ReminderPermission =
        ReminderPermission.entries.firstOrNull { string(permissionLabel(it)) == label }
            ?: error("unknown permission label: «$label»")

    private fun permissionOfReason(reason: String): ReminderPermission = when (reason) {
        "уведомления" -> ReminderPermission.NOTIFICATIONS
        "канал уведомлений" -> ReminderPermission.NOTIFICATION_CHANNEL
        "точные будильники" -> ReminderPermission.EXACT_ALARMS
        "полноэкранные уведомления" -> ReminderPermission.FULL_SCREEN_INTENT
        else -> permissionOfLabel(reason)
    }

    private fun stateOf(state: String): PermissionState = when (state) {
        string(R.string.diagnostics_state_denied) -> PermissionState.DENIED
        string(R.string.diagnostics_state_unknown) -> PermissionState.UNKNOWN
        string(R.string.diagnostics_state_granted) -> PermissionState.GRANTED
        else -> error("unknown permission state: «$state»")
    }

    private companion object {
        const val SECONDS_IN_MINUTE = 60

        const val LAST_INTAKE = "intake-last"
        const val DAILY_TIME = "08:00"
        const val MIN_TEXT_SIZE_SP = 12f
        const val ALIGNMENT_TOLERANCE_PX = 1f
    }
}
