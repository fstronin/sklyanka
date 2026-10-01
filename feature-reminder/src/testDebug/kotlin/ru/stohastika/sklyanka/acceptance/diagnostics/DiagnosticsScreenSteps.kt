package ru.stohastika.sklyanka.acceptance.diagnostics

import android.content.Context
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalExportOutcome
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsUiState
import ru.stohastika.sklyanka.feature.reminder.R
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_NOW
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_EXPORT_JOURNAL
import ru.stohastika.sklyanka.feature.reminder.TAG_JOURNAL_ROW
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Экранные шаги приёмки диагностики напоминаний (SKL-20, группа `@diagnostics`).
 *
 * Шаги живут в `feature-reminder`, потому что говорят об экране диагностики
 * (`SKL-26/TDD.md §4.3`). Состояние собирает настоящий `DiagnosticsViewModel` на мире
 * сценария ([DiagnosticsStand]): журнал, будильники и разрешения — те же порты, что
 * у продукта; «Проверить сейчас» идёт полным обходом НАП-11, выгрузка — файловым портом
 * переноса данных.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class DiagnosticsScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locale = context.resources.configuration.locales[0]

    /** Момент следующего будильника, названный Предысторией. */
    private var nextPlannedAt: Instant? = null

    // --- Предыстория ---------------------------------------------------------------------------

    @Допустим(
        "^последнее напоминание было запланировано на (.+?), (\\d{2}:\\d{2}) и показано в (\\d{2}:\\d{2}):(\\d{2})$",
    )
    @Suppress("LongParameterList")
    fun theLastReminderWasShown(date: String, planned: String, actual: String, seconds: String) {
        val scheduled = world.clock.momentOf(world.localMoment(date, planned))
        val shown = world.clock.momentOf(world.dateOf(date).atTime(LocalTime.parse("$actual:$seconds")))
        record(
            ReminderLogEntry(
                occurredAt = shown,
                event = ReminderEvent.SHOWN,
                intakeId = LAST_INTAKE,
                scheduledAt = scheduled,
                firedAt = shown,
                deviationSeconds = Duration.between(scheduled, shown).seconds,
                channel = ReminderChannel.REGULAR,
                delivered = true,
            ),
        )
    }

    @Допустим("^следующее напоминание запланировано на (.+?), (\\d{2}:\\d{2})$")
    fun theNextReminderIsPlannedAt(date: String, time: String) {
        nextPlannedAt = world.clock.momentOf(world.localMoment(date, time))
    }

    /**
     * Шесть будильников — данность Предыстории.
     *
     * Ставятся прямо в очередь порта: сценарий говорит о числе поставленных будильников
     * в окне, а не о том, из каких расписаний они выведены.
     */
    @Допустим("^в ближайшем окне стоит шесть будильников$")
    fun sixAlarmsAreScheduled() {
        val first = requireNotNull(nextPlannedAt) { "the scenario must name the next reminder before the window" }
        repeat(SIX) { index ->
            runBlocking {
                world.reminders.alarms.schedule("alarm-${index + 1}", first.plus(Duration.ofHours(index.toLong())))
            }
        }
    }

    @Допустим("^последнее перепланирование выполнено (.+?) в (\\d{2}:\\d{2}) по причине «(.+?)»$")
    fun theLastRescheduleHappened(date: String, time: String, reason: String) {
        record(
            ReminderLogEntry(
                occurredAt = world.clock.momentOf(world.localMoment(date, time)),
                event = ReminderEvent.RESCHEDULED,
                reason = reasonOf(reason).code,
                alarmsCancelled = 1,
                alarmsScheduled = 2,
                delivered = true,
            ),
        )
    }

    /**
     * Журнал за 30 суток как данность Предыстории.
     *
     * Отклонения заданы рисунком, а не случайностью: сводка «вовремя за 7 дней»
     * считается по ним, и ожидание проверки обязано быть воспроизводимым.
     */
    @Допустим("^за последние 30 суток записано 180 срабатываний напоминаний$")
    fun oneHundredEightyFiringsAreRecorded() {
        val now = world.clock.now()
        repeat(HUNDRED_EIGHTY) { index ->
            val occurred = now.minus(Duration.ofDays((index % THIRTY).toLong())).minus(
                Duration.ofHours(
                    (
                        index %
                            DAY_HOURS
                        ).toLong(),
                ),
            )
            val late = index % THREE == 0
            val deviation = if (late) LATE_SECONDS else ON_TIME_SECONDS
            record(
                ReminderLogEntry(
                    occurredAt = occurred,
                    event = ReminderEvent.SHOWN,
                    intakeId = "intake-$index",
                    scheduledAt = occurred,
                    firedAt = occurred.plusSeconds(deviation),
                    deviationSeconds = deviation,
                    channel = ReminderChannel.REGULAR,
                    delivered = true,
                ),
            )
        }
    }

    // --- Диагностика: что было и что будет -----------------------------------------------------

    @Когда("^человек открывает диагностику напоминаний$")
    fun thePersonOpensTheDiagnostics() {
        DiagnosticsStand.ask(world) { assertTag(TAG_DIAGNOSTICS) }
    }

    @Тогда(
        "^он видит последнее сработавшее напоминание с плановым временем 08:00, фактическим 08:00:47 и отклонением 47 секунд$",
    )
    fun heSeesTheLastShown() {
        val state = DiagnosticsStand.state(world)
        val last = requireNotNull(state.snapshot?.lastShown) { "the journal must carry the last shown reminder" }
        val planned = requireNotNull(last.scheduledAt)
        assertTrue(
            hasText(state, string(R.string.diagnostics_planned, momentText(planned))),
            "the last reminder must carry its planned time",
        )
        assertTrue(
            hasText(state, string(R.string.diagnostics_actual, momentText(requireNotNull(last.firedAt)))),
            "the last reminder must carry the actual time",
        )
        assertEquals(DURATION_47, requireNotNull(last.deviationSeconds))
        assertTrue(
            hasText(state, string(R.string.diagnostics_deviation, DURATION_47)),
            "the deviation must be shown in seconds (НАП-2)",
        )
    }

    @Тогда("^следующее запланированное на 20:00 с числом будильников в окне$")
    fun theNextIsShownWithTheAlarmCount() {
        val state = DiagnosticsStand.state(world)
        val snapshot = requireNotNull(state.snapshot)
        assertEquals(nextPlannedAt, snapshot.nextAt)
        assertEquals(SIX, snapshot.totalScheduled)
        assertTrue(
            hasText(state, momentText(requireNotNull(snapshot.nextAt))),
            "the next reminder must be shown with its time",
        )
        assertTrue(
            hasText(state, string(R.string.diagnostics_scheduled_total, SIX)),
            "the number of alarms in the window must be shown",
        )
    }

    @Тогда("^время и причину последнего перепланирования$")
    fun theLastRescheduleIsShown() {
        val state = DiagnosticsStand.state(world)
        val entry = requireNotNull(state.snapshot?.lastReschedule) { "the journal must carry the last reschedule" }
        assertTrue(
            hasText(state, momentText(entry.occurredAt)),
            "the moment of the last reschedule must be shown",
        )
        val reason = requireNotNull(RescheduleReason.entries.firstOrNull { it.code == entry.reason })
        assertTrue(
            hasText(state, string(reasonLabel(reason))),
            "the reason of the last reschedule must be shown in words",
        )
    }

    // --- Диагностика: разрешения ---------------------------------------------------------------

    @Тогда(
        "^состояние уведомлений, точных будильников, полноэкранных уведомлений, доступа к режиму «Не беспокоить» и приёмника загрузки показано отдельными строками$",
    )
    fun everyPermissionIsShownOnItsOwnRow() {
        val state = DiagnosticsStand.state(world)
        assertTrue(state.snapshot?.permissions?.isNotEmpty() == true, "the permissions must be read from the system")
        listOf(
            ReminderPermission.NOTIFICATIONS,
            ReminderPermission.EXACT_ALARMS,
            ReminderPermission.FULL_SCREEN_INTENT,
            ReminderPermission.DND_POLICY_ACCESS,
            ReminderPermission.BOOT_RECEIVER,
        ).forEach { permission ->
            assertTrue(hasText(state, string(permissionLabel(permission))), "the row of $permission must be shown")
        }
        assertTrue(hasText(state, string(R.string.diagnostics_state_granted)), "granted states must be in words")
    }

    @Тогда("^строка про автозапуск и энергосбережение подана как «проверьте вручную»$")
    fun theAutostartRowIsHonest() {
        val state = DiagnosticsStand.state(world)
        assertEquals(
            PermissionState.UNKNOWN,
            state.snapshot?.permissions?.get(ReminderPermission.AUTOSTART),
            "the EMUI restrictions are not readable programmatically (НАП-20)",
        )
        assertTrue(
            hasText(state, string(R.string.diagnostics_permission_autostart)),
            "the autostart row must be named",
        )
        assertTrue(
            hasText(state, string(R.string.diagnostics_state_unknown)),
            "the autostart row must say «проверьте вручную»",
        )
    }

    // --- Диагностика: журнал и сводка ----------------------------------------------------------

    @Тогда("^он видит журнал не менее чем на 50 последних срабатываний с отклонениями$")
    fun theJournalHoldsAtLeastFiftyEntries() {
        val state = DiagnosticsStand.state(world)
        val journal = requireNotNull(state.snapshot?.journal)
        assertTrue(journal.size >= FIFTY, "the diagnostics must show at least 50 entries (НАП-37): ${journal.size}")
        assertTrue(journal.any { it.deviationSeconds != null }, "the entries must carry their deviations")
        assertTrue(hasTag(state, TAG_JOURNAL_ROW), "the journal rows must be on the screen")
    }

    @Тогда("^видит сводку, сколько напоминаний сработало вовремя за последние 7 дней$")
    fun theSevenDaySummaryIsShown() {
        val state = DiagnosticsStand.state(world)
        val summary = requireNotNull(state.snapshot?.onTime)
        assertEquals(SEVEN.toLong(), summary.days)
        assertTrue(summary.fired > 0, "the premise records firings in the last seven days")
        val percent = requireNotNull(summary.percent)
        assertTrue(
            hasText(state, string(R.string.diagnostics_on_time, summary.onTime, summary.fired, percent)),
            "the summary must name how many fired on time out of how many",
        )
    }

    @Тогда("^может выгрузить журнал файлом$")
    fun theJournalCanBeExported() {
        DiagnosticsStand.act(world) { onNodeWithTag(TAG_EXPORT_JOURNAL).performScrollTo().performClick() }
        val outcome = DiagnosticsStand.state(world).exportOutcome
        assertTrue(outcome is ReminderJournalExportOutcome.Saved, "the export must succeed: $outcome")
        val csv = exportedCsv()
        // Файл кончается переводом строки: строк данных на одну меньше, чем переводов.
        assertEquals(
            (outcome as ReminderJournalExportOutcome.Saved).records,
            csv.count { it == '\n' } - 1,
            "the file must carry every exported record",
        )
    }

    // --- Проверка по требованию -----------------------------------------------------------------

    @Когда("^человек нажимает «Проверить сейчас»$")
    fun thePersonPressesCheckNow() {
        DiagnosticsStand.act(world) { onNodeWithTag(TAG_CHECK_NOW).performScrollTo().performClick() }
    }

    @Тогда("^перепланирование выполняется сразу$")
    fun theRescheduleHappensAtOnce() {
        val last = runBlocking { world.reminders.journal.last(ReminderEvent.RESCHEDULED) }
        assertNotNull(last, "the check must leave a reschedule record")
        assertEquals(world.clock.now(), last?.occurredAt, "the reschedule must happen at the moment of the press")
    }

    @Тогда("^в журнале появляется запись о перепланировании с причиной «проверка по требованию»$")
    fun theJournalCarriesTheManualReason() {
        val last = requireNotNull(runBlocking { world.reminders.journal.last(ReminderEvent.RESCHEDULED) })
        // «Проверка по требованию» — имя кнопки; причина в журнале та же, что у неё:
        // `MANUAL` («ручное действие человека» словами экрана, SKL-13).
        assertEquals(RescheduleReason.MANUAL.code, last.reason)
        val state = DiagnosticsStand.state(world)
        assertTrue(
            hasText(state, string(reasonLabel(RescheduleReason.MANUAL))),
            "the screen must name the reason of the manual check",
        )
    }

    @Тогда("^на экране обновляется число будильников в окне$")
    fun theAlarmCountIsRefreshed() {
        val state = DiagnosticsStand.state(world)
        val scheduled = world.reminders.scheduled().size
        assertEquals(scheduled, state.snapshot?.totalScheduled)
        assertTrue(
            hasText(state, string(R.string.diagnostics_scheduled_total, scheduled)),
            "the screen must show the refreshed number of alarms",
        )
    }

    // --- Приватность ---------------------------------------------------------------------------

    @Допустим("^устройство находится под сетевым перехватом$")
    fun theDeviceIsUnderNetworkInterception() = Unit

    @Когда("^человек открывает диагностику и выгружает журнал$")
    fun thePersonUsesTheDiagnostics() {
        seedAnonymousEntries()
        DiagnosticsStand.act(world) {
            onNodeWithTag(TAG_EXPORT_JOURNAL).performScrollTo().performClick()
            onNodeWithTag(TAG_CHECK_NOW).performScrollTo().performClick()
        }
        assertTrue(exportedCsv().isNotEmpty(), "the journal must be exported")
    }

    @Тогда("^журнал можно только показать на экране, скопировать текстом или сохранить файлом$")
    fun theJournalStaysOnTheDevice() {
        val state = DiagnosticsStand.state(world)
        assertTrue(hasTag(state, TAG_JOURNAL_ROW), "the journal must be shown on the screen as text")
        assertTrue(hasTag(state, TAG_EXPORT_JOURNAL), "the journal must be savable as a file")
        val shareWords = listOf("Поделиться", "Отправить", "Экспорт в облако")
        val offenders = texts(state).filter { text -> shareWords.any { text.contains(it) } }
        assertEquals(emptyList<String>(), offenders, "there must be no channel to send the journal anywhere")
    }

    // --- Приватность: журнал без медицинских подробностей --------------------------------------

    @Когда("^человек выгружает журнал срабатываний файлом$")
    fun thePersonExportsTheJournal() {
        seedAnonymousEntries()
        DiagnosticsStand.act(world) { onNodeWithTag(TAG_EXPORT_JOURNAL).performScrollTo().performClick() }
    }

    @Тогда("^в нём есть плановое и фактическое время, отклонение, состояние доставки и факт отправки на часы$")
    fun theFileCarriesTheColumns() {
        val csv = exportedCsv()
        val header = csv.lineSequence().first()
        REQUIRED_COLUMNS.forEach { column ->
            assertTrue(header.contains(column), "the journal file must carry the column $column: $header")
        }
        val row = csv.lineSequence().first { it.contains(ReminderEvent.SHOWN.code) }
        val cells = row.split(',')
        val columns = header.split(',')
        listOf("scheduled_at", "fired_at", "deviation_seconds", "delivered").forEach { column ->
            val value = cells[columns.indexOf(column)]
            assertTrue(value.isNotBlank(), "the row must carry $column: $row")
        }
        assertTrue(csv.contains(WATCH_SENT), "the file must say the reminder was sent to the watch")
    }

    @Но("^названий лекарств, дозировок и имён профилей в нём нет$")
    fun theFileCarriesNoMedicalNames() {
        val csv = exportedCsv()
        val medication = world.medications.all().first()
        val profile = world.profiles.get(world.profiles.active)
        assertTrue(csv.contains(medication.id), "the file carries identifiers, and they must be visible (НФТ-50)")
        assertFalse(csv.contains(medication.name), "the journal must carry no medication names (НФТ-19)")
        assertFalse(csv.contains(profile.name), "the journal must carry no profile names (НФТ-19)")
        assertFalse(csv.contains(medication.doseAmount.toPlainString() + " мг"), "the journal must carry no dosages")
    }

    // --- Обвязка -------------------------------------------------------------------------------

    /**
     * Записи журнала без медицинских подробностей: идентификаторы, времена и отклонения.
     *
     * Заводятся действием, потому что сценарий о выгрузке содержимое не называет;
     * имена лекарств и профилей берутся из мира, чтобы проверка их отсутствия была
     * предметной, а не пустой.
     */
    private fun seedAnonymousEntries() {
        val medication = world.medications.all().first()
        val profile = world.profiles.get(world.profiles.active)
        val now = world.clock.now()
        record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.SHOWN,
                intakeId = LAST_INTAKE,
                medicationId = medication.id,
                profileId = profile.id,
                scheduledAt = now.minusSeconds(THIRTY_SECONDS),
                firedAt = now,
                deviationSeconds = THIRTY_SECONDS,
                channel = ReminderChannel.REGULAR,
                delivered = true,
            ),
        )
        record(
            ReminderLogEntry(
                occurredAt = now.minusSeconds(THIRTY_SECONDS),
                event = ReminderEvent.FIRED,
                intakeId = LAST_INTAKE,
                medicationId = medication.id,
                profileId = profile.id,
                scheduledAt = now.minusSeconds(THIRTY_SECONDS),
                firedAt = now.minusSeconds(THIRTY_SECONDS),
                deviationSeconds = 0,
                channel = ReminderChannel.REGULAR,
                watchResult = WATCH_SENT,
            ),
        )
    }

    private fun record(entry: ReminderLogEntry) {
        runBlocking { world.reminders.journal.record(entry) }
    }

    private fun exportedCsv(): String =
        requireNotNull(world.transfer.files.documents[DiagnosticsStand.JOURNAL_HANDLE]) {
            "the journal file must be written to the chosen place"
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

    private fun texts(state: DiagnosticsUiState): List<String> {
        var texts: List<String> = emptyList()
        DiagnosticsStand.ask(world) {
            texts = onAllNodesWithTag(TAG_DIAGNOSTICS, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { textsOf(it) }
        }
        return texts
    }

    private fun hasTag(state: DiagnosticsUiState, tag: String): Boolean {
        var found = false
        DiagnosticsStand.ask(world) {
            found = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun textsOf(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            .orEmpty().map { it.text } + node.children.flatMap { textsOf(it) }

    private fun androidx.compose.ui.test.ComposeUiTest.assertTag(tag: String) = onNodeWithTag(tag).assertIsDisplayed()

    /**
     * Причина из Предыстории: сценарий зовёт её своим именем, мир хранит код.
     *
     * «Сработал будильник» — имя причины `ALARM_FIRED` в сценарии; на экране она
     * называется словами продукта («сработавшее напоминание», отображение
     * `ReminderText`). Соответствие имён — разметка, а не правило (SKL-26, TDD §4.5).
     */
    private fun reasonOf(label: String): RescheduleReason = SCENARIO_REASONS[label]
        ?: REASON_LABELS.entries.first { (_, id) -> string(id) == label }.key

    private fun reasonLabel(reason: RescheduleReason): Int = REASON_LABELS.getValue(reason)

    private fun permissionLabel(permission: ReminderPermission): Int = when (permission) {
        ReminderPermission.NOTIFICATIONS -> R.string.diagnostics_permission_notifications
        ReminderPermission.NOTIFICATION_CHANNEL -> R.string.diagnostics_permission_channel
        ReminderPermission.EXACT_ALARMS -> R.string.diagnostics_permission_exact_alarms
        ReminderPermission.FULL_SCREEN_INTENT -> R.string.diagnostics_permission_full_screen
        ReminderPermission.DND_POLICY_ACCESS -> R.string.diagnostics_permission_dnd
        ReminderPermission.BOOT_RECEIVER -> R.string.diagnostics_permission_boot
        ReminderPermission.AUTOSTART -> R.string.diagnostics_permission_autostart
    }

    private companion object {
        const val SIX = 6
        const val THIRTY_SECONDS = 30L
        const val SEVEN = 7
        const val THIRTY = 30
        const val FIFTY = 50
        const val THREE = 3
        const val DAY_HOURS = 24
        const val HUNDRED_EIGHTY = 180
        const val DURATION_47 = 47L

        /** Имена причин в сценариях Предыстории: мир хранит коды, экран — слова продукта. */
        val SCENARIO_REASONS: Map<String, RescheduleReason> = mapOf(
            "сработал будильник" to RescheduleReason.ALARM_FIRED,
            "проверка по требованию" to RescheduleReason.MANUAL,
        )

        /** Отображение причины пересборки словами — как в `ReminderText` продукта. */
        val REASON_LABELS: Map<RescheduleReason, Int> = mapOf(
            RescheduleReason.BOOT to R.string.diagnostics_reason_boot,
            RescheduleReason.LOCKED_BOOT to R.string.diagnostics_reason_locked_boot,
            RescheduleReason.USER_UNLOCKED to R.string.diagnostics_reason_user_unlocked,
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
        const val LATE_SECONDS = 300L
        const val ON_TIME_SECONDS = 60L
        const val LAST_INTAKE = "intake-journal"
        const val WATCH_SENT = "sent"

        val REQUIRED_COLUMNS = listOf(
            "scheduled_at",
            "fired_at",
            "deviation_seconds",
            "delivered",
            "watch_result",
        )
    }
}
