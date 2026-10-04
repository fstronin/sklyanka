package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalExportOutcome
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.ReminderLogRetention
import ru.stohastika.sklyanka.domain.reminder.ReminderLogRotation
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * Доменные шаги приёмки SKL-65: ротация журнала напоминаний (ПМ-30).
 *
 * Правило не копируется: ротацию выполняет настоящий `RotateReminderJournalUseCase`
 * поверх журнала мира сценария, выгрузку — настоящий `ExportReminderJournalUseCase`,
 * сводку — настоящий `BuildReminderDiagnosticsUseCase`. Здесь живут только данные
 * Предыстории и проверки последствий.
 *
 * «Не на критическом пути» — утверждение о сборке, а не о настроении: шаги читают
 * точки композиции (`SklyankaApp`, триггер) и требуют, чтобы очистка планировалась
 * корутиной, а не выполнялась в запуске.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions", "LargeClass")
class ReminderLogRotationSteps(private val world: AcceptanceWorld) {

    private var rotation: ReminderLogRotation? = null
    private var journalSizeBefore: Int = 0
    private var weekShows: Int = 0
    private var oldIds: List<String> = emptyList()
    private var protectedIds: List<String> = emptyList()
    private var csvRows: List<List<String>> = emptyList()
    private var startupSource: String = ""

    // --- Предыстория: что лежит в журнале -------------------------------------------------------

    @Допустим("^журнал напоминаний хранит записи старше 90 суток$")
    fun theJournalKeepsRecordsOlderThanNinetyDays() {
        oldIds = listOf("old-91", "old-120", "old-200")
        world.reminders.seedJournal(
            oldIds.mapIndexed { index, id -> seeded(intakeId = id, daysAgo = OLD_RECORD_AGES[index]) },
        )
    }

    @Допустим("^в журнале напоминаний есть срабатывания за последние 7 суток$")
    fun theJournalCarriesShowsWithinTheLastWeek() {
        weekShows = WEEK_SHOWS
        protectedIds = (1..weekShows).map { "week-$it" }
        world.reminders.seedJournal(
            protectedIds.map { id ->
                seeded(
                    intakeId = id,
                    hoursAgo = PROTECTED_FIRST_HOUR + Math.floorMod(id.hashCode(), PROTECTED_HOURS.toInt()),
                )
            },
        )
    }

    @Допустим("^журнал напоминаний разросся больше двух мегабайт$")
    fun theJournalGrewOverTwoMegabytes() {
        seedUntilOverTheCap(minutesAgo = { OUTSIDE_FIRST_MINUTE + it })
    }

    @Допустим("^журнал напоминаний разросся больше двух мегабайт за последние 7 суток$")
    fun theJournalGrewOverTwoMegabytesWithinTheProtectedWindow() {
        seedUntilOverTheCap(minutesAgo = { it % PROTECTED_MINUTES })
    }

    @Допустим("^в журнале напоминаний есть запись сорокадневной давности$")
    fun theJournalCarriesAFortyDayRecord() {
        world.reminders.seedJournal(listOf(seeded(intakeId = FORTY_DAY_ID, daysAgo = FORTY_DAYS)))
    }

    // --- Действие: ротация ----------------------------------------------------------------------

    @Когда("^приложение проводит ротацию журнала напоминаний$")
    fun theApplicationRotatesTheJournal() {
        journalSizeBefore = world.reminders.journalEntries().size
        rotation = world.reminders.rotateJournal()
    }

    @Когда("^человек выгружает журнал напоминаний файлом$")
    fun thePersonExportsTheJournal() {
        val export = ExportReminderJournalUseCase(
            journal = world.reminders.journal,
            files = world.transfer.files,
            time = world.clock.time,
        )
        val outcome = runBlocking { export(ExportTarget(JOURNAL_HANDLE)) }
        assertTrue(outcome is ReminderJournalExportOutcome.Saved, "the export must be saved: $outcome")
        val content = world.transfer.files.documents.getValue(JOURNAL_HANDLE)
        csvRows = content.trim().lines().drop(1).map { row -> row.split(',') }
    }

    // --- Последствия: что осталось в журнале ----------------------------------------------------

    @Тогда("^записей старше 90 суток в журнале не осталось$")
    fun noRecordsOlderThanNinetyDaysStay() {
        val cutoff = world.clock.now().minus(ReminderLogRetention.MAX_AGE)
        assertTrue(
            world.reminders.journalEntries().none { it.occurredAt.isBefore(cutoff) },
            "the rotation must remove everything beyond the age limit",
        )
        val remaining = world.reminders.journalEntries().mapNotNull { it.intakeId }
        oldIds.forEach { id -> assertFalse(remaining.contains(id), "the old record $id must be gone") }
    }

    @Тогда("^записи за последние 7 суток не тронуты$")
    fun theProtectedWeekIsUntouched() {
        val remaining = world.reminders.journalEntries().mapNotNull { it.intakeId }
        protectedIds.forEach { id -> assertTrue(remaining.contains(id), "the protected record $id must stay") }
    }

    @Тогда("^ни одна запись журнала не удалена$")
    fun noRecordWasRemoved() {
        assertNull(rotation, "nothing outside the protected window may be removed")
        assertEquals(journalSizeBefore, world.reminders.journalEntries().size, "the journal must stay whole")
    }

    @Тогда("^журнал занимает не больше двух мегабайт$")
    fun theJournalFitsTwoMegabytes() {
        assertTrue(
            world.reminders.journalStoredBytes() <= ReminderLogRetention.MAX_BYTES,
            "the journal must fit the volume cap, got ${world.reminders.journalStoredBytes()} bytes",
        )
    }

    @Тогда("^сводка «вовремя за 7 дней» по-прежнему считает срабатывания$")
    fun theWeekSummaryStillCountsTheShows() {
        val snapshot = runBlocking { diagnostics()() }

        assertEquals(weekShows, snapshot.onTime.fired, "the week shows must survive the rotation")
        assertEquals(weekShows, snapshot.onTime.onTime, "every seeded show was on time")
    }

    // --- Последствия: факт ротации --------------------------------------------------------------

    @Тогда("^в журнале появилась запись о ротации$")
    fun theJournalCarriesARotationFact() {
        val fact = rotationFact()
        assertEquals(rotation, ReminderLogRotation.parse(fact.reason), "the fact must repeat the rotation outcome")
    }

    @И("^запись о ротации называет число удалённых записей$")
    fun theRotationFactNamesTheCounts() {
        val numbers = requireNotNull(ReminderLogRotation.parse(rotationFact().reason)) {
            "the rotation fact must carry countable numbers"
        }
        assertTrue(numbers.removed > 0, "the fact exists only when something was removed")
    }

    @Но("^в записи о ротации нет ни одного идентификатора приёма$")
    fun theRotationFactCarriesNoIntakeIdentifier() {
        val fact = rotationFact()
        assertNull(fact.intakeId, "the fact must carry no intake identifier (НФТ-19)")
        assertNull(fact.medicationId, "the fact must carry no medication identifier (НФТ-19)")
        assertNull(fact.profileId, "the fact must carry no profile identifier (НФТ-19)")
        assertNull(fact.scheduledAt)
        assertNull(fact.firedAt)
    }

    // --- Последствия: выгрузка ------------------------------------------------------------------

    @Тогда("^в выгрузке есть запись сорокадневной давности$")
    fun theExportCarriesTheFortyDayRecord() {
        val fortyDaysAgo = world.clock.now().minus(Duration.ofDays(FORTY_DAYS))
        assertTrue(
            csvOccurredAt().any { moment -> Duration.between(moment, fortyDaysAgo).abs().seconds <= SECOND },
            "the export window must be wider than the old thirty days",
        )
    }

    @Но("^в выгрузке нет записей старше 90 суток$")
    fun theExportCarriesNoRecordsOlderThanNinetyDays() {
        val cutoff = world.clock.now().minus(ReminderLogRetention.MAX_AGE)
        csvOccurredAt().forEach { moment ->
            assertFalse(moment.isBefore(cutoff), "the export must not carry evicted records: $moment")
        }
    }

    // --- Незаметность очистки: точки композиции -------------------------------------------------

    @Когда("^приложение запускается$")
    fun theApplicationStarts() {
        startupSource = source(SKLYANKA_APP)
        assertTrue(
            startupSource.contains("reminderLogRotation.schedule()"),
            "the application must schedule the rotation at startup",
        )
    }

    @Тогда("^ротация журнала запланирована фоновой работой$")
    fun theRotationIsScheduledInTheBackground() {
        val trigger = source(ROTATION_TRIGGER)
        assertTrue(trigger.contains("scope.launch"), "the rotation must be launched, not awaited")
        val callAt = startupSource.indexOf("reminderLogRotation.schedule()")
        val launchAt = startupSource.lastIndexOf("startupScope.launch")
        assertTrue(launchAt in 0 until callAt, "the schedule call must live inside the background scope")
    }

    @И("^очистка не поднимает служб и уведомлений$")
    fun theCleanupRaisesNoServicesAndNotifications() {
        val sources = listOf(ROTATION_TRIGGER, ROTATION_USE_CASE).joinToString("\n", transform = ::source)
        FORBIDDEN_TOKENS.forEach { token ->
            assertFalse(sources.contains(token), "the rotation must not touch $token (ПМ-30, п. 5)")
        }
    }

    // --- Обвязка --------------------------------------------------------------------------------

    private fun rotationFact(): ReminderLogEntry = requireNotNull(
        world.reminders.journalEntries().lastOrNull { it.event == ReminderEvent.ROTATED },
    ) { "the journal must carry the rotation fact" }

    private fun csvOccurredAt(): List<Instant> = csvRows.map { row -> Instant.parse(row[OCCURRED_AT_COLUMN]) }

    private fun seedUntilOverTheCap(minutesAgo: (Long) -> Long) {
        val cap = ReminderLogRetention.MAX_BYTES
        val batch = mutableListOf<ReminderLogEntry>()
        var index = 0L
        while (world.reminders.journalStoredBytes() <= cap) {
            repeat(SEED_BATCH) {
                batch += seeded(intakeId = uuidLike(index), minutesAgo = minutesAgo(index))
                index++
            }
            check(index <= SEED_GUARD) { "the journal never reached $cap bytes: seed guard is $SEED_GUARD records" }
            world.reminders.seedJournal(batch.toList())
            batch.clear()
        }
    }

    private fun seeded(
        intakeId: String,
        daysAgo: Long = 0,
        hoursAgo: Long = 0,
        minutesAgo: Long = 0,
    ): ReminderLogEntry {
        val occurredAt = world.clock.now()
            .minus(Duration.ofDays(daysAgo))
            .minus(Duration.ofHours(hoursAgo))
            .minus(Duration.ofMinutes(minutesAgo))
        return ReminderLogEntry(
            occurredAt = occurredAt,
            event = ReminderEvent.SHOWN,
            intakeId = intakeId,
            medicationId = "$intakeId-medication",
            profileId = "$intakeId-profile",
            scheduledAt = occurredAt,
            firedAt = occurredAt,
            deviationSeconds = 0,
            channel = ReminderChannel.REGULAR,
            delivered = true,
        )
    }

    /** Идентификатор длиной UUID: сценарию нужен реалистичный объём записи, а не смысл. */
    private fun uuidLike(index: Long): String = String.format(Locale.ROOT, "%08d-0000-0000-0000-%012d", index, index)

    private fun source(path: String): String = File(RepositoryLayout.root, path).readText()

    private fun diagnostics(): BuildReminderDiagnosticsUseCase = BuildReminderDiagnosticsUseCase(
        journal = world.reminders.journal,
        alarms = world.reminders.alarms,
        permissions = world.system,
        plan = world.reminders.planSource(),
        reliability = ReadReminderReliabilityUseCase(world.system),
        medications = world.repositories.medicationStore,
        measurement = world.reminders.measurement,
        acknowledgements = world.reminders.acknowledgements,
        time = world.clock.time,
    )

    private companion object {
        const val WEEK_SHOWS = 5
        const val FORTY_DAYS = 40L
        const val FORTY_DAY_ID = "intake-forty-days"
        const val OCCURRED_AT_COLUMN = 0
        const val SECOND = 1L

        /** Первая запись старше 7 суток: снаружи неприкосновенного окна. */
        const val OUTSIDE_FIRST_MINUTE = 8L * 24L * 60L

        /** Записи внутри окна: от часа до почти шести суток назад. */
        const val PROTECTED_FIRST_HOUR = 1L
        const val PROTECTED_HOURS = 140L

        /** Окно в минутах: не даёт разросшемуся «внутри 7 суток» журналу выйти наружу. */
        const val PROTECTED_MINUTES = 6L * 24L * 60L

        const val SEED_BATCH = 512
        const val SEED_GUARD = 100_000L

        /** Возраст старых записей Предыстории: все старше 90 суток. */
        val OLD_RECORD_AGES = listOf(91L, 120L, 200L)

        const val JOURNAL_HANDLE = "chosen://reminder-journal"

        const val SKLYANKA_APP = "app/src/main/kotlin/ru/stohastika/sklyanka/SklyankaApp.kt"
        const val ROTATION_TRIGGER =
            "app/src/main/kotlin/ru/stohastika/sklyanka/reminder/ReminderLogRotationTrigger.kt"
        const val ROTATION_USE_CASE =
            "core-domain/src/main/kotlin/ru/stohastika/sklyanka/domain/reminder/RotateReminderJournalUseCase.kt"

        val FORBIDDEN_TOKENS = listOf(
            "Notification",
            "WorkManager",
            "startForeground",
            "OkHttpClient",
            "HttpURLConnection",
        )
    }
}
