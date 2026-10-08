package ru.stohastika.sklyanka.data.room

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.assertRefused
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode

/**
 * Приёмы: профильный фильтр, границы суток, уникальность по расписанию
 * и идемпотентная отметка.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class IntakeDaoTest {

    @get:Rule
    val db = DatabaseTestRule()

    @Before
    fun fillProfilesAndMedications() = runTest {
        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        db.medications.upsert(TestData.medication())
        db.medications.upsert(
            TestData.medication(id = TestData.RELATIVE_MEDICATION_ID, profileId = TestData.RELATIVE_ID),
        )
        db.medications.upsert(
            TestData.medication(id = TestData.PRN_MEDICATION_ID, scheduleMode = ScheduleMode.PRN, stockLeft = null),
        )
    }

    @Test
    fun theDaySelectionOfOneProfileNeverShowsAnother() = runTest {
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(id = OWN_INTAKE),
                TestData.scheduledIntake(id = FOREIGN_INTAKE, medicationId = TestData.RELATIVE_MEDICATION_ID),
            ),
        )

        val ownDay = db.intakes.observeDay(TestData.OWNER_ID, DAY_START, NEXT_DAY_START).first()

        assertEquals(listOf(OWN_INTAKE), ownDay.map { it.id })
    }

    @Test
    fun anIntakeAtHalfPastElevenStaysInItsOwnDay() = runTest {
        db.intakes.insertAll(
            listOf(TestData.scheduledIntake(id = OWN_INTAKE, scheduledAt = TestData.LATE_EVENING)),
        )

        val day = db.intakes.observeDay(TestData.OWNER_ID, DAY_START, NEXT_DAY_START).first()
        val nextDay = db.intakes.observeDay(TestData.OWNER_ID, NEXT_DAY_START, DAY_AFTER_START).first()

        assertEquals(1, day.size)
        assertTrue(nextDay.isEmpty())
    }

    @Test
    fun repeatedGenerationDoesNotCreateASecondIntakeForTheSameTime() = runTest {
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = OWN_INTAKE)))

        val inserted = db.intakes.insertAll(listOf(TestData.scheduledIntake(id = SECOND_INTAKE)))

        assertEquals(listOf(-1L), inserted)
        assertEquals(1, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    @Test
    fun twoAsNeededIntakesWithinTheSameMinuteAreBothKept() = runTest {
        val inserted = db.intakes.insertAll(
            listOf(
                TestData.prnIntake(id = OWN_INTAKE),
                TestData.prnIntake(id = SECOND_INTAKE),
            ),
        )

        assertTrue(inserted.none { it == -1L })
        assertEquals(2, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    @Test
    fun theFirstMarkingWinsAndTheSecondChangesNothing() = runTest {
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = OWN_INTAKE)))

        val first = db.intakes.markIfPending(
            intakeId = OWN_INTAKE,
            status = IntakeStatus.TAKEN.name,
            actedAt = FIRST_MARK_AT,
            source = IntakeSource.PHONE.name,
            actorProfileId = null,
        )
        val second = db.intakes.markIfPending(
            intakeId = OWN_INTAKE,
            status = IntakeStatus.SKIPPED.name,
            actedAt = SECOND_MARK_AT,
            source = IntakeSource.PHONE.name,
            actorProfileId = null,
        )

        assertEquals(1, first)
        assertEquals(0, second)
        val stored = requireNotNull(db.intakes.getById(OWN_INTAKE))
        assertEquals(IntakeStatus.TAKEN.name, stored.status)
        assertEquals(FIRST_MARK_AT, stored.actedAt)
        assertEquals(IntakeSource.PHONE.name, stored.source)
    }

    @Test
    fun expiredPendingIntakesAreSelectedByTheWindowBoundary() = runTest {
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = OWN_INTAKE)))

        val expired = db.intakes.selectExpiredPending(NEXT_DAY_START)

        assertEquals(listOf(OWN_INTAKE), expired.map { it.id })
    }

    @Test
    fun anIntakeWithoutItsMedicationIsRejected() = runTest {
        assertRefused<SQLiteConstraintException> {
            db.intakes.insertAll(listOf(TestData.scheduledIntake(id = OWN_INTAKE, medicationId = MISSING_ID)))
        }
    }

    private companion object {
        const val OWN_INTAKE = "aaaaaaaa-0000-4000-8000-000000000001"
        const val SECOND_INTAKE = "aaaaaaaa-0000-4000-8000-000000000002"
        const val FOREIGN_INTAKE = "aaaaaaaa-0000-4000-8000-000000000003"
        const val MISSING_ID = "ffffffff-0000-4000-8000-000000000000"

        const val DAY_START = "2026-09-25T00:00"
        const val NEXT_DAY_START = "2026-09-26T00:00"
        const val DAY_AFTER_START = "2026-09-27T00:00"

        const val FIRST_MARK_AT = 1_790_000_000_000L
        const val SECOND_MARK_AT = 1_790_000_060_000L
    }
}
