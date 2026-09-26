package ru.heuri.sklyanka.domain.medication

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.schedule.FakeMedicationRepository
import ru.heuri.sklyanka.domain.schedule.PlanReason

/** Отправка курса в архив: оба поля разом, перепланирование после записи (ИНВ-9, ПР-11). */
class ArchiveMedicationUseCaseTest {

    private val clock = Cards.clock()
    private val medications = FakeMedicationRepository(clock)

    @Test
    fun `archiving fills both the moment and the reason`() = runTest {
        medications.put(Cards.scheduled())

        val outcome = useCase().invoke(Cards.MEDICATION_ID, ArchiveReason.MANUAL)

        val stored = medications.stored.getValue(Cards.MEDICATION_ID)
        assertNotNull(stored.archivedAt, "the moment of archiving must be filled (INV-9)")
        assertEquals(ArchiveReason.MANUAL, stored.archiveReason)
        assertEquals(ArchiveOutcome.Archived(ArchiveReason.MANUAL, revokedIntakes = 2), outcome)
    }

    @Test
    fun `the confirmed offer archives with the course finished reason`() = runTest {
        medications.put(Cards.scheduled(endDate = Cards.TODAY.minusDays(1)))

        useCase().invoke(Cards.MEDICATION_ID, ArchiveReason.COURSE_FINISHED)

        assertEquals(
            ArchiveReason.COURSE_FINISHED,
            medications.stored.getValue(Cards.MEDICATION_ID).archiveReason,
        )
    }

    @Test
    fun `replanning happens after the medication is already archived`() = runTest {
        medications.put(Cards.scheduled())
        val planner = RecordingPlanner(medications, deleted = 2)

        ArchiveMedicationUseCase(medications, planner).invoke(Cards.MEDICATION_ID, ArchiveReason.MANUAL)

        // Обратный порядок увидел бы курс активным и породил бы приёмы заново.
        assertEquals(true, planner.archivedAtCall)
        assertEquals(listOf(Cards.MEDICATION_ID to PlanReason.MEDICATION_ARCHIVED), planner.calls)
    }

    @Test
    fun `archiving twice is neither an error nor an event`() = runTest {
        medications.put(Cards.scheduled())
        val planner = RecordingPlanner(medications, deleted = 2)
        val useCase = ArchiveMedicationUseCase(medications, planner)

        useCase(Cards.MEDICATION_ID, ArchiveReason.MANUAL)
        val second = useCase(Cards.MEDICATION_ID, ArchiveReason.COURSE_FINISHED)

        assertEquals(ArchiveOutcome.AlreadyArchived, second)
        assertEquals(1, planner.calls.size, "the second attempt must not replan anything")
        assertEquals(
            ArchiveReason.MANUAL,
            medications.stored.getValue(Cards.MEDICATION_ID).archiveReason,
            "the first reason survives",
        )
    }

    @Test
    fun `an unknown medication is reported, not archived silently`() = runTest {
        assertEquals(ArchiveOutcome.UnknownMedication, useCase().invoke("no-such-id", ArchiveReason.MANUAL))
    }

    @Test
    fun `the number of revoked pending intakes is carried out`() = runTest {
        medications.put(Cards.scheduled())
        val planner = RecordingPlanner(medications, deleted = 5)

        val outcome = ArchiveMedicationUseCase(medications, planner)
            .invoke(Cards.MEDICATION_ID, ArchiveReason.MANUAL) as ArchiveOutcome.Archived

        assertEquals(5, outcome.revokedIntakes, "the log needs the number of revoked intakes")
    }

    private fun useCase() = ArchiveMedicationUseCase(medications, RecordingPlanner(medications, deleted = 2))
}
