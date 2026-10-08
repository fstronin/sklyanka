package ru.stohastika.sklyanka.domain.medication

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.schedule.FakeMedicationRepository
import ru.stohastika.sklyanka.domain.schedule.GenerationWindow
import ru.stohastika.sklyanka.domain.schedule.MedicationPlan
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.schedule.PlanResult
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant

/**
 * Возврат удаления в коротком окне (ПМ-24 п. 5, SKL-99).
 *
 * Сценарий времени не знает: срок окна держит магазин «последнее действие» (SKL-30),
 * и проверять его здесь значило бы завести вторые часы правила.
 */
class UndoDeleteMedicationUseCaseTest {

    private val clock = FixedTimeSource(Instant.parse("2026-09-25T09:00:00Z"))
    private val medications = FakeMedicationRepository(clock)
    private val planned = mutableListOf<Pair<String, PlanReason>>()
    private var created = 0

    private val planner = MedicationPlanner { medicationId, reason ->
        planned += medicationId to reason
        PlanResult(
            window = GenerationWindow.of(clock.today(), 3),
            reason = reason,
            medications = listOf(
                MedicationPlan(
                    profileId = Fixtures.PROFILE_ID,
                    medicationId = medicationId,
                    created = created,
                    deleted = 0,
                    unchanged = 0,
                ),
            ),
        )
    }

    private val useCase = UndoDeleteMedicationUseCase(medications, planner)

    private fun seed(deleted: Boolean) {
        medications.put(
            Fixtures.scheduledMedication().copy(
                archivedAt = if (deleted) null else Instant.EPOCH,
                archiveReason = if (deleted) null else ArchiveReason.MANUAL,
                deletedAt = if (deleted) Instant.EPOCH else null,
            ),
        )
    }

    @Test
    fun `the undo clears the deletion and returns the intakes`() = runTest {
        seed(deleted = true)
        created = 4

        val outcome = useCase(Fixtures.MEDICATION_ID)

        assertEquals(UndoDeleteOutcome.Undone(createdIntakes = 4), outcome)
        assertNull(requireNotNull(medications.get(Fixtures.MEDICATION_ID)).deletedAt)
        assertEquals(
            listOf(Fixtures.MEDICATION_ID to PlanReason.MEDICATION_UNDELETED),
            planned,
            "the undo must return the intakes and the alarms",
        )
    }

    @Test
    fun `an existing medication is not undone`() = runTest {
        seed(deleted = false)

        assertEquals(UndoDeleteOutcome.NotDeleted, useCase(Fixtures.MEDICATION_ID))
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `an unknown medication is refused`() = runTest {
        assertEquals(UndoDeleteOutcome.UnknownMedication, useCase(Fixtures.MEDICATION_ID))
    }
}
