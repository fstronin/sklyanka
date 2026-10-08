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
import ru.stohastika.sklyanka.data.assertRefused
import ru.stohastika.sklyanka.data.log.RoomOperationLog
import ru.stohastika.sklyanka.data.room.DatabaseTestRule
import ru.stohastika.sklyanka.data.room.SklyankaTransactor
import ru.stohastika.sklyanka.data.room.TestData
import ru.stohastika.sklyanka.domain.intake.MarkCommand
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.PrnCommand
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.intake.UndoCommand
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.InvariantViolationException
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.schedule.IntakePlanDiff
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Инварианты приёма на путях записи настоящей базы (SKL-107, ИНВ-10, ИНВ-11, ИНВ-14,
 * ИНВ-15, ИНВ-18).
 *
 * Правила как таковые проверены модульными тестами домена; здесь проверяется граница:
 * вызывается ли проверка продуктивным путём, отказывает ли она до записи и сохраняет ли
 * транзакция исходное состояние. Ровно так же здесь закреплено, чего инварианты не
 * делают: отметка живущего приёма законна и после правки курса, и после смены режима,
 * а «принять» по потребности фиксирует факт даже за границей курса — история человека
 * важнее формата (обоснование — `docs/v2/10-behavior.md` §1.4, SKL-107/TDD.md §2).
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class IntakeIntegrityTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(MARK_AT, ZoneId.of("Europe/Moscow"))

    private val log by lazy { RoomOperationLog(db.opLog, clock) }
    private val transactor by lazy { SklyankaTransactor(db.database) }
    private val intakes by lazy { IntakeRepositoryImpl(db.intakes, db.medications, transactor, clock, log) }

    @Before
    fun fillProfileAndMedications() = runTest {
        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        db.medications.upsert(TestData.medication())
        db.medications.upsert(TestData.medication(id = TestData.PRN_MEDICATION_ID, scheduleMode = ScheduleMode.PRN))
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = INTAKE_ID)))
    }

    // --- Отметка: ИНВ-10, ИНВ-11, ИНВ-18 на writeMark ------------------------------------------

    @Test
    fun aMarkSignedByTheOwnerOfTheMedicationIsRefusedAndTheIntakeStaysPending() = runTest {
        assertRefused<InvariantViolationException> {
            intakes.markAtomically(
                MarkCommand(
                    intakeId = INTAKE_ID,
                    result = MarkResult.TAKEN,
                    source = IntakeSource.PHONE,
                    actedAt = MARK_AT,
                    actorProfileId = TestData.OWNER_ID,
                ),
            )
        }

        val stored = db.intakes.getById(INTAKE_ID)
        assertEquals(IntakeStatus.PENDING.name, stored?.status)
        assertNull("nothing of the refused mark may remain", stored?.actedAt)
        assertEquals(
            "the refused write keeps the stock",
            "30",
            db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft,
        )
    }

    @Test
    fun aMarkByThePhoneOwnerOnARelativeCourseIsWrittenAndUndoClearsEveryField() = runTest {
        // Достижимая форма: актора даёт ActorResolver — всегда профиль владельца телефона,
        // отметившего чужой приём (ПР-12).
        db.medications.upsert(
            TestData.medication(id = TestData.RELATIVE_MEDICATION_ID, profileId = TestData.RELATIVE_ID),
        )
        db.intakes.insertAll(
            listOf(TestData.scheduledIntake(id = RELATIVE_INTAKE_ID, medicationId = TestData.RELATIVE_MEDICATION_ID)),
        )
        intakes.markAtomically(
            MarkCommand(
                intakeId = RELATIVE_INTAKE_ID,
                result = MarkResult.TAKEN,
                source = IntakeSource.PHONE,
                actedAt = MARK_AT,
                actorProfileId = TestData.OWNER_ID,
            ),
        )
        intakes.undoAtomically(
            UndoCommand(intakeIds = listOf(RELATIVE_INTAKE_ID), source = IntakeSource.PHONE, actorProfileId = null),
        )

        val stored = db.intakes.getById(RELATIVE_INTAKE_ID)
        assertEquals(IntakeStatus.PENDING.name, stored?.status)
        assertNull("undo must clear the marking moment (INV-11)", stored?.actedAt)
        assertNull("undo must clear the source (INV-11)", stored?.source)
        assertNull("undo must clear the actor signature (INV-11)", stored?.actorProfileId)
    }

    @Test
    fun aLeftoverRowOfARenamedModeIsStillMarkable() = runTest {
        // Расписание поменяли на «по потребности» после рождения приёма: назначенное
        // время отметка не переписывает, и ИНВ-14 эту строку не судит (SKL-107).
        db.medications.upsert(TestData.medication(scheduleMode = ScheduleMode.PRN))

        val outcome = intakes.markAtomically(
            MarkCommand(
                intakeId = INTAKE_ID,
                result = MarkResult.TAKEN,
                source = IntakeSource.PHONE,
                actedAt = MARK_AT,
                actorProfileId = null,
            ),
        )

        assertTrue("the human mark of a living intake must take effect: $outcome", outcome is MarkOutcome.Applied)
    }

    @Test
    fun markingALivingIntakeAfterTheCourseDatesMovedCloserIsStillApplied() = runTest {
        // Даты курса подвинули после рождения приёма: ИНВ-15 судит рождение, не факт
        // (`docs/v2/10-behavior.md` §1.4) — отметка законна и вне нового периода.
        db.medications.upsert(TestData.medication(endDate = CLOSED_COURSE))

        val outcome = intakes.markAtomically(
            MarkCommand(
                intakeId = INTAKE_ID,
                result = MarkResult.TAKEN,
                source = IntakeSource.PHONE,
                actedAt = MARK_AT,
                actorProfileId = null,
            ),
        )

        assertTrue("a moved course must not block the mark: $outcome", outcome is MarkOutcome.Applied)
    }

    // --- «По потребности»: ИНВ-14 на рождении ----------------------------------------------------

    @Test
    fun aPrnIntakeIsBornWithItsScheduledMinuteEqualtoTheMomentOfMarking() = runTest {
        val actedAt = Instant.parse("2026-09-25T05:00:00Z")

        val outcome = intakes.createPrnAtomically(
            PrnCommand(
                medicationId = TestData.PRN_MEDICATION_ID,
                actedAt = actedAt,
                scheduledAt = LocalDateTime.of(2026, 9, 25, 8, 0),
                source = IntakeSource.PHONE,
                actorProfileId = null,
            ),
        )

        assertTrue("the on-demand intake must be created: $outcome", outcome is PrnOutcome.Created)
    }

    @Test
    fun aPrnRowWithAMismatchedScheduledMinuteIsRefused() = runTest {
        assertRefused<InvariantViolationException> {
            intakes.createPrnAtomically(
                PrnCommand(
                    medicationId = TestData.PRN_MEDICATION_ID,
                    actedAt = MARK_AT,
                    scheduledAt = LocalDateTime.of(2026, 9, 25, 8, 0),
                    source = IntakeSource.PHONE,
                    actorProfileId = null,
                ),
            )
        }

        assertEquals(1, db.database.transferDao().countIntakes())
        assertEquals(
            "the refused write keeps the stock",
            "30",
            db.medications.getById(TestData.PRN_MEDICATION_ID)?.stockLeft,
        )
    }

    @Test
    fun aPrnMarkAfterTheCourseEndStillRecordsTheFact() = runTest {
        // Курс «по потребности» просрочен, а кнопка жива до архивации: отказа быть не
        // должно — приложение обязано фиксировать реальный приём (ИНВ-15 здесь не судья).
        db.medications.upsert(
            TestData.medication(
                id = TestData.PRN_MEDICATION_ID,
                scheduleMode = ScheduleMode.PRN,
                endDate = CLOSED_COURSE,
            ),
        )

        val outcome = intakes.createPrnAtomically(
            PrnCommand(
                medicationId = TestData.PRN_MEDICATION_ID,
                actedAt = MARK_AT,
                scheduledAt = LocalDateTime.of(2026, 9, 25, 8, 5),
                source = IntakeSource.PHONE,
                actorProfileId = null,
            ),
        )

        assertTrue("a real dose must be recordable: $outcome", outcome is PrnOutcome.Created)
    }

    // --- Генерация плана: ИНВ-15 на границе рождения --------------------------------------------

    @Test
    fun aPlanInsideTheCourseCreatesItsRows() = runTest {
        db.medications.upsert(TestData.medication(endDate = ENDED_COURSE))

        val applied = intakes.applyPlan(
            profileId = TestData.OWNER_ID,
            medicationId = TestData.OWNER_MEDICATION_ID,
            diff = IntakePlanDiff(
                toCreate = listOf(LocalDateTime.of(2026, 9, 26, 20, 0)),
                toDelete = emptyList(),
                unchanged = 0,
            ),
        )

        assertEquals(1, applied.created)
    }

    @Test
    fun aPlanMomentOutsideTheCourseIsRefusedAndNothingIsInserted() = runTest {
        db.medications.upsert(TestData.medication(endDate = ENDED_COURSE))

        assertRefused<InvariantViolationException> {
            intakes.applyPlan(
                profileId = TestData.OWNER_ID,
                medicationId = TestData.OWNER_MEDICATION_ID,
                diff = IntakePlanDiff(
                    toCreate = listOf(LocalDateTime.of(2026, 10, 5, 8, 0)),
                    toDelete = emptyList(),
                    unchanged = 0,
                ),
            )
        }

        assertEquals(1, db.database.transferDao().countIntakes())
    }
    // --- Вставка произвольной модели: ИНВ-10, ИНВ-14 на границе ----------------------------------

    @Test
    fun anExpectedRowOfAnOnDemandMedicationIsRefusedAtTheStorageBoundary() = runTest {
        val pendingPrn = Intake(
            id = "pending-prn",
            medicationId = TestData.PRN_MEDICATION_ID,
            scheduledAt = TestData.MORNING,
            status = IntakeStatus.PENDING,
            actedAt = null,
            source = null,
            actorProfileId = null,
        )

        assertRefused<InvariantViolationException> { intakes.insertMissing(listOf(pendingPrn)) }

        assertEquals(1, db.database.transferDao().countIntakes())
    }

    @Test
    fun aPendingRowCarryingMarkingFieldsIsRefusedAtTheStorageBoundary() = runTest {
        val poisoned = Intake(
            id = "poisoned",
            medicationId = TestData.OWNER_MEDICATION_ID,
            scheduledAt = LocalDateTime.of(2026, 9, 25, 20, 0),
            status = IntakeStatus.PENDING,
            actedAt = MARK_AT,
            source = IntakeSource.PHONE,
            actorProfileId = null,
        )

        assertRefused<InvariantViolationException> { intakes.insertMissing(listOf(poisoned)) }

        assertEquals(1, db.database.transferDao().countIntakes())
    }

    private companion object {
        val MARK_AT: Instant = Instant.parse("2026-09-25T05:05:00Z")
        const val INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000001"
        const val ENDED_COURSE = "2026-09-26"
        const val CLOSED_COURSE = "2026-09-10"
        const val RELATIVE_INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000002"
    }
}
