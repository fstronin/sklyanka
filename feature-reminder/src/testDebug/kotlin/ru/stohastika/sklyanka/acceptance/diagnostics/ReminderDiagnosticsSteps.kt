package ru.stohastika.sklyanka.acceptance.diagnostics

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsUiState
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_NOW
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_JOURNAL_ROW
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Экранные шаги приёмки диагностики напоминаний (SKL-13, группа `@diagnostics`).
 *
 * Шаги живут в `feature-reminder`, потому что говорят об экране диагностики;
 * состояние собирает настоящий `DiagnosticsViewModel` на мире сценария
 * ([DiagnosticsStand]) — журнал, будильники и разрешения те же порты, что у продукта.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class ReminderDiagnosticsSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locale = context.resources.configuration.locales[0]

    // --- Предыстория: последнее сработавшее и следующее ----------------------------------------

    /**
     * Срабатывание с опозданием: будильник доставлен в названный момент, а не в плановый.
     *
     * Плановое время — начало приёма, фактическое — момент доставки; отклонение считается
     * доменом и уходит в журнал (НАП-2, НАП-4). Заодно в окне остаётся напоминание
     * на следующие сутки — его порождает повседневный курс.
     */
    @Допустим("^напоминание о приёме «([^»]+)» (.+?), (\\d{2}:\\d{2}) пришло в (\\d{2}):(\\d{2}):(\\d{2})$")
    @Suppress("LongParameterList")
    fun theReminderCameLate(name: String, date: String, time: String, hour: String, minute: String, second: String) {
        val day = world.dateOf(date)
        val planned = day.atTime(LocalTime.parse(time))
        world.intakes.pending(name, planned)
        world.intakes.pending(name, day.plusDays(1).atTime(LocalTime.parse(time)))
        world.reminders.reschedule(RescheduleReason.MANUAL)
        val fired = world.localMoment(date, "$hour:$minute").withSecond(second.toInt())
        world.clock.moveTo(fired)
        world.reminders.deliverDue()
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек открывает диагностику напоминаний$")
    fun thePersonOpensTheDiagnosticsAt(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        DiagnosticsStand.ask(world) { assertTag(TAG_DIAGNOSTICS) }
    }

    @Когда("^в (\\d{2}:\\d{2}) человек открывает диагностику напоминаний$")
    fun thePersonOpensTheDiagnosticsTodayAt(time: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        DiagnosticsStand.ask(world) { assertTag(TAG_DIAGNOSTICS) }
    }

    @Тогда(
        "^показано последнее сработавшее напоминание с плановым временем (\\d{2}:\\d{2}), " +
            "фактическим (\\d{2}:\\d{2}):(\\d{2}) и отклонением (\\d+) секунд$",
    )
    fun theLastShownIsDisplayed(plannedTime: String, actual: String, seconds: String, deviation: Long) {
        val state = DiagnosticsStand.state(world)
        val last = requireNotNull(state.snapshot?.lastShown) { "the journal must carry the last shown reminder" }
        assertEquals(
            world.clock.momentOf(world.clock.today().atTime(LocalTime.parse(plannedTime))),
            last.scheduledAt,
            "the planned time of the firing",
        )
        assertEquals(
            world.clock.momentOf(world.clock.today().atTime(LocalTime.parse("$actual:$seconds"))),
            last.firedAt,
            "the actual time of the firing",
        )
        assertEquals(deviation, last.deviationSeconds)
        assertTrue(hasText(state, string(R.string.diagnostics_deviation, deviation)), "the deviation must be shown")
        assertTrue(hasText(state, string(R.string.diagnostics_planned, momentText(requireNotNull(last.scheduledAt)))))
        assertTrue(hasText(state, string(R.string.diagnostics_actual, momentText(requireNotNull(last.firedAt)))))
    }

    @И("^показано следующее запланированное напоминание на (.+?), (\\d{2}:\\d{2})$")
    fun theNextReminderIsDisplayed(date: String, time: String) {
        val state = DiagnosticsStand.state(world)
        val expected = world.clock.momentOf(world.localMoment(date, time))
        assertEquals(expected, state.snapshot?.nextAt, "the next reminder must be the armed one")
        assertTrue(
            hasText(state, momentText(expected)),
            "the next reminder must be shown with its time",
        )
    }

    @И("^показано общее число запланированных напоминаний$")
    fun theTotalNumberOfRemindersIsDisplayed() {
        val state = DiagnosticsStand.state(world)
        val total = state.snapshot?.totalScheduled ?: error("the diagnostics must carry the window size")
        assertTrue(total > 0, "the premise arms at least one reminder")
        assertTrue(
            hasText(state, string(R.string.diagnostics_scheduled_total, total)),
            "the number of armed alarms must be shown",
        )
    }

    // --- Диагностика: причина последней пересборки ---------------------------------------------

    @Тогда("^показано время последней пересборки плана (.+?), (\\d{2}:\\d{2})$")
    fun theLastRescheduleMomentIsDisplayed(date: String, time: String) {
        val expected = world.clock.momentOf(world.localMoment(date, time))
        val state = DiagnosticsStand.state(world)
        assertEquals(expected, state.snapshot?.lastReschedule?.occurredAt)
        assertTrue(hasText(state, momentText(expected)), "the moment of the last rebuild must be shown")
    }

    @И("^причиной названо изменение расписания лекарства$")
    fun theReasonIsTheScheduleEdit() {
        assertReasonIs(RescheduleReason.SCHEDULE_EDITED)
    }

    @И("^в диагностике причиной последней пересборки названо ручное действие человека$")
    fun theReasonIsTheManualAction() {
        assertReasonIs(RescheduleReason.MANUAL)
    }

    // --- Диагностика: разрешения ---------------------------------------------------------------

    @Тогда("^состояние уведомлений показано как выданное$")
    fun theNotificationsStateIsGranted() {
        assertPermissionRow(ReminderPermission.NOTIFICATIONS, PermissionState.GRANTED)
    }

    @И("^состояние точных будильников показано как невыданное$")
    fun theExactAlarmsStateIsDenied() {
        assertPermissionRow(ReminderPermission.EXACT_ALARMS, PermissionState.DENIED)
    }

    @И("^состояние доступа к политике «Не беспокоить» показано как невыданное$")
    fun theDndStateIsDenied() {
        assertPermissionRow(ReminderPermission.DND_POLICY_ACCESS, PermissionState.DENIED)
    }

    @И("^состояние автозапуска и энергосбережения показано с пометкой, что его нужно проверить вручную$")
    fun theAutostartStateIsManual() {
        assertPermissionRow(ReminderPermission.AUTOSTART, PermissionState.UNKNOWN)
    }

    // «Человек открывает диагностику напоминаний» объявлен у SKL-20 (DiagnosticsScreenSteps):
    // та же строка и тот же стенд, и второе объявление погасило бы прогон целиком.

    // --- Проверка по требованию ------------------------------------------------------------------

    @Допустим("^по какой-то причине напоминание о приёме (.+?), (\\d{2}:\\d{2}) не запланировано$")
    fun theReminderOfThatIntakeIsNotScheduled(date: String, time: String) {
        val moment = world.localMoment(date, time)
        val intake = world.intakes.pending(world.medications.last, moment)
        assertEquals(null, world.reminders.alarmOf(intake.id), "the premise says no alarm is armed")
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «Проверить сейчас» в диагностике$")
    fun thePersonPressesCheckNowAt(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        DiagnosticsStand.act(world) { onNodeWithTag(TAG_CHECK_NOW).performScrollTo().performClick() }
    }

    @Тогда("^план напоминаний пересобирается$")
    fun thePlanIsRebuilt() {
        val last = requireNotNull(runBlocking { world.reminders.journal.last(ReminderEvent.RESCHEDULED) })
        assertEquals(world.clock.now(), last.occurredAt, "the rebuild must happen at the moment of the press")
        assertEquals(RescheduleReason.MANUAL.code, last.reason)
    }

    /**
     * Момент Предыстории без глагола: «Допустим 25 сентября 2026, 16:00».
     *
     * Сценарий называет, когда он начинается, — этого достаточно, чтобы поставить часы.
     */
    @Допустим("^(\\d{1,2} \\S+ \\d{4}), (\\d{2}:\\d{2})$")
    fun theMomentIs(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
    }

    // --- Журнал срабатываний -------------------------------------------------------------------

    @Допустим("^за последние двое суток пришло (\\d+) напоминаний$")
    fun thatManyRemindersArrived(fired: Int) {
        val now = world.clock.now()
        repeat(fired) { index ->
            val planned = now.minus(Duration.ofMinutes((index + 1).toLong() * MINUTES_BETWEEN))
            record(
                ReminderLogEntry(
                    occurredAt = planned,
                    event = ReminderEvent.SHOWN,
                    intakeId = "intake-journal-$index",
                    scheduledAt = planned,
                    firedAt = planned.plusSeconds(index.toLong()),
                    deviationSeconds = index.toLong(),
                    channel = ReminderChannel.REGULAR,
                    delivered = true,
                ),
            )
        }
    }

    @Тогда("^показан журнал срабатываний с плановым и фактическим временем и отклонением$")
    fun theJournalIsShown() {
        val state = DiagnosticsStand.state(world)
        val shown = requireNotNull(state.snapshot?.journal).filter { it.event == ReminderEvent.SHOWN }
        assertEquals(FIRED, shown.size, "the premise records exactly $FIRED firings")
        shown.forEach { entry ->
            assertNotNull(entry.scheduledAt, "every entry must carry the planned time")
            assertNotNull(entry.firedAt, "every entry must carry the actual time")
            assertNotNull(entry.deviationSeconds, "every entry must carry the deviation")
        }
        assertTrue(hasTag(state, TAG_JOURNAL_ROW), "the journal rows must be on the screen")
        assertTrue(
            hasText(state, string(R.string.diagnostics_deviation, shown.first().deviationSeconds!!)),
            "the deviation must be shown in the journal",
        )
    }

    @И("^журнал вмещает не менее 50 последних записей$")
    fun theJournalHoldsAtLeastFifty() {
        assertTrue(
            BuildReminderDiagnosticsUseCase.JOURNAL_LIMIT >= FIFTY,
            "the journal must keep at least $FIFTY records (НАП-37)",
        )
    }

    // --- Приватность журнала -------------------------------------------------------------------

    @Допустим("^за 25 сентября 2026 сработали напоминания о «([^»]+)» и «([^»]+)»$")
    fun theRemindersFiredForThoseMedications(first: String, second: String) {
        addAnonymousMedication(second)
        val day = world.clock.today()
        listOf(first, second).forEachIndexed { index, name ->
            val medication = world.medications.get(name)
            val planned = day.atTime(LocalTime.parse(DAILY_TIME))
            record(
                ReminderLogEntry(
                    occurredAt = world.clock.momentOf(planned),
                    event = ReminderEvent.SHOWN,
                    intakeId = "intake-journal-$index",
                    medicationId = medication.id,
                    profileId = medication.profileId,
                    scheduledAt = world.clock.momentOf(planned),
                    firedAt = world.clock.momentOf(planned.plusSeconds(index.toLong())),
                    deviationSeconds = index.toLong(),
                    channel = ReminderChannel.REGULAR,
                    delivered = true,
                ),
            )
        }
    }

    @Когда("^человек открывает журнал срабатываний$")
    fun thePersonOpensTheJournal() {
        DiagnosticsStand.ask(world) { assertTag(TAG_DIAGNOSTICS) }
        assertTrue(hasTag(DiagnosticsStand.state(world), TAG_JOURNAL_ROW), "the journal must be on the screen")
    }

    @Тогда("^в записях журнала нет названий лекарств$")
    fun theJournalCarriesNoMedicationNames() {
        val names = world.medications.all().map { it.name }
        val offenders = texts().filter { text -> names.any { text.contains(it) } }
        assertEquals(emptyList<String>(), offenders, "the journal must carry no medication names (НФТ-19)")
    }

    @И("^нет дозировок$")
    fun theJournalCarriesNoDosages() {
        val doseWords = listOf("таблетк", "капсул", "мг", "mg")
        val offenders = texts().filter { text -> doseWords.any { word -> text.contains(word, ignoreCase = true) } }
        assertEquals(emptyList<String>(), offenders, "the journal must carry no dosages (НФТ-19)")
    }

    @И("^нет имён профилей$")
    fun theJournalCarriesNoProfileNames() {
        val offenders = texts().filter { text ->
            world.profiles.all().any { profile -> occursAsToken(text, profile.name) }
        }
        assertEquals(emptyList<String>(), offenders, "the journal must carry no profile names (НФТ-19)")
    }

    /** Имя отдельным словом: «Я» в «Проверьте» именем не является. */
    private fun occursAsToken(text: String, name: String): Boolean =
        Regex("(?<![\\p{L}])${Regex.escape(name)}(?![\\p{L}])").containsMatchIn(text)

    // --- Обвязка -------------------------------------------------------------------------------

    private fun assertReasonIs(reason: RescheduleReason) {
        val state = DiagnosticsStand.state(world)
        assertEquals(
            reason.code,
            state.snapshot?.lastReschedule?.reason,
            "the last rebuild must name its reason",
        )
        assertTrue(hasText(state, string(reasonLabel(reason))), "the reason must be shown in words")
    }

    private fun assertPermissionRow(permission: ReminderPermission, expected: PermissionState) {
        val state = DiagnosticsStand.state(world)
        assertEquals(expected, state.snapshot?.permissions?.get(permission), "the state of $permission")
        assertTrue(hasText(state, string(permissionLabel(permission))), "the row of $permission must be shown")
        assertTrue(hasText(state, string(stateLabel(expected))), "the state must be in words")
    }

    private fun addAnonymousMedication(name: String) {
        if (world.medications.has(name)) return
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = name,
            times = listOf(LocalTime.parse(DAILY_TIME)),
        )
        world.medications.update(name) { it.copy(name = name) }
    }

    private fun record(entry: ReminderLogEntry) {
        runBlocking { world.reminders.journal.record(entry) }
    }

    private fun reasonLabel(reason: RescheduleReason): Int = REASON_LABELS.getValue(reason)

    private fun permissionLabel(permission: ReminderPermission): Int = when (permission) {
        ReminderPermission.NOTIFICATIONS -> R.string.diagnostics_permission_notifications
        ReminderPermission.EXACT_ALARMS -> R.string.diagnostics_permission_exact_alarms
        ReminderPermission.FULL_SCREEN_INTENT -> R.string.diagnostics_permission_full_screen
        ReminderPermission.DND_POLICY_ACCESS -> R.string.diagnostics_permission_dnd
        ReminderPermission.BOOT_RECEIVER -> R.string.diagnostics_permission_boot
        ReminderPermission.AUTOSTART -> R.string.diagnostics_permission_autostart
    }

    private fun stateLabel(state: PermissionState): Int = when (state) {
        PermissionState.GRANTED -> R.string.diagnostics_state_granted
        PermissionState.DENIED -> R.string.diagnostics_state_denied
        PermissionState.UNKNOWN -> R.string.diagnostics_state_unknown
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun momentText(instant: Instant): String = instant.atZone(world.clock.zone).toLocalDateTime()
        .format(DateTimeFormatter.ofPattern(string(R.string.diagnostics_datetime_pattern), locale))

    private fun hasText(state: DiagnosticsUiState, text: String): Boolean {
        var found = false
        DiagnosticsStand.ask(world) {
            found = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun hasTag(state: DiagnosticsUiState, tag: String): Boolean {
        var found = false
        DiagnosticsStand.ask(world) {
            found = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun texts(): List<String> {
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

    private fun androidx.compose.ui.test.ComposeUiTest.assertTag(tag: String) = onNodeWithTag(tag)

    private companion object {
        const val DAILY_TIME = "14:00"
        const val MINUTES_BETWEEN = 37L
        const val FIRED = 12
        const val FIFTY = 50

        val REASON_LABELS: Map<RescheduleReason, Int> = mapOf(
            RescheduleReason.BOOT to R.string.diagnostics_reason_boot,
            RescheduleReason.PACKAGE_REPLACED to R.string.diagnostics_reason_package_replaced,
            RescheduleReason.TIMEZONE_CHANGED to R.string.diagnostics_reason_timezone_changed,
            RescheduleReason.TIME_CHANGED to R.string.diagnostics_reason_time_changed,
            RescheduleReason.SCHEDULE_EDITED to R.string.diagnostics_reason_schedule_edited,
            RescheduleReason.MEDICATION_ADDED to R.string.diagnostics_reason_medication_added,
            RescheduleReason.MEDICATION_ARCHIVED to R.string.diagnostics_reason_medication_archived,
            RescheduleReason.MEDICATION_UNARCHIVED to R.string.diagnostics_reason_medication_unarchived,
            RescheduleReason.ALARM_FIRED to R.string.diagnostics_reason_alarm_fired,
            RescheduleReason.PROFILE_SET_CHANGED to R.string.diagnostics_reason_profile_set_changed,
            RescheduleReason.ANCHOR to R.string.diagnostics_reason_anchor,
            RescheduleReason.MANUAL to R.string.diagnostics_reason_manual,
            RescheduleReason.DATA_IMPORTED to R.string.diagnostics_reason_data_imported,
            RescheduleReason.APP_START to R.string.diagnostics_reason_app_start,
        )
    }
}
