package ru.stohastika.sklyanka.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
import ru.stohastika.sklyanka.domain.intake.MarkCommand
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.UndoCommand
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.ZoneId

/**
 * Отмена отметки: состояние и остаток меняются вместе, повторная отмена молчит,
 * а след остаётся в журнале (SKL-30).
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется, как и соседние инструментальные тесты.
 */
@RunWith(AndroidJUnit4::class)
class IntakeRepositoryUndoTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(MOMENT, ZoneId.of("Europe/Moscow"))

    private val log by lazy { RoomOperationLog(db.opLog, clock) }
    private val transactor by lazy { SklyankaTransactor(db.database) }
    private val intakes by lazy {
        IntakeRepositoryImpl(db.intakes, db.medications, transactor, clock, log)
    }

    @Before
    fun fillProfileAndMedication() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(id = INTAKE_ID),
                TestData.scheduledIntake(id = SECOND_INTAKE_ID, scheduledAt = TestData.LATE_EVENING),
            ),
        )
    }

    @Test
    fun theUndoReturnsTheIntakeToPendingAndRestoresTheDoseOnce() = runTest {
        mark(INTAKE_ID)

        val outcome = intakes.undoAtomically(command(INTAKE_ID))

        assertEquals(UndoOutcome.Applied(undone = 1), outcome)
        val stored = db.intakes.getById(INTAKE_ID)
        assertEquals(IntakeStatus.PENDING.name, stored?.status)
        assertNull(stored?.actedAt)
        assertNull(stored?.source)
        assertNull(stored?.actorProfileId)
        assertEquals("30", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun theSecondUndoOfTheSameMarkReturnsNothing() = runTest {
        mark(INTAKE_ID)
        intakes.undoAtomically(command(INTAKE_ID))

        val second = intakes.undoAtomically(command(INTAKE_ID))

        assertEquals(UndoOutcome.Applied(undone = 0), second)
        assertEquals("30", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
        assertEquals(IntakeStatus.PENDING.name, db.intakes.getById(INTAKE_ID)?.status)
    }

    @Test
    fun aMissingIntakeLeavesTheWholeActionUntouched() = runTest {
        mark(INTAKE_ID)
        mark(SECOND_INTAKE_ID)

        val outcome = intakes.undoAtomically(command(INTAKE_ID, MISSING_INTAKE_ID))

        assertEquals(UndoOutcome.NotFound, outcome)
        assertEquals(IntakeStatus.TAKEN.name, db.intakes.getById(INTAKE_ID)?.status)
        assertEquals(IntakeStatus.TAKEN.name, db.intakes.getById(SECOND_INTAKE_ID)?.status)
    }

    @Test
    fun theWholeGroupOfOneActionIsUndoneTogether() = runTest {
        mark(INTAKE_ID)
        mark(SECOND_INTAKE_ID)

        val outcome = intakes.undoAtomically(command(INTAKE_ID, SECOND_INTAKE_ID))

        assertEquals(UndoOutcome.Applied(undone = 2), outcome)
        assertEquals(IntakeStatus.PENDING.name, db.intakes.getById(INTAKE_ID)?.status)
        assertEquals(IntakeStatus.PENDING.name, db.intakes.getById(SECOND_INTAKE_ID)?.status)
        assertEquals("30", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun aClampedDecrementIsRestoredAsTheFullDoseAndBothStepsStayInTheLog() = runTest {
        db.medications.setStock(TestData.OWNER_MEDICATION_ID, "0.5")
        mark(INTAKE_ID)
        assertEquals("0", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)

        intakes.undoAtomically(command(INTAKE_ID))

        // Возвращается ровно доза одного приёма (ПР-5): счётчик может превысить упаковку,
        // и пара записей журнала — тот след, по которому это разбирается (TDD.md §3.5).
        assertEquals("1.5", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
        val records = log.recent(RECENT_LIMIT)
        assertTrue(records.any { it.event == OpEvent.STOCK_DECREMENTED && it.detail == "clamped_at_zero" })
        assertTrue(records.any { it.event == OpEvent.STOCK_RESTORED })
    }

    @Test
    fun aSkippedMarkComesBackToPendingWithoutTouchingTheStock() = runTest {
        mark(INTAKE_ID, MarkResult.SKIPPED)

        intakes.undoAtomically(command(INTAKE_ID))

        assertEquals(IntakeStatus.PENDING.name, db.intakes.getById(INTAKE_ID)?.status)
        assertEquals("30", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun theLogNamesTheUndoWithIdentifiersAndNoMedicalData() = runTest {
        mark(INTAKE_ID)

        intakes.undoAtomically(command(INTAKE_ID))

        val record = log.recent(RECENT_LIMIT).last { it.event == OpEvent.INTAKE_MARK_UNDONE }
        assertEquals(INTAKE_ID, record.intakeId)
        assertEquals(TestData.OWNER_MEDICATION_ID, record.medicationId)
        assertEquals(IntakeStatus.TAKEN.name, record.before)
        assertEquals(IntakeStatus.PENDING.name, record.after)
        assertEquals(IntakeSource.PHONE.name, record.source)
        val written = listOfNotNull(
            record.profileId,
            record.medicationId,
            record.intakeId,
            record.before,
            record.after,
            record.source,
            record.detail,
        )
        assertTrue("the log must not carry medication names", written.none { it.contains("med-a") })
        assertTrue("the log must not carry profile names", written.none { it.contains("owner") })
        assertTrue("the log must not carry units", written.none { it.contains("unit") })
    }

    private suspend fun mark(intakeId: String, result: MarkResult = MarkResult.TAKEN) {
        intakes.markAtomically(
            MarkCommand(
                intakeId = intakeId,
                result = result,
                source = IntakeSource.PHONE,
                actedAt = MOMENT,
                actorProfileId = null,
            ),
        )
    }

    private fun command(vararg intakeIds: String) = UndoCommand(
        intakeIds = intakeIds.toList(),
        source = IntakeSource.PHONE,
        actorProfileId = null,
    )

    private companion object {
        const val INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000001"
        const val SECOND_INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000002"
        const val MISSING_INTAKE_ID = "aaaaaaaa-0000-4000-8000-0000000000ff"
        const val RECENT_LIMIT = 50
        val MOMENT: Instant = Instant.parse("2026-09-25T05:05:00Z")
    }
}
