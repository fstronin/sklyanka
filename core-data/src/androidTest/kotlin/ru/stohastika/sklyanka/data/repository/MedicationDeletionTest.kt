package ru.stohastika.sklyanka.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.log.RoomOperationLog
import ru.stohastika.sklyanka.data.room.DatabaseTestRule
import ru.stohastika.sklyanka.data.room.SklyankaTransactor
import ru.stohastika.sklyanka.data.room.TestData
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ArchiveOutcome
import ru.stohastika.sklyanka.domain.medication.DeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.DeleteOutcome
import ru.stohastika.sklyanka.domain.medication.UndoDeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.UndoDeleteOutcome
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.schedule.GenerationWindow
import ru.stohastika.sklyanka.domain.schedule.MedicationPlan
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.schedule.PlanResult
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.ZoneId

/**
 * Мягкое удаление лекарства на настоящей базе (SKL-99, ПМ-24, PR-14, ИНВ-21/22).
 *
 * Здесь проверяется то, чего не видно в домене: что условное обновление действительно
 * не трогает запись с отметкой `TAKEN` даже при гонке, что поля архива очищаются одной
 * записью, что строки приёмов остаются и что экранные выборки удалённое не показывают.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class MedicationDeletionTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(MOMENT, ZoneId.of("Europe/Moscow"))

    private val transactor by lazy { SklyankaTransactor(db.database) }
    private val log by lazy { RoomOperationLog(db.opLog, clock) }
    private val medications by lazy { MedicationRepositoryImpl(db.medications, clock, log) }
    private val intakes by lazy { IntakeRepositoryImpl(db.intakes, db.medications, transactor, clock, log) }
    private val planner = NoPlan()

    private val delete by lazy { DeleteMedicationUseCase(medications, intakes, planner) }
    private val undoDelete by lazy { UndoDeleteMedicationUseCase(medications, planner) }

    @Before
    fun fillProfilesAndMedications() = runTest {
        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        db.medications.upsert(TestData.medication())
    }

    @Test
    fun deletingIsSoftAndClearsTheArchiveFields() = runTest {
        db.medications.upsert(
            TestData.medication().copy(archivedAt = MOMENT.toEpochMilli(), archiveReason = ArchiveReason.MANUAL.name),
        )

        val outcome = delete(TestData.OWNER_MEDICATION_ID)

        assertEquals(DeleteOutcome.Deleted(revokedIntakes = 0), outcome)
        val row = db.medications.getById(TestData.OWNER_MEDICATION_ID)
        assertNotNull("the deletion moment must be written", row?.deletedAt)
        assertNull("INV-21: the archive fields must be cleared together", row?.archivedAt)
        assertNull(row?.archiveReason)
        assertTrue(medications.observeActive(TestData.OWNER_ID).first().isEmpty())
        assertTrue(medications.observeArchived(TestData.OWNER_ID).first().isEmpty())
    }

    @Test
    fun aTakenIntakeBlocksTheDeletionEvenInsideTheUpdate() = runTest {
        // Гонка: отметка уже лежит в базе — условное обновление обязано вернуть 0 строк,
        // и удаление не скрывает факт приёма (ИНВ-22).
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(
                    id = TAKEN_ID,
                    status = IntakeStatus.TAKEN,
                    actedAt = MOMENT.toEpochMilli(),
                    source = "PHONE",
                ),
            ),
        )

        val outcome = delete(TestData.OWNER_MEDICATION_ID)

        assertEquals(DeleteOutcome.HasTakenIntakes, outcome)
        assertNull("the record must stay visible", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.deletedAt)
        assertEquals(1, medications.observeActive(TestData.OWNER_ID).first().size)
    }

    @Test
    fun undoClearsTheDeletionAndReturnsTheRecord() = runTest {
        assertTrue(medications.delete(TestData.OWNER_MEDICATION_ID))
        assertTrue(medications.observeActive(TestData.OWNER_ID).first().isEmpty())

        val outcome = undoDelete(TestData.OWNER_MEDICATION_ID)

        assertTrue(outcome is UndoDeleteOutcome.Undone)
        assertNull(db.medications.getById(TestData.OWNER_MEDICATION_ID)?.deletedAt)
        assertEquals(1, medications.observeActive(TestData.OWNER_ID).first().size)
    }

    @Test
    fun theIntakeRowsSurviveTheDeletion() = runTest {
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(id = MISSED_ID, status = IntakeStatus.MISSED, actedAt = MOMENT.toEpochMilli()),
            ),
        )

        delete(TestData.OWNER_MEDICATION_ID)

        val stored = db.intakes.getById(MISSED_ID)
        assertNotNull("PR-14: the intake row must stay", stored)
        assertEquals(IntakeStatus.MISSED.name, stored?.status)
    }

    @Test
    fun theDeletedRecordLeavesTheScreenQueries() = runTest {
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(id = TODAY_ID),
                TestData.scheduledIntake(id = OTHER_DAY_ID, scheduledAt = TestData.MORNING.minusDays(2)),
            ),
        )
        val history = HistoryRepositoryImpl(db.intakes, db.database.historyDao(), log)

        delete(TestData.OWNER_MEDICATION_ID)

        assertTrue(intakes.observeDay(TestData.OWNER_ID, TestData.TODAY).first().isEmpty())
        assertTrue(
            intakes.observePeriod(TestData.OWNER_ID, TestData.TODAY.minusDays(7), TestData.TODAY).first().isEmpty(),
        )
        val slice = history.observe(
            TestData.OWNER_ID,
            HistoryWindow(TestData.TODAY.minusDays(7), TestData.TODAY),
        ).first()
        assertTrue("PR-14: history excludes the deleted record", slice.medications.isEmpty())
        assertTrue(slice.intakes.isEmpty())
        assertTrue(slice.historyStartsAt == null)
    }

    @Test
    fun aDeletedMedicationCreatesNoOnDemandIntake() = runTest {
        db.medications.upsert(
            TestData.medication(
                id = TestData.PRN_MEDICATION_ID,
                name = "med-prn",
                scheduleMode = ScheduleMode.PRN,
            ).copy(deletedAt = MOMENT.toEpochMilli()),
        )
        val createPrn = CreatePrnIntakeUseCase(intakes, medications, ProfileRepositoryImpl(db.profiles), clock)

        val outcome = createPrn(TestData.PRN_MEDICATION_ID, IntakeSource.PHONE)

        assertEquals(PrnOutcome.Deleted, outcome)
        assertTrue(
            intakes.scheduledBetween(
                profileId = TestData.OWNER_ID,
                medicationId = TestData.PRN_MEDICATION_ID,
                from = TestData.TODAY.atStartOfDay(),
                toExclusive = TestData.TODAY.plusDays(1).atStartOfDay(),
            ).isEmpty(),
        )
    }

    @Test
    fun archivingADeletedMedicationDoesNothing() = runTest {
        delete(TestData.OWNER_MEDICATION_ID)
        val archive = ArchiveMedicationUseCase(medications, planner)

        val outcome = archive(TestData.OWNER_MEDICATION_ID, ArchiveReason.MANUAL)

        assertEquals(ArchiveOutcome.Deleted, outcome)
        assertNull(db.medications.getById(TestData.OWNER_MEDICATION_ID)?.archivedAt)
    }

    @Test
    fun theJournalKeepsTheDeletionAndTheUndoAsFacts() = runTest {
        delete(TestData.OWNER_MEDICATION_ID)
        undoDelete(TestData.OWNER_MEDICATION_ID)

        assertTrue(log.recent(limit = 10).any { it.event == OpEvent.MEDICATION_DELETED })
        assertTrue(log.recent(limit = 10).any { it.event == OpEvent.MEDICATION_DELETED_UNDONE })
    }

    /** Перепланирование здесь только фиксируется: его правила проверены в домене. */
    private class NoPlan : MedicationPlanner {
        val calls: MutableList<Pair<String, PlanReason>> = mutableListOf()

        override suspend fun plan(medicationId: String, reason: PlanReason): PlanResult {
            calls += medicationId to reason
            return PlanResult(
                window = GenerationWindow.of(TestData.TODAY, GenerationWindow.MIN_DEPTH_DAYS),
                reason = reason,
                medications = listOf(
                    MedicationPlan(
                        profileId = TestData.OWNER_ID,
                        medicationId = medicationId,
                        created = 0,
                        deleted = 0,
                        unchanged = 0,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAKEN_ID = "66666666-6666-4666-8666-666666666666"
        const val MISSED_ID = "77777777-7777-4777-8777-777777777777"
        const val TODAY_ID = "88888888-8888-4888-8888-888888888888"
        const val OTHER_DAY_ID = "99999999-9999-4999-8999-999999999999"
        val MOMENT: Instant = Instant.parse("2026-09-25T07:00:00Z")
    }
}
