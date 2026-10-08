package ru.stohastika.sklyanka.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.log.RoomOperationLog
import ru.stohastika.sklyanka.data.room.DatabaseTestRule
import ru.stohastika.sklyanka.data.room.SklyankaTransactor
import ru.stohastika.sklyanka.data.room.TestData
import ru.stohastika.sklyanka.domain.intake.EndOfDayIntakeWindow
import ru.stohastika.sklyanka.domain.intake.MarkCommand
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.PrnCommand
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Жизненный цикл приёма на настоящей базе (SKL-07).
 *
 * Здесь проверяется не правило — оно проверено на JVM в `core-domain`, — а его
 * неразрывность: проверка статуса, запись отметки и приведение остатка обязаны быть
 * одной транзакцией (ЧАС-24, НФТ-10). Ошибка здесь проявляется только под нагрузкой
 * и стоит двойного списания остатка.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class IntakeLifecycleTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(MOMENT, ZoneId.of("Europe/Moscow"))

    private val log by lazy { RoomOperationLog(db.opLog, clock) }
    private val transactor by lazy { SklyankaTransactor(db.database) }
    private val intakes by lazy { IntakeRepositoryImpl(db.intakes, db.medications, transactor, clock, log) }
    private val window = EndOfDayIntakeWindow()

    @Before
    fun fillProfileAndMedication() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = INTAKE_ID)))
    }

    // --- Отметка ---------------------------------------------------------------------

    @Test
    fun theFirstMarkIsAppliedAndTheStockGoesDownOnce() = runTest {
        val outcome = intakes.markAtomically(command(MarkResult.TAKEN, at("08:05")))

        assertTrue(outcome is MarkOutcome.Applied)
        assertEquals(IntakeStatus.TAKEN.name, db.intakes.getById(INTAKE_ID)?.status)
        assertEquals("29", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun aLaterMarkOfTheSameIntakeIsIgnoredAndDecrementsNothing() = runTest {
        intakes.markAtomically(command(MarkResult.TAKEN, at("08:05")))

        val outcome = intakes.markAtomically(command(MarkResult.SKIPPED, at("08:10"), IntakeSource.PHONE))

        assertTrue(outcome is MarkOutcome.Ignored)
        assertEquals(IntakeStatus.TAKEN.name, db.intakes.getById(INTAKE_ID)?.status)
        assertEquals(at("08:05").toEpochMilli(), db.intakes.getById(INTAKE_ID)?.actedAt)
        assertEquals("29", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun anEarlierSkipTakesTheDoseBackExactlyOnce() = runTest {
        intakes.markAtomically(command(MarkResult.TAKEN, at("08:05")))

        intakes.markAtomically(command(MarkResult.SKIPPED, at("08:02"), IntakeSource.PHONE))

        assertEquals(IntakeStatus.SKIPPED.name, db.intakes.getById(INTAKE_ID)?.status)
        assertEquals("30", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun simultaneousMarksFromTwoSourcesLeaveOneRecordAndOneDecrement() = runTest {
        val outcomes = withContext(Dispatchers.IO) {
            listOf(IntakeSource.PHONE, IntakeSource.AUTO)
                .map { source -> async { intakes.markAtomically(command(MarkResult.TAKEN, at("08:05"), source)) } }
                .awaitAll()
        }

        assertEquals(1, outcomes.count { it is MarkOutcome.Applied })
        assertEquals(1, outcomes.count { it is MarkOutcome.Ignored })
        assertEquals("29", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
        assertEquals(1, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    @Test
    fun markingAnIntakeThatIsGoneReportsItInsteadOfCreatingOne() = runTest {
        val outcome = intakes.markAtomically(
            MarkCommand(
                intakeId = "missing",
                result = MarkResult.TAKEN,
                source = IntakeSource.PHONE,
                actedAt = at("08:05"),
                actorProfileId = null,
            ),
        )

        assertEquals(MarkOutcome.NotFound, outcome)
        assertEquals(1, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    // --- Автоматический пропуск -------------------------------------------------------

    @Test
    fun theSweepMovesOnlyIntakesWhoseWindowHasExpired() = runTest {
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(id = TOMORROW_INTAKE_ID, scheduledAt = TestData.MORNING.plusDays(1)),
            ),
        )
        val bound = window.candidateBound(LocalDateTime.of(2026, 9, 26, 0, 0))

        val expired = intakes.expiredPending(bound)

        assertEquals(listOf(INTAKE_ID), expired.map { it.id })
    }

    @Test
    fun theSecondSweepChangesNothing() = runTest {
        val midnight = Instant.parse("2026-09-25T21:00:00Z")

        assertEquals(1, intakes.markMissed(listOf(INTAKE_ID), midnight))
        val afterFirst = db.intakes.getById(INTAKE_ID)
        assertEquals(0, intakes.markMissed(listOf(INTAKE_ID), midnight.plusSeconds(60)))

        assertEquals(afterFirst, db.intakes.getById(INTAKE_ID))
        assertEquals(IntakeStatus.MISSED.name, afterFirst?.status)
        assertEquals(IntakeSource.AUTO.name, afterFirst?.source)
        assertEquals("30", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun theSweepDoesNotTouchAnIntakeMarkedByTheHuman() = runTest {
        intakes.markAtomically(command(MarkResult.SKIPPED, at("08:40")))

        assertEquals(0, intakes.markMissed(listOf(INTAKE_ID), Instant.parse("2026-09-25T21:00:00Z")))
        assertEquals(IntakeStatus.SKIPPED.name, db.intakes.getById(INTAKE_ID)?.status)
    }

    // --- Приём по потребности ----------------------------------------------------------

    @Test
    fun anOnDemandIntakeIsBornTakenWithTheScheduledTimeEqualToTheMark() = runTest {
        givenOnDemandMedication(maxPerDay = 3)

        val outcome = intakes.createPrnAtomically(prnCommand(LocalDateTime.of(2026, 9, 25, 13, 20)))

        assertTrue(outcome is PrnOutcome.Created)
        val created = (outcome as PrnOutcome.Created).intake
        assertEquals(IntakeStatus.TAKEN, created.status)
        assertEquals(created.scheduledAt, LocalDateTime.of(2026, 9, 25, 13, 20))
        assertEquals("9", db.medications.getById(TestData.PRN_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun theDailyLimitStopsTheFourthPress() = runTest {
        givenOnDemandMedication(maxPerDay = 3)
        repeat(3) { index -> intakes.createPrnAtomically(prnCommand(LocalDateTime.of(2026, 9, 25, 9 + index, 0))) }

        val outcome = intakes.createPrnAtomically(prnCommand(LocalDateTime.of(2026, 9, 25, 21, 0)))

        assertEquals(PrnOutcome.RejectedDailyLimit(limit = 3, countToday = 3), outcome)
        assertEquals("7", db.medications.getById(TestData.PRN_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun twoSimultaneousPressesDoNotSlipPastTheLimit() = runTest {
        givenOnDemandMedication(maxPerDay = 3)
        repeat(2) { index -> intakes.createPrnAtomically(prnCommand(LocalDateTime.of(2026, 9, 25, 9 + index, 0))) }

        val outcomes = withContext(Dispatchers.IO) {
            List(2) { async { intakes.createPrnAtomically(prnCommand(LocalDateTime.of(2026, 9, 25, 21, 0))) } }
                .awaitAll()
        }

        assertEquals(1, outcomes.count { it is PrnOutcome.Created })
        assertEquals(1, outcomes.count { it is PrnOutcome.RejectedDailyLimit })
    }

    @Test
    fun theLimitResetsWithTheLocalMidnight() = runTest {
        givenOnDemandMedication(maxPerDay = 3)
        repeat(3) { index -> intakes.createPrnAtomically(prnCommand(LocalDateTime.of(2026, 9, 25, 20 + index, 0))) }

        val outcome = intakes.createPrnAtomically(prnCommand(LocalDateTime.of(2026, 9, 26, 0, 5)))

        assertTrue(outcome is PrnOutcome.Created)
    }

    @Test
    fun aScheduledMedicationIsNotTakenOnDemand() = runTest {
        val outcome = intakes.createPrnAtomically(
            PrnCommand(
                medicationId = TestData.OWNER_MEDICATION_ID,
                actedAt = at("13:20"),
                scheduledAt = LocalDateTime.of(2026, 9, 25, 13, 20),
                source = IntakeSource.PHONE,
                actorProfileId = null,
            ),
        )

        assertEquals(PrnOutcome.NotOnDemand, outcome)
    }

    // --- Вспомогательное ----------------------------------------------------------------

    private suspend fun givenOnDemandMedication(maxPerDay: Int) {
        db.medications.upsert(
            TestData.medication(
                id = TestData.PRN_MEDICATION_ID,
                scheduleMode = ScheduleMode.PRN,
                stockLeft = "10",
            ).copy(maxPerDay = maxPerDay),
        )
    }

    private fun command(result: MarkResult, actedAt: Instant, source: IntakeSource = IntakeSource.PHONE) = MarkCommand(
        intakeId = INTAKE_ID,
        result = result,
        source = source,
        actedAt = actedAt,
        actorProfileId = null,
    )

    private fun prnCommand(local: LocalDateTime) = PrnCommand(
        medicationId = TestData.PRN_MEDICATION_ID,
        actedAt = local.atZone(clock.zone()).toInstant(),
        scheduledAt = local,
        source = IntakeSource.PHONE,
        actorProfileId = null,
    )

    /** Местное время 25 сентября 2026 в моменте: отметки сравниваются моментами (ПР-3). */
    private fun at(time: String): Instant = LocalDateTime.parse("2026-09-25T$time").atZone(clock.zone()).toInstant()

    private companion object {
        const val INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000001"
        const val TOMORROW_INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000002"
        val MOMENT: Instant = Instant.parse("2026-09-25T05:05:00Z")
    }
}
