package ru.heuri.sklyanka.domain.medication

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.schedule.FakeMedicationRepository
import ru.heuri.sklyanka.domain.schedule.PlanReason
import ru.heuri.sklyanka.domain.schedule.RecordingOperationLog

/** Возврат курса из архива: оба поля разом, генерация после транзакции (ИНВ-9, НАП-11). */
class RestoreMedicationUseCaseTest {

    private val clock = Cards.clock()
    private val medications = FakeMedicationRepository(clock)
    private val transactor = DirectTransactor()
    private val log = RecordingOperationLog()

    @Test
    fun `restoring clears the moment and the reason together`() = runTest {
        medications.put(archived(ArchiveReason.COURSE_FINISHED, endDate = Cards.COURSE_END))

        val outcome = useCase().invoke(Cards.MEDICATION_ID)

        val stored = medications.stored.getValue(Cards.MEDICATION_ID)
        assertNull(stored.archivedAt, "the moment of archiving must be cleared (INV-9)")
        assertNull(stored.archiveReason, "the reason must be cleared with it (INV-9)")
        assertEquals(RestoreOutcome.Restored(createdIntakes = 4), outcome)
    }

    @Test
    fun `restoring keeps the schedule it had before the archive`() = runTest {
        val before = archived(ArchiveReason.MANUAL, endDate = Cards.COURSE_END)
        medications.put(before)

        useCase().invoke(Cards.MEDICATION_ID)

        val after = medications.stored.getValue(Cards.MEDICATION_ID)
        assertEquals(before.times, after.times)
        assertEquals(before.doseAmount, after.doseAmount)
        assertEquals(before.foodRelation, after.foodRelation)
        assertEquals(before.startDate, after.startDate)
        assertEquals(before.endDate, after.endDate)
    }

    @Test
    fun `an open course comes back open`() = runTest {
        medications.put(archived(ArchiveReason.MANUAL, endDate = null))

        val outcome = useCase().invoke(Cards.MEDICATION_ID)

        assertTrue(outcome is RestoreOutcome.Restored)
        assertNull(
            medications.stored.getValue(Cards.MEDICATION_ID).endDate,
            "an open course must not grow an end date (DR-6)",
        )
    }

    @Test
    fun `a course whose end has passed is not restored silently`() = runTest {
        val past = Cards.TODAY.minusDays(35)
        medications.put(archived(ArchiveReason.COURSE_FINISHED, endDate = past, startDate = past.minusDays(6)))
        val planner = RecordingPlanner(medications, created = 4)

        val outcome = restore(planner).invoke(Cards.MEDICATION_ID)

        assertEquals(RestoreOutcome.NeedsPeriodUpdate(past), outcome)
        assertTrue(
            medications.stored.getValue(Cards.MEDICATION_ID).isArchived,
            "the course stays in the archive until its period is fixed",
        )
        assertEquals(emptyList<Pair<String, PlanReason>>(), planner.calls)
    }

    @Test
    fun `a course ending today is still restorable`() = runTest {
        medications.put(archived(ArchiveReason.MANUAL, endDate = Cards.TODAY))

        val outcome = useCase().invoke(Cards.MEDICATION_ID)

        // Последний день входит в курс целиком (ИНВ-15): сегодня он ещё не в прошлом.
        assertTrue(outcome is RestoreOutcome.Restored)
    }

    @Test
    fun `the fields are written inside a transaction and the planner runs after it`() = runTest {
        medications.put(archived(ArchiveReason.MANUAL, endDate = Cards.COURSE_END))
        val planner = RecordingPlanner(medications, created = 4)

        restore(planner).invoke(Cards.MEDICATION_ID)

        assertEquals(1, transactor.opened)
        assertEquals(listOf(Cards.MEDICATION_ID to PlanReason.MEDICATION_RESTORED), planner.calls)
        assertEquals(false, planner.archivedAtCall, "the planner must see an active course")
    }

    @Test
    fun `a course that is not archived is reported, not written again`() = runTest {
        medications.put(Cards.scheduled())

        assertEquals(RestoreOutcome.NotArchived, useCase().invoke(Cards.MEDICATION_ID))
    }

    @Test
    fun `an unknown medication is reported`() = runTest {
        assertEquals(RestoreOutcome.UnknownMedication, useCase().invoke("no-such-id"))
    }

    @Test
    fun `the log carries the reason before the restore and no medication name`() = runTest {
        medications.put(archived(ArchiveReason.COURSE_FINISHED, endDate = Cards.COURSE_END))

        useCase().invoke(Cards.MEDICATION_ID)

        val record = log.records.last()
        assertEquals(ArchiveReason.COURSE_FINISHED.name, record.before)
        assertEquals(Cards.MEDICATION_ID, record.medicationId)
        assertTrue(
            log.records.none { it.after?.contains("med-") == true || it.before?.contains("med-") == true },
            "the log carries identifiers only (NFR-19)",
        )
    }

    private fun archived(
        reason: ArchiveReason,
        endDate: java.time.LocalDate?,
        startDate: java.time.LocalDate = Cards.COURSE_START,
    ) = Cards.scheduled(
        startDate = startDate,
        endDate = endDate,
        archivedAt = Cards.instant(Cards.TODAY.minusDays(1).atTime(Cards.MORNING)),
        archiveReason = reason,
    )

    private fun useCase() = restore(RecordingPlanner(medications, created = 4))

    private fun restore(planner: RecordingPlanner) = RestoreMedicationUseCase(
        medications = medications,
        planner = planner,
        transactor = transactor,
        time = clock,
        log = log,
    )
}
