package ru.stohastika.sklyanka.domain.history

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.schedule.FakeProfileRepository
import ru.stohastika.sklyanka.domain.schedule.RecordingOperationLog
import ru.stohastika.sklyanka.domain.today.FakeActiveProfileRepository
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import ru.stohastika.sklyanka.domain.transfer.ImportSource
import ru.stohastika.sklyanka.domain.transfer.SafetyCopy
import ru.stohastika.sklyanka.domain.transfer.TransferFiles
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Выгрузка истории в CSV: строки, шапка, имя файла и исходы записи (SKL-119, Р-35, Р-85 пп. 2–3).
 *
 * Проверяется содержимое, а не факт записи: файл уходит из приложения и открывается таблицей,
 * и «записалось» без проверки строк не значит «выгрузилось то, что нужно». Слова шапки
 * и колонок собирает поверхность (НФТ-42), поэтому здесь проверяются правила: какие записи
 * попадают в таблицу, каким исходом, с каким временем и с какой долей «вовремя».
 */
class HistoryCsvTest {

    private val clock = Months.clock()
    private val activeProfile = FakeActiveProfileRepository(Months.PROFILE_ID)
    private val profiles = FakeProfileRepository().apply {
        put(Profile(id = Months.PROFILE_ID, name = "profile-a", color = 0, isSelf = true, mirrorReminders = true))
        put(
            Profile(
                id = Months.OTHER_PROFILE_ID,
                name = "profile-b",
                color = 0,
                isSelf = false,
                mirrorReminders = true,
            ),
        )
    }
    private val history = FakeHistoryRepository()
    private val files = FakeTransferFiles()
    private val log = RecordingOperationLog()

    // --- Строки таблицы ------------------------------------------------------------------

    @Test
    fun `every outcome of an intake is named and only the taken one carries the on time word`() = runTest {
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = listOf(
                    // Принято в окне: 08:05 при плане в 08:00 (Р-21, ±2 ч).
                    Months.intake(LocalDate.of(2026, 9, 1).atTime(Months.MORNING), IntakeStatus.TAKEN)
                        .copy(actedAt = Months.instant(LocalDate.of(2026, 9, 1).atTime(8, 5))),
                    // Принято с опозданием: 12:00 при плане в 08:00.
                    Months.intake(LocalDate.of(2026, 9, 2).atTime(Months.MORNING), IntakeStatus.TAKEN)
                        .copy(actedAt = Months.instant(LocalDate.of(2026, 9, 2).atTime(12, 0))),
                    Months.intake(LocalDate.of(2026, 9, 3).atTime(Months.MORNING), IntakeStatus.SKIPPED),
                    Months.intake(LocalDate.of(2026, 9, 4).atTime(Months.MORNING), IntakeStatus.MISSED),
                ),
                medications = listOf(Months.medication()),
            ),
        )

        val rows = table().rows

        assertEquals(
            listOf(
                HistoryCsvStatus.TAKEN,
                HistoryCsvStatus.TAKEN,
                HistoryCsvStatus.SKIPPED_BY_PERSON,
                HistoryCsvStatus.MISSED_WITHOUT_MARK,
            ),
            rows.map { it.status },
        )
        assertEquals(listOf(true, false), rows.filter { it.status == HistoryCsvStatus.TAKEN }.map { it.onTime })
        assertTrue(
            rows.filter { it.status != HistoryCsvStatus.TAKEN }.all { it.onTime == null },
            "only the taken intake has an on-time word: there is nothing to compare for the others (R-21, R-27)",
        )
    }

    @Test
    fun `an intake on demand comes with its status and without a planned time`() = runTest {
        val onDemand = Months.medication(
            id = Months.ON_DEMAND_MEDICATION_ID,
            name = "med-prn",
            times = emptyList(),
            scheduleMode = ScheduleMode.PRN,
        )
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = listOf(
                    Months.intake(LocalDate.of(2026, 9, 1).atTime(15, 30), IntakeStatus.TAKEN, onDemand.id),
                ),
                medications = listOf(Months.medication(), onDemand),
            ),
        )

        val row = table().rows.single()

        assertEquals(HistoryCsvStatus.TAKEN_ON_DEMAND, row.status)
        assertNull(row.plannedTime)
        assertNull(row.onTime)
        assertEquals(LocalDateTime.of(2026, 9, 1, 15, 30), row.actedAt)
    }

    @Test
    fun `an intake that has not happened yet is not in the file at all`() = runTest {
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = listOf(
                    Months.intake(LocalDate.of(2026, 9, 1).atTime(Months.MORNING), IntakeStatus.TAKEN),
                    Months.intake(LocalDate.of(2026, 9, 2).atTime(Months.MORNING), IntakeStatus.PENDING),
                ),
            ),
        )

        val rows = table().rows

        assertEquals(1, rows.size)
        assertEquals(LocalDate.of(2026, 9, 1), rows.single().date)
    }

    /**
     * У автоматического пропуска времени отметки нет.
     *
     * `actedAt` такой записи — момент перевода в «пропущено» (ПР-1), а не отметка человека;
     * колонка «Время отметки» у статуса «пропущено — без отметки» обязана быть пустой,
     * как и на экране (`IntakeCard` печатает время только у «принято»).
     */
    @Test
    fun `an automatic miss carries no mark time`() = runTest {
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = listOf(
                    Months.intake(LocalDate.of(2026, 9, 1).atTime(Months.MORNING), IntakeStatus.MISSED),
                    Months.intake(LocalDate.of(2026, 9, 2).atTime(Months.MORNING), IntakeStatus.TAKEN),
                ),
            ),
        )

        val rows = table().rows
        val missed = rows.single { it.status == HistoryCsvStatus.MISSED_WITHOUT_MARK }
        assertNull(missed.actedAt)
        assertNull(missed.onTime)
        assertNotNull(rows.single { it.status == HistoryCsvStatus.TAKEN }.actedAt)
    }

    /** Файл — одного активного профиля: чужое лекарство и удалённое в него не попадают (ПР-10, ПР-14). */
    @Test
    fun `a foreign profile and a deleted medication stay out of the file`() = runTest {
        val foreign = Months.medication(
            id = Months.SECOND_MEDICATION_ID,
            profileId = Months.OTHER_PROFILE_ID,
            name = "med-b",
        )
        val deleted = Months.medication(id = Months.DELETED_MEDICATION_ID, name = "med-c")
            .copy(deletedAt = Instant.parse("2026-09-05T00:00:00Z"))
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = listOf(
                    Months.intake(LocalDate.of(2026, 9, 1).atTime(Months.MORNING), IntakeStatus.TAKEN),
                    Months.intake(LocalDate.of(2026, 9, 2).atTime(Months.MORNING), IntakeStatus.TAKEN, foreign.id),
                    Months.intake(LocalDate.of(2026, 9, 3).atTime(Months.MORNING), IntakeStatus.TAKEN, deleted.id),
                ),
                medications = listOf(Months.medication(), foreign, deleted),
            ),
        )

        assertEquals(listOf("med-a"), table().rows.map { it.medication })
    }

    /** Снятая запись не выгружается: её для продукта больше нет (Р-46, SKL-127). */
    @Test
    fun `a revoked record is not exported`() = runTest {
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = listOf(
                    Months.intake(LocalDate.of(2026, 9, 1).atTime(Months.MORNING), IntakeStatus.TAKEN),
                    Months.intake(LocalDate.of(2026, 9, 2).atTime(Months.MORNING), IntakeStatus.TAKEN)
                        .copy(revokedAt = Instant.parse("2026-09-02T10:00:00Z")),
                ),
            ),
        )

        assertEquals(listOf(LocalDate.of(2026, 9, 1)), table().rows.map { it.date })
    }

    /** Архивированный курс входит: архив придуман ровно для того, чтобы курс не исчезал (ПР-11). */
    @Test
    fun `an archived course stays in the file up to the day it was archived`() = runTest {
        val archived = Months.medication(
            id = Months.ARCHIVED_MEDICATION_ID,
            name = "med-c",
            archivedAt = Months.instant(LocalDate.of(2026, 9, 3).atTime(9, 0)),
        )
        history.put(
            Months.PROFILE_ID,
            Months.slice(
                intakes = listOf(
                    Months.intake(LocalDate.of(2026, 9, 3).atTime(Months.MORNING), IntakeStatus.TAKEN, archived.id),
                    Months.intake(LocalDate.of(2026, 9, 4).atTime(Months.MORNING), IntakeStatus.TAKEN, archived.id),
                ),
                medications = listOf(Months.medication(), archived),
            ),
        )

        assertEquals(listOf(LocalDate.of(2026, 9, 3)), table().rows.map { it.date })
    }

    @Test
    fun `an empty period gives a file with a header and no records`() = runTest {
        history.put(Months.PROFILE_ID, Months.slice(intakes = emptyList()))

        assertEquals(0, table().rows.size)
    }

    // --- Файл ----------------------------------------------------------------------------

    @Test
    fun `the file carries the header, the columns and one line per record`() {
        val document = documentOf(
            header = listOf("profile-a", "1 - 30 September 2026", "not a medical document"),
            columns = listOf("Date", "Medication"),
            rows = listOf(listOf("2026-09-01", "med-a"), listOf("2026-09-02", "med-b")),
        )

        assertEquals(
            listOf(
                "profile-a",
                "1 - 30 September 2026",
                "not a medical document",
                "Date,Medication",
                "2026-09-01,med-a",
                "2026-09-02,med-b",
            ),
            document.content.trim().lines(),
        )
        assertEquals(2, document.records)
        assertEquals(HistoryCsvDocument.MEDIA_TYPE, document.mediaType)
    }

    /** Запятая, кавычка и перевод строки в названии не имеют права разорвать таблицу (НФТ-51-стиль). */
    @Test
    fun `a value with a comma, a quote or a line break is quoted`() {
        val document = documentOf(
            header = emptyList(),
            columns = listOf("Medication"),
            rows = listOf(listOf("Magnesium, B6"), listOf("A \"quoted\" value"), listOf("first\nsecond")),
        )

        assertEquals(
            "Medication\n\"Magnesium, B6\"\n\"A \"\"quoted\"\" value\"\n\"first\nsecond\"\n",
            document.content,
        )
    }

    // --- Исходы записи --------------------------------------------------------------------

    @Test
    fun `a saved file is written whole and leaves a trace without medical data`() = runTest {
        val outcome = export()(documentOf(), ExportTarget("content://target/history.csv"))

        assertEquals(
            HistoryCsvSaveOutcome.Saved(records = 1, bytes = files.written.single().toByteArray().size),
            outcome,
        )
        val record = log.records.single { it.event == OpEvent.HISTORY_EXPORTED }
        assertEquals(Months.PROFILE_ID, record.profileId)
        assertTrue(record.detail.orEmpty().startsWith("saved;records=1;bytes="), record.detail.orEmpty())
        assertFalse(record.detail.orEmpty().contains("med-a"))
    }

    @Test
    fun `no space stops the export before anything is written`() = runTest {
        files.freeSpace = 1

        val outcome = export()(documentOf(), ExportTarget("content://target/history.csv"))

        assertEquals(HistoryCsvSaveOutcome.NotEnoughSpace, outcome)
        assertTrue(files.written.isEmpty())
        assertEquals("no_space;records=1;bytes=${documentOf().sizeBytes}", log.records.single().detail)
    }

    @Test
    fun `an interrupted write takes the target with it`() = runTest {
        files.failOnWrite = true

        val outcome = export()(documentOf(), ExportTarget("content://target/history.csv"))

        assertEquals(HistoryCsvSaveOutcome.Failed, outcome)
        assertEquals(setOf("content://target/history.csv"), files.discarded)
    }

    @Test
    fun `sharing the file hands the same document to the system exchange`() = runTest {
        val transfer = RecordingTransfer()
        val useCase = export(transfer)

        val outcome = useCase.share(documentOf(), { ReportShareResult.CHOSEN })

        assertEquals(ReportTransferOutcome.Shared, outcome)
        val shared = transfer.shared.single()
        assertEquals("sklyanka-history-2026-09-01.csv", shared.fileName)
        assertEquals(HistoryCsvDocument.MEDIA_TYPE, shared.mediaType)
    }

    // --- Обвязка -------------------------------------------------------------------------

    private suspend fun table(): HistoryCsvTable = requireNotNull(
        BuildHistoryCsvTableUseCase(
            activeProfile = activeProfile,
            profiles = profiles,
            history = history,
            onTimeRule = OnTimeRule.TwoHours,
            time = clock,
        ).invoke(Months.WINDOW),
    ) { "the active profile must be in the database" }

    private fun export(transfer: RecordingTransfer = RecordingTransfer()) =
        ExportHistoryCsvUseCase(files = files, transfer = transfer, activeProfile = activeProfile, log = log)

    private fun documentOf(
        header: List<String> = listOf("profile-a"),
        columns: List<String> = listOf("Date"),
        rows: List<List<String>> = listOf(listOf("2026-09-01")),
    ) = HistoryCsvDocument(
        fileName = "sklyanka-history-2026-09-01.csv",
        mediaType = HistoryCsvDocument.MEDIA_TYPE,
        header = header,
        columns = columns,
        rows = rows,
    )
}

/** Файловый порт выгрузки в памяти: помнит, что записали, и умеет отказать (тот же приём, что ПЕР-31). */
private class FakeTransferFiles : TransferFiles {

    val written: MutableList<String> = mutableListOf()
    val discarded: MutableSet<String> = mutableSetOf()
    var freeSpace: Long = Long.MAX_VALUE
    var failOnWrite: Boolean = false

    override fun freeSpaceBytes(): Long = freeSpace

    override suspend fun write(target: ExportTarget, content: String) {
        if (failOnWrite) error("the export write was interrupted")
        written += content
    }

    override suspend fun discard(target: ExportTarget) {
        discarded += target.handle
    }

    override suspend fun read(source: ImportSource): String = error("the history export never reads")

    override suspend fun writeSafetyCopy(content: String): SafetyCopy = error("the history export writes no copy")

    override fun safetyCopy(): SafetyCopy? = null
}
