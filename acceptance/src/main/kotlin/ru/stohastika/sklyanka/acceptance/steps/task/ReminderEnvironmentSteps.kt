package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderEnvironment
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalExportOutcome
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingKey
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import java.time.Instant

/**
 * Доменные шаги приёмки SKL-85: механизм будильников, журнал среды и измерение пилота.
 *
 * Правила не копируются: окно пересобирает настоящий `RescheduleRemindersUseCase`,
 * журнал среды пишет он же, выгрузка идёт настоящим `ExportReminderJournalUseCase`.
 * Здесь живут только Предыстория и последствия, которых у сценария не выпросить портами:
 * выдача права системным событием, смена варианта измерения и разбор выгрузки.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions", "LargeClass")
class ReminderEnvironmentSteps(private val world: AcceptanceWorld) {

    /**
     * Слова сценария → служебные коды.
     *
     * В приёмке нет ресурсов экранов, и «системный будильник» здесь — код `alarm_clock`,
     * которым размечается журнал и выгрузка. Соответствие слов и кодов проверяет
     * экранная приёмка, где доступны настоящие ресурсы.
     */
    private fun kindOf(words: String): AlarmKind = when (words) {
        SYSTEM_ALARM_CLOCK_WORDS -> AlarmKind.ALARM_CLOCK
        EXACT_IDLE_WORDS -> AlarmKind.EXACT_IDLE
        else -> error("unknown alarm kind: $words")
    }

    // --- Механизм и границы окон ---------------------------------------------------------------

    @Когда("^приложение пересобирает план напоминаний$")
    fun theApplicationReschedulesThePlan() {
        world.reminders.reschedule(RescheduleReason.MANUAL)
    }

    @Когда("^приложение пересобирает план напоминаний ещё раз$")
    fun theApplicationReschedulesThePlanOnceMore() {
        world.reminders.reschedule(RescheduleReason.MANUAL)
    }

    @Тогда("^в (?:журнале|записи) назван тип будильника «([^»]+)»$")
    fun theJournalNamesTheAlarmKind(words: String) {
        val expected = kindOf(words)
        val environment = lastEnvironment()
        assertEquals(expected, environment.alarmKind, "the journal record must name the acting mechanism")
    }

    // --- Выдача права на точные будильники (НАП-16, И-20) --------------------------------------

    @Допустим("^напоминания не поставлены$")
    fun noRemindersAreArmed() {
        world.reminders.scheduled().keys.forEach { intakeId ->
            runBlocking { world.reminders.alarms.cancel(intakeId) }
        }
        assertEquals(emptyMap<String, Instant>(), world.reminders.scheduled(), "the premise arms nothing")
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек выдаёт право в системных настройках$")
    fun thePersonGrantsTheRightInSystemSettings(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        world.system.grant(ReminderPermission.EXACT_ALARMS)
        // Приёмник системного события переводит выдачу права в причину и пересобирает
        // план тем же путём, что `SystemEventReceiver` (НАП-11, НАП-16).
        world.reminders.reschedule(RescheduleReason.EXACT_ALARM_GRANTED)
    }

    @Тогда("^приложение само пересобирает план напоминаний$")
    fun theApplicationReschedulesByItself() {
        val last = requireNotNull(runBlocking { world.reminders.journal.last(ReminderEvent.RESCHEDULED) })
        assertEquals(world.clock.now(), last.occurredAt, "the rebuild must happen at the moment of the grant")
    }

    @И("^последняя пересборка в журнале названа выдачей права$")
    fun theLastRebuildIsNamedAsTheGrant() {
        val last = requireNotNull(runBlocking { world.reminders.journal.last(ReminderEvent.RESCHEDULED) })
        assertEquals(RescheduleReason.EXACT_ALARM_GRANTED.code, last.reason)
    }

    // --- Журнал среды --------------------------------------------------------------------------

    @Тогда("^в журнале появляется запись среды$")
    fun theJournalCarriesAnEnvironmentRecord() {
        assertTrue(environmentEntries().isNotEmpty(), "the rebuild must leave an environment record")
    }

    @И("^в записи названы производитель и версия прошивки устройства$")
    fun theRecordNamesTheManufacturerAndTheFirmware() {
        val environment = lastEnvironment()
        assertEquals(world.reminders.environment.manufacturer, environment.device.manufacturer)
        assertEquals(world.reminders.environment.buildDisplay, environment.device.buildDisplay)
    }

    @И("^в записи названо состояние энергосбережения$")
    fun theRecordNamesTheBatteryState() {
        val environment = lastEnvironment()
        assertEquals(world.reminders.environment.batteryUnrestricted, environment.device.batteryUnrestricted)
    }

    @Но("^названий лекарств и имён профилей в записи нет$")
    fun theRecordCarriesNoMedicalData() {
        val payload = requireNotNull(environmentEntries().firstOrNull()?.reason)
        forbiddenNames().forEach { name ->
            assertFalse(payload.contains(name), "the environment record must not carry «$name» (НФТ-19)")
        }
    }

    @Допустим("^журнал уже содержит запись среды, снятую при пересборке плана$")
    fun theJournalAlreadyCarriesAnEnvironmentRecord() {
        if (environmentEntries().isEmpty()) world.reminders.reschedule(RescheduleReason.MANUAL)
        assertTrue(environmentEntries().isNotEmpty(), "the premise must leave an environment record")
    }

    @И("^среда устройства и тип будильника не менялись$")
    fun theEnvironmentAndTheMechanismDidNotChange() {
        // Ничего не делаем: неизменность среды — условие сценария, а не действие.
        assertEquals(1, environmentEntries().size, "the premise expects a single environment record")
    }

    @Тогда("^новой записи среды в журнале не появляется$")
    fun noSecondEnvironmentRecordAppears() {
        assertEquals(1, environmentEntries().size, "an unchanged environment must not be recorded twice (SKL-47)")
    }

    // --- Выгрузка журнала ----------------------------------------------------------------------

    @Допустим("^записи журнала охватывают два окна: «([^»]+)», затем «([^»]+)»$")
    fun theJournalCoversTwoMeasurementWindows(first: String, second: String) {
        world.reminders.measurementAvailable = true
        setVariant(kindOf(first))
        world.reminders.reschedule(RescheduleReason.MANUAL)
        setVariant(kindOf(second))
        world.reminders.reschedule(RescheduleReason.MANUAL)
        val kinds = environmentEntries().map { entry ->
            requireNotNull(ReminderEnvironment.parse(entry.reason.orEmpty())).alarmKind
        }
        assertEquals(listOf(kindOf(second), kindOf(first)), kinds, "the journal must carry both windows")
    }

    @Когда("^человек выгружает журнал файлом$")
    fun thePersonExportsTheJournal() {
        val export = ExportReminderJournalUseCase(
            journal = world.reminders.journal,
            files = world.transfer.files,
            time = world.clock.time,
        )
        val outcome = runBlocking { export(ExportTarget(JOURNAL_HANDLE)) }
        assertTrue(outcome is ReminderJournalExportOutcome.Saved, "the export must be saved: $outcome")
        csv = csvOf(world.transfer.files.documents.getValue(JOURNAL_HANDLE))
    }

    @Тогда("^в файле есть запись среды (?:первого|второго) окна с типом «([^»]+)»$")
    fun theFileCarriesTheWindow(words: String) {
        val expected = kindOf(words).code
        val rows = environmentRows()
        assertTrue(
            rows.any { it[column(ALARM_KIND)] == expected },
            "the export must carry the «$words» window, got ${rows.map { it[column(ALARM_KIND)] }}",
        )
    }

    @И("^производитель, версия прошивки и состояние энергосбережения указаны в этих записях$")
    fun theFileCarriesTheEnvironmentFacts() {
        val rows = environmentRows()
        assertEquals(2, rows.size, "both measurement windows must be in the file")
        rows.forEach { row ->
            assertTrue(row[column(MANUFACTURER)].isNotEmpty(), "the manufacturer must be named")
            assertTrue(row[column(BUILD_DISPLAY)].isNotEmpty(), "the firmware build must be named")
            assertTrue(row[column(BATTERY_UNRESTRICTED)].isNotEmpty(), "the battery state must be named")
        }
    }

    @Но("^названий лекарств и имён профилей в файле нет$")
    fun theFileCarriesNoMedicalData() {
        val content = requireNotNull(csv).toString()
        forbiddenNames().forEach { name ->
            assertFalse(content.contains(name), "the export must not carry «$name» (НФТ-19)")
        }
    }

    // --- Измерение пилота ----------------------------------------------------------------------

    @Допустим("^приложение собрано (без режима измерения|в режиме измерения)$")
    fun theBuildCarriesTheMeasurementMode(state: String) {
        world.reminders.measurementAvailable = state == MEASUREMENT_ON
    }

    @И("^в сохранённой настройке стоит «([^»]+)»$")
    fun theStoredVariantIs(words: String) {
        setVariant(kindOf(words))
    }

    @Тогда("^приём всё равно поставлен системным будильником$")
    fun theIntakeIsArmedWithTheSystemAlarmClockAnyway() {
        assertEquals(
            AlarmKind.ALARM_CLOCK,
            world.reminders.effectiveKind(),
            "a normal build ignores the stored variant",
        )
        assertEquals(AlarmKind.ALARM_CLOCK, lastEnvironment().alarmKind, "the journal must name the real mechanism")
    }

    @И("^переключателя варианта на «Проверке напоминаний» нет$")
    fun theVariantSwitchIsAbsentOnTheCheckScreen() {
        val snapshot = runBlocking { diagnostics()() }
        assertFalse(snapshot.measurementAvailable, "the switch must not exist outside the measuring build")
    }

    @И("^сейчас действует вариант «([^»]+)»$")
    fun theActingVariantIs(words: String) {
        setVariant(kindOf(words))
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек переключает вариант на «([^»]+)»$")
    fun thePersonSwitchesTheVariant(date: String, time: String, words: String) {
        world.clock.moveTo(world.localMoment(date, time))
        // Порядок обязателен и тот же, что у экрана: сначала настройка, потом пересборка
        // (`DiagnosticsViewModel.onAlarmKindChange`), иначе будильники встали бы прежним
        // механизмом (SKL-85, TDD §9.4).
        setVariant(kindOf(words))
        world.reminders.reschedule(RescheduleReason.MANUAL)
    }

    @Тогда("^план напоминаний пересобирается немедленно$")
    fun thePlanIsRebuiltImmediately() {
        val last = requireNotNull(runBlocking { world.reminders.journal.last(ReminderEvent.RESCHEDULED) })
        assertEquals(world.clock.now(), last.occurredAt, "the switch must rebuild the plan at once")
    }

    @И("^в журнале записана смена варианта$")
    fun theVariantChangeIsRecorded() {
        val last = requireNotNull(runBlocking { world.reminders.journal.last(ReminderEvent.SETTINGS_CHANGED) })
        assertEquals(ReminderSettingKey.ALARM_STRATEGY.trace(AlarmKind.EXACT_IDLE.code), last.reason)
    }

    @И("^новая запись среды называет тип «([^»]+)»$")
    fun theNewEnvironmentRecordNamesTheKind(words: String) {
        assertEquals(kindOf(words), lastEnvironment().alarmKind)
    }

    @И("^записи среды дальше не повторяются до следующей смены$")
    fun theEnvironmentRecordsDoNotRepeat() {
        val before = environmentEntries().size
        world.reminders.reschedule(RescheduleReason.MANUAL)
        assertEquals(before, environmentEntries().size, "an unchanged environment must stay silent")
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private var csv: Csv? = null

    private fun setVariant(kind: AlarmKind) {
        runBlocking { world.reminders.settingsStore.setAlarmStrategy(kind) }
    }

    private fun environmentEntries(): List<ReminderLogEntry> = runBlocking {
        world.reminders.journal.recent(RECENT_LIMIT)
    }.filter { it.event == ReminderEvent.ENVIRONMENT }

    private fun lastEnvironment(): ReminderEnvironment {
        val entry = environmentEntries().firstOrNull()
        assertNotNull(entry, "the journal must carry an environment record")
        return requireNotNull(ReminderEnvironment.parse(requireNotNull(entry).reason.orEmpty())) {
            "the environment record must carry a parseable payload"
        }
    }

    private fun diagnostics(): BuildReminderDiagnosticsUseCase = BuildReminderDiagnosticsUseCase(
        journal = world.reminders.journal,
        alarms = world.reminders.alarms,
        permissions = world.system,
        plan = world.reminders.planSource(),
        reliability = ReadReminderReliabilityUseCase(world.system),
        medications = world.repositories.medicationStore,
        measurement = world.reminders.measurement,
        time = world.clock.time,
    )

    private fun forbiddenNames(): List<String> =
        world.medications.all().map { it.name } + world.profiles.all().map { it.name }

    private fun column(name: String): Int {
        val parsed = requireNotNull(csv) { "the journal must be exported first" }
        val index = parsed.header.indexOf(name)
        require(index >= 0) { "the export must carry the $name column" }
        return index
    }

    private fun environmentRows(): List<List<String>> {
        val parsed = requireNotNull(csv) { "the journal must be exported first" }
        return parsed.rows.filter { it[parsed.header.indexOf(EVENT)] == ReminderEvent.ENVIRONMENT.code }
    }

    /** Таблица выгрузки, разобранная построчно: значения сценария разделителей не содержат. */
    private data class Csv(val header: List<String>, val rows: List<List<String>>)

    private fun csvOf(content: String): Csv {
        val lines = content.trim().lines().map { line -> line.split(',') }
        return Csv(header = lines.first(), rows = lines.drop(1))
    }

    private companion object {
        const val RECENT_LIMIT = 200
        const val EVENT = "event"
        const val ALARM_KIND = "alarm_kind"
        const val MANUFACTURER = "manufacturer"
        const val BUILD_DISPLAY = "build_display"
        const val BATTERY_UNRESTRICTED = "battery_unrestricted"
        const val JOURNAL_HANDLE = "chosen://reminder-journal"

        const val SYSTEM_ALARM_CLOCK_WORDS = "системный будильник"
        const val EXACT_IDLE_WORDS = "точный будильник"
        const val MEASUREMENT_ON = "в режиме измерения"
    }
}
