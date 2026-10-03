package ru.stohastika.sklyanka.domain.medication

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.MedicationActionType
import ru.stohastika.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.schedule.FakeIntakeRepository
import ru.stohastika.sklyanka.domain.schedule.FakeMedicationRepository
import ru.stohastika.sklyanka.domain.schedule.FakeProfileRepository
import ru.stohastika.sklyanka.domain.schedule.GenerationWindow
import ru.stohastika.sklyanka.domain.schedule.MedicationPlan
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.schedule.PlanResult
import ru.stohastika.sklyanka.domain.schedule.RecordingOperationLog
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant

/**
 * Мягкое удаление ошибочно заведённого лекарства (ПМ-24, ПР-14, SKL-99).
 *
 * Граница «ни одной отметки `TAKEN`» проверяется на двух уровнях: объяснимый отказ
 * здесь и условное обновление хранилища на настоящей базе (`MedicationDeletionTest`).
 * Гонка «отметка между чтением и записью» воспроизводится обёрткой, которая ставит
 * отметку ровно в момент удаления.
 */
class DeleteMedicationUseCaseTest {

    private val start: Instant = Instant.parse("2026-09-25T09:00:00Z")
    private val clock = FixedTimeSource(start)
    private val medications = FakeMedicationRepository(clock)
    private val intakes = FakeIntakeRepository(clock, medications)
    private val analytics = RecordingAnalytics()
    private val planned = mutableListOf<Pair<String, PlanReason>>()
    private var revoked = 0

    private val planner = MedicationPlanner { medicationId, reason ->
        planned += medicationId to reason
        PlanResult(
            window = GenerationWindow.of(clock.today(), 3),
            reason = reason,
            medications = listOf(
                MedicationPlan(
                    profileId = Fixtures.PROFILE_ID,
                    medicationId = medicationId,
                    created = 0,
                    deleted = revoked,
                    unchanged = 0,
                ),
            ),
        )
    }

    private fun useCase(overrides: MedicationRepository? = null) = DeleteMedicationUseCase(
        medications = overrides ?: medications,
        intakes = intakes,
        planner = planner,
        analytics = analytics,
    )

    private fun seedMedication(archived: Boolean = false, deleted: Boolean = false, taken: Boolean = false) {
        val base = Fixtures.scheduledMedication()
        medications.put(
            base.copy(
                archivedAt = if (archived) Instant.EPOCH else null,
                archiveReason = if (archived) ArchiveReason.MANUAL else null,
                deletedAt = if (deleted) Instant.EPOCH else null,
            ),
        )
        if (taken) {
            intakes.put(
                Fixtures.takenIntake().copy(
                    status = IntakeStatus.TAKEN,
                    actedAt = start,
                    source = IntakeSource.PHONE,
                ),
            )
        }
    }

    @Test
    fun `a medication without taken intakes is deleted softly`() = runTest {
        seedMedication(archived = true)
        revoked = 2

        val outcome = useCase()(Fixtures.MEDICATION_ID)

        assertEquals(DeleteOutcome.Deleted(revokedIntakes = 2), outcome)
        val stored = requireNotNull(medications.get(Fixtures.MEDICATION_ID))
        assertTrue(stored.isDeleted, "the deletion moment must be filled")
        assertNull(stored.archivedAt, "INV-21: the archive fields are cleared")
        assertNull(stored.archiveReason, "INV-21: the archive fields are cleared")
        assertEquals(
            listOf(Fixtures.MEDICATION_ID to PlanReason.MEDICATION_DELETED),
            planned,
            "the deletion must be a rescheduling event",
        )
        assertEquals(
            listOf(AnalyticsEvent.MedicationAction(MedicationActionType.DELETED, mode = null)),
            analytics.events,
        )
    }

    @Test
    fun `a medication with a taken intake is not deleted`() = runTest {
        seedMedication(taken = true)

        val outcome = useCase()(Fixtures.MEDICATION_ID)

        assertEquals(DeleteOutcome.HasTakenIntakes, outcome)
        val stored = requireNotNull(medications.get(Fixtures.MEDICATION_ID))
        assertTrue(!stored.isDeleted, "the fact of intake must stay visible")
        assertTrue(planned.isEmpty(), "a refused deletion must not reschedule anything")
        assertTrue(analytics.events.isEmpty(), "a refused deletion is not an event")
    }

    @Test
    fun `an already deleted medication is not a second event`() = runTest {
        seedMedication(deleted = true)

        val outcome = useCase()(Fixtures.MEDICATION_ID)

        assertEquals(DeleteOutcome.AlreadyDeleted, outcome)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `an unknown medication is refused`() = runTest {
        assertEquals(DeleteOutcome.UnknownMedication, useCase()(Fixtures.MEDICATION_ID))
    }

    @Test
    fun `an intake marked between the check and the write wins`() = runTest {
        seedMedication()
        // Гонка: отметка ложится ровно в момент условного обновления, и запись удаления
        // обязана вернуть 0 строк — удаление не имеет права скрыть факт приёма.
        val racing = object : MedicationRepository by medications {
            override suspend fun delete(medicationId: String): Boolean {
                intakes.put(
                    Fixtures.takenIntake().copy(
                        status = IntakeStatus.TAKEN,
                        actedAt = start,
                        source = IntakeSource.PHONE,
                    ),
                )
                return false
            }
        }

        val outcome = useCase(overrides = racing)(Fixtures.MEDICATION_ID)

        assertEquals(DeleteOutcome.HasTakenIntakes, outcome)
        assertTrue(!requireNotNull(medications.get(Fixtures.MEDICATION_ID)).isDeleted)
    }

    @Test
    fun `a lost race without a taken intake is an already deleted record`() = runTest {
        seedMedication()
        val racing = object : MedicationRepository by medications {
            override suspend fun delete(medicationId: String): Boolean {
                medications.delete(medicationId)
                return false
            }
        }

        assertEquals(DeleteOutcome.AlreadyDeleted, useCase(overrides = racing)(Fixtures.MEDICATION_ID))
    }

    private class RecordingAnalytics : Analytics {
        val events = mutableListOf<AnalyticsEvent>()

        override fun track(event: AnalyticsEvent) {
            events += event
        }

        override suspend fun applyConsent(consent: AnalyticsConsent) = Unit
    }
}

/** Проверки, что удалённое лекарство не участвует в соседних сценариях. */
class DeletedMedicationBoundariesTest {

    private val clock = FixedTimeSource(Instant.parse("2026-09-25T09:00:00Z"))
    private val medications = FakeMedicationRepository(clock)
    private val intakes = FakeIntakeRepository(clock, medications)

    private fun deletedMedication(): Medication = Fixtures.scheduledMedication().copy(
        deletedAt = Instant.EPOCH,
        stockLeft = BigDecimal("1"),
    )

    private val planner = MedicationPlanner { _, _ -> error("a deleted medication must not be planned") }

    @Test
    fun `a deleted medication is not archived`() = runTest {
        medications.put(deletedMedication())

        val outcome = ArchiveMedicationUseCase(medications, planner)(Fixtures.MEDICATION_ID, ArchiveReason.MANUAL)

        assertEquals(ArchiveOutcome.Deleted, outcome)
        assertTrue(!requireNotNull(medications.get(Fixtures.MEDICATION_ID)).isArchived)
    }

    @Test
    fun `a deleted medication is not restored from the archive`() = runTest {
        medications.put(deletedMedication())

        val outcome = RestoreMedicationUseCase(
            medications = medications,
            planner = planner,
            transactor = DirectTransactor(),
            time = clock,
            log = RecordingOperationLog(),
        )(Fixtures.MEDICATION_ID)

        assertEquals(RestoreOutcome.Deleted, outcome)
    }

    @Test
    fun `a deleted medication is not mirrored to the watch`() = runTest {
        medications.put(deletedMedication())

        val outcome = ToggleWatchMirrorUseCase(medications, RecordingOperationLog())(
            Fixtures.MEDICATION_ID,
            enabled = false,
        )

        assertEquals(WatchMirrorOutcome.Deleted, outcome)
    }

    @Test
    fun `a deleted medication creates no on demand intake`() = runTest {
        medications.put(Fixtures.prnMedication().copy(deletedAt = Instant.EPOCH))
        val profiles = FakeProfileRepository().apply { put(Fixtures.profile()) }
        val createPrn = CreatePrnIntakeUseCase(intakes, medications, profiles, clock)

        val outcome = createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)

        assertEquals(PrnOutcome.Deleted, outcome)
        assertTrue(intakes.stored.isEmpty(), "a deleted medication must not produce intakes (PR-14)")
    }

    @Test
    fun `saving an edit does not resurrect a deleted medication`() = runTest {
        medications.put(deletedMedication())
        val draft = MedicationDraft.of(deletedMedication()).copy(name = "renamed")
        val save = SaveMedicationUseCase(
            validate = ValidateMedicationDraft(),
            medications = medications,
            planner = planner,
            transactor = DirectTransactor(),
            log = RecordingOperationLog(),
        )

        val result = save(draft)

        assertTrue(result is SaveResult.Saved, "the draft must be saved: $result")
        assertTrue(
            requireNotNull(medications.get(Fixtures.MEDICATION_ID)).isDeleted,
            "the form must not bring a deleted record back",
        )
    }
}
