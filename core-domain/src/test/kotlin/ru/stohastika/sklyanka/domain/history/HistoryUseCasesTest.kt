package ru.stohastika.sklyanka.domain.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import ru.stohastika.sklyanka.domain.schedule.FakeProfileRepository
import ru.stohastika.sklyanka.domain.schedule.RecordingOperationLog
import ru.stohastika.sklyanka.domain.today.FakeActiveProfileRepository
import java.time.LocalDate

/**
 * История и отчёт врачу как сценарии: профиль из контекста, журнал без медицинских данных.
 *
 * Профиль здесь главное. Экран не умеет попросить историю чужого профиля — ему нечем это
 * выразить, и проверяется именно это (ПР-10), а не «мы не забыли передать параметр».
 */
class HistoryUseCasesTest {

    private val clock = Months.clock()
    private val log = RecordingOperationLog()
    private val activeProfile = FakeActiveProfileRepository(Months.PROFILE_ID)
    private val profiles = FakeProfileRepository().apply {
        put(profile(Months.PROFILE_ID, "profile-a", isSelf = true))
        put(profile(Months.OTHER_PROFILE_ID, "profile-b", isSelf = false))
    }
    private val history = FakeHistoryRepository()

    @Test
    fun `the history is read for the active profile and for no other`() = runTest {
        history.put(Months.PROFILE_ID, ownMonth())
        history.put(Months.OTHER_PROFILE_ID, foreignMonth())

        val period = observe().invoke(Months.SEPTEMBER).first()

        assertEquals(Months.PROFILE_ID, period.profileId)
        assertEquals(1, period.byMedication.size)
        assertEquals(Months.MEDICATION_ID, period.byMedication.single().medicationId)
        assertEquals(listOf(Months.PROFILE_ID), history.asked)
    }

    @Test
    fun `switching the active profile recalculates the month for the new one`() = runTest {
        history.put(Months.PROFILE_ID, ownMonth())
        history.put(Months.OTHER_PROFILE_ID, foreignMonth())
        val periods = observe().invoke(Months.SEPTEMBER)

        val own = periods.first()
        activeProfile.switchTo(Months.OTHER_PROFILE_ID)
        val foreign = periods.first()

        assertEquals(Months.PROFILE_ID, own.profileId)
        assertEquals(Months.OTHER_PROFILE_ID, foreign.profileId)
    }

    @Test
    fun `switching the month asks the data layer for the new window`() = runTest {
        history.put(Months.PROFILE_ID, ownMonth())

        val september = observe().invoke(Months.SEPTEMBER).first()
        val october = observe().invoke(Months.OCTOBER).first()

        assertEquals(LocalDate.of(2026, 9, 1), september.from)
        assertEquals(LocalDate.of(2026, 10, 31), october.to)
        assertEquals(
            listOf(HistoryWindow.of(Months.SEPTEMBER), HistoryWindow.of(Months.OCTOBER)),
            history.windows,
        )
    }

    /**
     * Журнал: профиль, период, число записей и длительность — и ни одного результата.
     *
     * «86 %» рядом с `profile_id` это медицинские данные, просто агрегированные (НФТ-19).
     */
    @Test
    fun `the journal keeps the period and the record count and no medical data`() = runTest {
        history.put(Months.PROFILE_ID, ownMonth())

        observe().invoke(Months.SEPTEMBER).first()

        val record = log.records.single { it.event == OpEvent.HISTORY_PERIOD_CALCULATED }
        assertEquals(Months.PROFILE_ID, record.profileId)
        assertEquals("2026-09-01..2026-09-30", record.after)
        assertTrue(record.detail.orEmpty().startsWith("records=5;"))
        assertFalse(log.records.any { it.after?.contains("%") == true })
        assertTrue(
            log.records.none { record ->
                MEDICATION_NAMES.any { name -> record.detail?.contains(name) == true }
            },
        )
    }

    /** Прочерк на плитке «принято вовремя» оставляет след: иначе он выглядит дефектом (ОВ-4). */
    @Test
    fun `an undefined on time share leaves its own trace`() = runTest {
        history.put(Months.PROFILE_ID, ownMonth())

        observe().invoke(Months.SEPTEMBER).first()

        val record = log.records.single { it.event == OpEvent.HISTORY_ON_TIME_UNDEFINED }
        assertEquals("on_time_rule_missing", record.detail)
    }

    @Test
    fun `the report is built for the active profile and names it`() = runTest {
        history.put(Months.PROFILE_ID, ownMonth())

        val report = buildReport().invoke(HistoryWindow.of(Months.SEPTEMBER))

        assertEquals("profile-a", report?.profileName)
        assertEquals(Months.instant(Months.TODAY.atTime(10, 0)), report?.generatedAt)
    }

    @Test
    fun `building the report leaves a trace with the period and the counts`() = runTest {
        history.put(Months.PROFILE_ID, ownMonth())

        buildReport().invoke(HistoryWindow.of(Months.SEPTEMBER))

        val record = log.records.single { it.event == OpEvent.DOCTOR_REPORT_BUILT }
        assertEquals(Months.PROFILE_ID, record.profileId)
        assertEquals("2026-09-01..2026-09-30", record.after)
        assertEquals("records=5;medications=1", record.detail)
    }

    @Test
    fun `a report of a profile that is not in the database is not built`() = runTest {
        activeProfile.switchTo("profile-that-does-not-exist")

        assertNull(buildReport().invoke(HistoryWindow.of(Months.SEPTEMBER)))
    }

    /** Передача отчёта: каждый исход оставляет свой след, включая отмену (`TDD.md §11`). */
    @Test
    fun `every outcome of the transfer is recorded including the cancellation`() = runTest {
        val transfer = RecordingTransfer()
        val useCase = TransferDoctorReportUseCase(transfer, activeProfile, log)
        val document = ReportDocument(fileName = "report.pdf", mediaType = "application/pdf", bytes = "x".toByteArray())

        transfer.next = ReportTransferOutcome.Shared
        assertEquals(ReportTransferOutcome.Shared, useCase.share(document, { ReportShareResult.CHOSEN }))
        transfer.next = ReportTransferOutcome.Cancelled
        assertEquals(ReportTransferOutcome.Cancelled, useCase.save(document, ReportTarget("target")))
        transfer.next = ReportTransferOutcome.Failed("write_failed")
        useCase.save(document, ReportTarget("target"))

        assertEquals(
            listOf("shared;bytes=1", "cancelled;bytes=1", "failed:write_failed;bytes=1"),
            log.records.filter { it.event == OpEvent.DOCTOR_REPORT_TRANSFERRED }.map { it.detail },
        )
    }

    private fun observe() = ObserveAdherenceUseCase(
        activeProfile = activeProfile,
        history = history,
        calculator = AdherenceCalculator(),
        time = clock,
        log = log,
    )

    private fun buildReport() = BuildDoctorReportUseCase(
        activeProfile = activeProfile,
        profiles = profiles,
        history = history,
        calculator = AdherenceCalculator(),
        time = clock,
        log = log,
    )

    private fun ownMonth(): HistorySlice = Months.slice(
        intakes = Months.daily(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)),
        medications = listOf(Months.medication()),
    )

    private fun foreignMonth(): HistorySlice = Months.slice(
        intakes = Months.daily(
            from = LocalDate.of(2026, 9, 1),
            to = LocalDate.of(2026, 9, 30),
            medicationId = Months.SECOND_MEDICATION_ID,
        ),
        medications = listOf(
            Months.medication(id = Months.SECOND_MEDICATION_ID, profileId = Months.OTHER_PROFILE_ID, name = "med-b"),
        ),
        profileId = Months.OTHER_PROFILE_ID,
    )

    private fun profile(id: String, name: String, isSelf: Boolean) = Profile(
        id = id,
        name = name,
        color = 0,
        isSelf = isSelf,
        mirrorReminders = true,
    )

    private companion object {
        val MEDICATION_NAMES = listOf("med-a", "med-b", "med-c")
    }
}

/**
 * История в памяти: отдаёт заранее положенный срез и запоминает, о чём её спрашивали.
 *
 * Запоминает намеренно: главное утверждение этих проверок — что выборка спрашивается
 * ровно про активный профиль, и подтвердить его можно только списком заданных вопросов.
 */
internal class FakeHistoryRepository : HistoryRepository {

    val asked: MutableList<String> = mutableListOf()
    val windows: MutableList<HistoryWindow> = mutableListOf()

    private val slices = MutableStateFlow<Map<String, HistorySlice>>(emptyMap())

    fun put(profileId: String, slice: HistorySlice) {
        slices.value = slices.value + (profileId to slice)
    }

    override fun observe(profileId: String, window: HistoryWindow): Flow<HistorySlice> {
        asked += profileId
        windows += window
        return slices.map { stored ->
            val slice = stored[profileId] ?: HistorySlice.empty(profileId)
            slice.copy(intakes = slice.intakes.filter { it.inside(window) })
        }
    }
}

private fun Intake.inside(window: HistoryWindow): Boolean {
    val date = scheduledAt.toLocalDate()
    return !date.isBefore(window.from) && !date.isAfter(window.to)
}

/** Передача отчёта, которой можно назначить исход: настоящая живёт в `app`. */
internal class RecordingTransfer : DoctorReportTransfer {

    var next: ReportTransferOutcome = ReportTransferOutcome.Shared

    val shared: MutableList<ReportDocument> = mutableListOf()
    val saved: MutableList<ReportDocument> = mutableListOf()

    override suspend fun share(document: ReportDocument, launcher: ReportShareLauncher): ReportTransferOutcome {
        shared += document
        launcher.launch(ReportShare("content://share/report.pdf", document.fileName, document.mediaType))
        return next
    }

    override suspend fun save(document: ReportDocument, target: ReportTarget): ReportTransferOutcome {
        saved += document
        return next
    }
}
