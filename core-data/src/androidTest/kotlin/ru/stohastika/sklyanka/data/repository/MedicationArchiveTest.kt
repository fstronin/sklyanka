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
import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ArchiveOutcome
import ru.stohastika.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.RestoreOutcome
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.schedule.GenerationWindow
import ru.stohastika.sklyanka.domain.schedule.MedicationPlan
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.schedule.PlanResult
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.ZoneId

/**
 * Архивация и возврат на настоящей базе (SKL-10, SKL-11).
 *
 * Правила проверены модульными тестами домена; здесь проверяется то, чего в домене не
 * видно: что пара полей архивации записывается и очищается вместе (ИНВ-9), что архивное
 * лекарство уходит из выборки активных и что история приёмов при этом цела (ПР-11).
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class MedicationArchiveTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(MOMENT, ZoneId.of("Europe/Moscow"))
    private val planner = RecordingPlanner()

    private val transactor by lazy { SklyankaTransactor(db.database) }
    private val log by lazy { RoomOperationLog(db.opLog, clock) }
    private val medications by lazy { MedicationRepositoryImpl(db.medications, clock, log) }
    private val archive by lazy { ArchiveMedicationUseCase(medications, planner) }
    private val restore by lazy {
        RestoreMedicationUseCase(medications, planner, transactor, clock, log)
    }

    @Before
    fun fillProfilesAndMedications() = runTest {
        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        db.medications.upsert(TestData.medication().copy(times = "08:00,20:00"))
        db.medications.upsert(
            TestData.medication(id = TestData.RELATIVE_MEDICATION_ID, profileId = TestData.RELATIVE_ID),
        )
    }

    @Test
    fun archivingWritesBothFieldsAndTakesTheCourseOutOfTheActiveList() = runTest {
        val outcome = archive(TestData.OWNER_MEDICATION_ID, ArchiveReason.MANUAL)

        assertTrue(outcome is ArchiveOutcome.Archived)
        val row = db.medications.getById(TestData.OWNER_MEDICATION_ID)
        assertNotNull("the moment of archiving must be written (INV-9)", row?.archivedAt)
        assertEquals(ArchiveReason.MANUAL.name, row?.archiveReason)
        assertTrue(medications.observeActive(TestData.OWNER_ID).first().isEmpty())
        assertEquals(1, medications.observeArchived(TestData.OWNER_ID).first().size)
    }

    @Test
    fun archivingLeavesEveryMarkedIntakeWhereItWas() = runTest {
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(
                    id = INTAKE_ID,
                    status = IntakeStatus.TAKEN,
                    actedAt = MOMENT.toEpochMilli(),
                    source = "PHONE",
                ),
            ),
        )

        archive(TestData.OWNER_MEDICATION_ID, ArchiveReason.MANUAL)

        val intake = db.intakes.getById(INTAKE_ID)
        assertEquals(IntakeStatus.TAKEN.name, intake?.status)
        assertEquals(MOMENT.toEpochMilli(), intake?.actedAt)
    }

    @Test
    fun restoringClearsBothFieldsTogether() = runTest {
        archive(TestData.OWNER_MEDICATION_ID, ArchiveReason.COURSE_FINISHED)

        val outcome = restore(TestData.OWNER_MEDICATION_ID)

        assertTrue(outcome is RestoreOutcome.Restored)
        val row = db.medications.getById(TestData.OWNER_MEDICATION_ID)
        assertNull("no partially cleared state may exist (INV-9)", row?.archivedAt)
        assertNull(row?.archiveReason)
        assertEquals(1, medications.observeActive(TestData.OWNER_ID).first().size)
    }

    @Test
    fun theArchiveOfOneProfileNeverShowsAnother() = runTest {
        archive(TestData.RELATIVE_MEDICATION_ID, ArchiveReason.MANUAL)

        assertTrue(medications.observeArchived(TestData.OWNER_ID).first().isEmpty())
        assertEquals(1, medications.observeArchived(TestData.RELATIVE_ID).first().size)
    }

    @Test
    fun theWeeklySliceOfOneMedicationStopsAtTheDayBoundary() = runTest {
        val intakes = IntakeRepositoryImpl(db.intakes, db.medications, transactor, clock, log)
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(id = "inside", scheduledAt = TestData.MORNING),
                TestData.scheduledIntake(
                    id = "outside",
                    scheduledAt = TestData.MORNING.minusDays(WEEK_LENGTH.toLong()),
                ),
            ),
        )

        val week = intakes
            .observeByMedication(
                profileId = TestData.OWNER_ID,
                medicationId = TestData.OWNER_MEDICATION_ID,
                from = TestData.TODAY.minusDays((WEEK_LENGTH - 1).toLong()),
                to = TestData.TODAY,
            )
            .first()

        assertEquals(listOf("inside"), week.map { it.id })
    }

    /** Перепланирование в этом тесте только фиксируется: его правила проверены в домене. */
    private class RecordingPlanner : MedicationPlanner {

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
        const val INTAKE_ID = "66666666-6666-4666-8666-666666666666"
        const val WEEK_LENGTH = 7
        val MOMENT: Instant = Instant.parse("2026-09-25T07:00:00Z")
    }
}
