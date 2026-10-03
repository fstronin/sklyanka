package ru.stohastika.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import ru.stohastika.sklyanka.domain.transfer.ImportSource
import ru.stohastika.sklyanka.domain.transfer.SafetyCopy
import ru.stohastika.sklyanka.domain.transfer.TransferFiles
import java.time.Instant
import java.time.ZoneId

/**
 * Выгрузка журнала срабатываний файлом (НФТ-51) и запрет медицинских данных в нём (НФТ-19).
 *
 * Файл отдаётся человеку в руки и уходит из приложения, поэтому проверяется не только
 * то, что он записался, но и то, чего в нём нет.
 */
class ExportReminderJournalUseCaseTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val now: Instant = Instant.parse("2026-09-25T09:00:00Z")
    private val clock = FixedTimeSource(now, zone)
    private val journal = RecordingReminderJournal()
    private val files = FakeTransferFiles()
    private val target = ExportTarget("content://documents/journal")

    private val export = ExportReminderJournalUseCase(journal = journal, files = files, time = clock)

    /**
     * Срабатывание — это предъявление уведомления (`04-reminders.md` §1), и признак НАП-2
     * стоит именно у этой записи: у записи о получении управления его нет и быть не может.
     */
    private suspend fun firing(deviation: Long = 37L, at: Instant = now) {
        journal.record(
            ReminderLogEntry(
                occurredAt = at,
                event = ReminderEvent.SHOWN,
                intakeId = "intake-1",
                medicationId = "med-1",
                profileId = "profile-1",
                scheduledAt = at.minusSeconds(deviation),
                firedAt = at,
                deviationSeconds = deviation,
                channel = ReminderChannel.REGULAR,
                delivered = true,
            ),
        )
    }

    @Test
    fun `the file name carries the local date and nothing else`() {
        assertEquals("sklyanka-reminder-journal-2026-09-25.csv", export.fileName())
    }

    @Test
    fun `every record becomes a row under one header`() = runTest {
        firing()
        firing(deviation = 600L, at = now.plusSeconds(60))

        val document = export.document()

        val lines = document.content.trim().lines()
        assertEquals(3, lines.size, "one header line plus two records")
        assertTrue(lines.first().startsWith("occurred_at,event,intake_id"))
        assertEquals(2, document.records)
    }

    @Test
    fun `the file carries identifiers and the on time verdict, not medication data`() = runTest {
        firing()

        val content = export.document().content

        // Строка записи проверяется целиком: так видно и то, что в файле есть, и то, что
        // в него не приписалось ничего сверх полей журнала (НФТ-19).
        val row = content.trim().lines()[1]
        assertEquals(
            "2026-09-25T09:00:00Z,shown,intake-1,med-1,profile-1," +
                "2026-09-25T08:59:23Z,2026-09-25T09:00:00Z,37,reminders_regular,true,,,,,,,true,,,,,,",
            row,
        )
        // Медицинские данные продукта — текст, введённый человеком по-русски: название
        // лекарства, единица дозировки, имя профиля. Файл без единого символа вне ASCII
        // такого текста не несёт, и проверка эта механическая, а не на внимательность.
        assertFalse(content.any { it.code > ASCII_LIMIT }, "human text would show up as non-ASCII characters")
    }

    @Test
    fun `an environment record fills the new columns and the windows are distinguishable`() = runTest {
        environmentRow(AlarmKind.ALARM_CLOCK, at = now.minusSeconds(120))
        firing(at = now.minusSeconds(60))
        environmentRow(AlarmKind.EXACT_IDLE, at = now)

        val lines = export.document().content.trim().lines()
        val header = lines.first().split(',')
        // Журнал отдаёт записи от новых к старым: плечо «точный» снято последним, «системный» — первым.
        val alarmClockRow = lines[3].split(',')
        val idleRow = lines[1].split(',')

        assertEquals("alarm_kind", header[COLUMN_ALARM_KIND])
        assertEquals("alarm_clock", alarmClockRow[COLUMN_ALARM_KIND])
        assertEquals("HUAWEI", alarmClockRow[COLUMN_MANUFACTURER])
        assertEquals("HUAWEI", alarmClockRow[COLUMN_BRAND])
        assertEquals("12", alarmClockRow[COLUMN_OS_RELEASE])
        assertEquals("HUAWEI Pura 80 Ultra", alarmClockRow[COLUMN_BUILD_DISPLAY])
        assertEquals("true", alarmClockRow[COLUMN_BATTERY_UNRESTRICTED])
        assertEquals("exact_idle", idleRow[COLUMN_ALARM_KIND])
    }

    @Test
    fun `the new columns stay empty on the records of other kinds`() = runTest {
        firing()

        val row = export.document().content.trim().lines()[1].split(',')

        (COLUMN_ALARM_KIND..COLUMN_BATTERY_UNRESTRICTED).forEach { column ->
            assertEquals("", row[column], "column $column must be empty outside an environment record")
        }
    }

    @Test
    fun `the environment columns carry service codes and no medical data`() = runTest {
        environmentRow(AlarmKind.ALARM_CLOCK)

        val content = export.document().content

        assertFalse(content.any { it.code > ASCII_LIMIT }, "environment facts are service codes, not human text")
    }

    private suspend fun environmentRow(kind: AlarmKind, at: Instant = now) {
        journal.record(
            ReminderLogEntry(
                occurredAt = at,
                event = ReminderEvent.ENVIRONMENT,
                reason = ReminderEnvironment(
                    alarmKind = kind,
                    device = DeviceEnvironment(
                        manufacturer = "HUAWEI",
                        brand = "HUAWEI",
                        osRelease = "12",
                        buildDisplay = "HUAWEI Pura 80 Ultra",
                        batteryUnrestricted = true,
                    ),
                ).encode(),
            ),
        )
    }

    @Test
    fun `a value with a separator cannot tear the table apart`() = runTest {
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.SUPPRESSED,
                // Служебная строка причины однажды окажется составной; таблица от этого
                // не имеет права развалиться.
                reason = "already_marked,window_over",
            ),
        )

        val lines = export.document().content.trim().lines()

        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"already_marked,window_over\""))
    }

    @Test
    fun `records older than the retained window stay out`() = runTest {
        firing(at = now.minusSeconds(DAY * 91))
        firing(at = now.minusSeconds(DAY * 40))

        val document = export.document()

        assertEquals(1, document.records, "the export covers the window the journal must keep")
        assertTrue(
            document.content.contains("2026-08-16T09:00:00Z"),
            "the forty-day record must be in the file: the export window is not thirty days anymore",
        )
    }

    @Test
    fun `a written file reports how much of the journal went out`() = runTest {
        firing()

        val outcome = export(target)

        val bytes = files.written!!.toByteArray(Charsets.UTF_8).size
        assertEquals(ReminderJournalExportOutcome.Saved(records = 1, bytes = bytes), outcome)
        assertTrue(files.written!!.startsWith("occurred_at"))
        assertNull(files.discarded, "a written document is never discarded")
    }

    @Test
    fun `a failed write takes the target down with it`() = runTest {
        firing()
        files.failWrite = true

        val outcome = export(target)

        assertEquals(ReminderJournalExportOutcome.Failed, outcome)
        assertEquals(target, files.discarded, "a half written file must not stay behind")
    }

    @Test
    fun `not enough space is found out before the write starts`() = runTest {
        firing()
        files.freeSpace = 1L

        val outcome = export(target)

        assertEquals(ReminderJournalExportOutcome.NotEnoughSpace, outcome)
        assertNull(files.written, "the write never started")
    }

    private companion object {
        const val DAY = 86_400L

        /** Граница ASCII: русский текст продукта за ней, служебные коды журнала — до неё. */
        const val ASCII_LIMIT = 127

        const val COLUMN_ALARM_KIND = 17
        const val COLUMN_MANUFACTURER = 18
        const val COLUMN_BRAND = 19
        const val COLUMN_OS_RELEASE = 20
        const val COLUMN_BUILD_DISPLAY = 21
        const val COLUMN_BATTERY_UNRESTRICTED = 22
    }
}

/**
 * Файловые операции переноса в памяти.
 *
 * Выгрузка журнала идёт тем же механизмом, что перенос данных (НФТ-51), поэтому фейк
 * здесь тот же порт, а не собственный «писатель файлов».
 */
private class FakeTransferFiles : TransferFiles {

    var written: String? = null
    var discarded: ExportTarget? = null
    var failWrite: Boolean = false
    var freeSpace: Long = 100_000_000L

    override fun freeSpaceBytes(): Long = freeSpace

    override suspend fun write(target: ExportTarget, content: String) {
        if (failWrite) error("the chosen document went away")
        written = content
    }

    override suspend fun discard(target: ExportTarget) {
        discarded = target
    }

    override suspend fun read(source: ImportSource): String = error("the journal export reads nothing")

    override suspend fun writeSafetyCopy(content: String): SafetyCopy = error("the journal has no safety copy")

    override fun safetyCopy(): SafetyCopy? = null
}
