package ru.stohastika.sklyanka.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.log.RoomOperationLog
import ru.stohastika.sklyanka.data.room.DatabaseTestRule
import ru.stohastika.sklyanka.data.room.SklyankaConverters
import ru.stohastika.sklyanka.data.room.SklyankaTransactor
import ru.stohastika.sklyanka.data.room.TestData
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.schedule.IntakeGenerationPolicy
import ru.stohastika.sklyanka.domain.schedule.IntakePlanDiff
import ru.stohastika.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.schedule.PlanScope
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Применение плана генерации на настоящей базе (SKL-06).
 *
 * Правило генерации проверено на JVM в `core-domain`; здесь проверяется то, чего в памяти
 * не проверить: транзакция, уникальность приёма по расписанию (INV-13) и поведение двух
 * одновременных генераций.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class IntakePlanningTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(
        TestData.TODAY.atTime(7, 0).atZone(ZoneId.of("Europe/Moscow")).toInstant(),
        ZoneId.of("Europe/Moscow"),
    )

    private val log by lazy { RoomOperationLog(db.opLog, clock) }
    private val transactor by lazy { SklyankaTransactor(db.database) }
    private val intakes by lazy { IntakeRepositoryImpl(db.intakes, db.medications, transactor, clock, log) }
    private val medications by lazy { MedicationRepositoryImpl(db.medications, clock, log) }
    private val profiles by lazy { ProfileRepositoryImpl(db.profiles) }

    private val plan by lazy {
        PlanIntakesUseCase(
            profiles = profiles,
            medications = medications,
            intakes = intakes,
            time = clock,
            log = log,
            policy = IntakeGenerationPolicy(windowDepthDays = DEPTH_DAYS),
        )
    }

    private val morningsOfTheWindow: List<LocalDateTime> =
        (0 until DEPTH_DAYS).map { TestData.TODAY.plusDays(it.toLong()).atTime(8, 0) }

    @Before
    fun fillProfileAndMedication() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())
    }

    @Test
    fun applyingAPlanCreatesTheMissingIntakesOfTheWindow() = runTest {
        val applied = intakes.applyPlan(
            profileId = TestData.OWNER_ID,
            medicationId = TestData.OWNER_MEDICATION_ID,
            diff = IntakePlanDiff(toCreate = morningsOfTheWindow, toDelete = emptyList(), unchanged = 0),
        )

        assertEquals(DEPTH_DAYS, applied.created)
        assertEquals(DEPTH_DAYS, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    /** INV-13: повторная генерация того же приёма упирается в уникальный индекс, а не в дубль. */
    @Test
    fun applyingTheSamePlanTwiceCreatesNothingTheSecondTime() = runTest {
        val diff = IntakePlanDiff(toCreate = morningsOfTheWindow, toDelete = emptyList(), unchanged = 0)
        intakes.applyPlan(TestData.OWNER_ID, TestData.OWNER_MEDICATION_ID, diff)

        val second = intakes.applyPlan(TestData.OWNER_ID, TestData.OWNER_MEDICATION_ID, diff)

        assertEquals(0, second.created)
        assertEquals(DEPTH_DAYS, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    @Test
    fun twoSimultaneousGenerationsLeaveExactlyOneSetOfIntakes() = runTest {
        val diff = IntakePlanDiff(toCreate = morningsOfTheWindow, toDelete = emptyList(), unchanged = 0)

        withContext(Dispatchers.IO) {
            List(2) { async { intakes.applyPlan(TestData.OWNER_ID, TestData.OWNER_MEDICATION_ID, diff) } }.awaitAll()
        }

        assertEquals(DEPTH_DAYS, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    @Test
    fun applyingAPlanRemovesThePendingIntakesThatFellOutOfTheSchedule() = runTest {
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = STRAY_ID, scheduledAt = STRAY_MOMENT)))

        val applied = intakes.applyPlan(
            profileId = TestData.OWNER_ID,
            medicationId = TestData.OWNER_MEDICATION_ID,
            diff = IntakePlanDiff(toCreate = morningsOfTheWindow, toDelete = listOf(STRAY_ID), unchanged = 0),
        )

        assertEquals(1, applied.deleted)
        assertEquals(null, db.intakes.getById(STRAY_ID))
    }

    /**
     * Отметка неприкосновенна (ПР-11): условие `status = 'PENDING'` стоит внутри DELETE,
     * поэтому отметка, пришедшая с телефона между чтением окна и записью, переживает
     * перепланирование.
     */
    @Test
    fun aMarkedIntakeIsNotDeletedEvenWhenThePlanAsksForIt() = runTest {
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = STRAY_ID, scheduledAt = STRAY_MOMENT)))
        intakes.markIfPending(STRAY_ID, IntakeStatus.TAKEN, IntakeSource.PHONE)

        val applied = intakes.applyPlan(
            profileId = TestData.OWNER_ID,
            medicationId = TestData.OWNER_MEDICATION_ID,
            diff = IntakePlanDiff(toCreate = emptyList(), toDelete = listOf(STRAY_ID), unchanged = 0),
        )

        assertEquals(0, applied.deleted)
        assertNotNull(db.intakes.getById(STRAY_ID))
    }

    @Test
    fun theWindowQueryReturnsOnlyTheScheduledIntakesOfItsProfile() = runTest {
        db.profiles.insert(TestData.relative())
        db.medications.upsert(
            TestData.medication(id = TestData.RELATIVE_MEDICATION_ID, profileId = TestData.RELATIVE_ID),
        )
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(id = OWNER_INTAKE_ID),
                TestData.scheduledIntake(
                    id = RELATIVE_INTAKE_ID,
                    medicationId = TestData.RELATIVE_MEDICATION_ID,
                ),
            ),
        )

        val window = intakes.scheduledBetween(
            profileId = TestData.OWNER_ID,
            medicationId = TestData.OWNER_MEDICATION_ID,
            from = TestData.TODAY.atStartOfDay(),
            toExclusive = TestData.TODAY.plusDays(DEPTH_DAYS.toLong()).atStartOfDay(),
        )

        assertEquals(listOf(OWNER_INTAKE_ID), window.map { it.id })
    }

    /** INV-14: приём по потребности слота не занимает и плану не подчиняется. */
    @Test
    fun theWindowQueryIgnoresIntakesTakenOnDemand() = runTest {
        db.medications.upsert(
            TestData.medication(
                id = TestData.PRN_MEDICATION_ID,
                scheduleMode = ScheduleMode.PRN,
            ),
        )
        db.intakes.insertAll(listOf(TestData.prnIntake(id = PRN_INTAKE_ID)))

        val window = intakes.scheduledBetween(
            profileId = TestData.OWNER_ID,
            medicationId = TestData.PRN_MEDICATION_ID,
            from = TestData.TODAY.atStartOfDay(),
            toExclusive = TestData.TODAY.plusDays(DEPTH_DAYS.toLong()).atStartOfDay(),
        )

        assertTrue(window.isEmpty())
    }

    // --- Домен поверх настоящих репозиториев ---------------------------------------------

    @Test
    fun aFullPassFillsTheWindowAndTheSecondOneChangesNothing() = runTest {
        val first = plan(PlanScope.AllProfiles, PlanReason.APP_START)
        val second = plan(PlanScope.AllProfiles, PlanReason.ANCHOR_ALARM)

        assertEquals(DEPTH_DAYS, first.created)
        assertTrue(second.changedNothing)
        assertEquals(DEPTH_DAYS, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    @Test
    fun everyGeneratedIntakeCarriesTheScheduleSlotAndThePendingStatus() = runTest {
        plan(PlanScope.Medication(TestData.OWNER_MEDICATION_ID), PlanReason.MEDICATION_SAVED)

        val stored = db.intakes.selectScheduledBetween(
            profileId = TestData.OWNER_ID,
            medicationId = TestData.OWNER_MEDICATION_ID,
            from = SklyankaConverters.dateTimeToText(TestData.TODAY.atStartOfDay()),
            to = SklyankaConverters.dateTimeToText(TestData.TODAY.plusDays(DEPTH_DAYS.toLong()).atStartOfDay()),
        )

        assertEquals(DEPTH_DAYS, stored.size)
        assertTrue(stored.all { it.status == IntakeStatus.PENDING.name })
        assertTrue(stored.all { it.scheduleSlot != null })
    }

    private companion object {
        const val DEPTH_DAYS = 7
        const val STRAY_ID = "stray-intake"
        const val OWNER_INTAKE_ID = "owner-intake"
        const val RELATIVE_INTAKE_ID = "relative-intake"
        const val PRN_INTAKE_ID = "prn-intake"

        /** Время, которого в расписании нет: его и снимает перепланирование. */
        val STRAY_MOMENT: LocalDateTime = TestData.TODAY.plusDays(1).atTime(21, 0)
    }
}
