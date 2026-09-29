package ru.stohastika.sklyanka.data.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.ZoneId

/**
 * Устойчивость ко времени.
 *
 * Назначенное время — правило («в восемь утра»), момент отметки — факт. Пояс меняет
 * прочтение первого и не имеет права трогать второй (НФТ-34): смена пояса не изменяет
 * ни одной строки, потому что менять в них нечего.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class TimeTest {

    @get:Rule
    val db = DatabaseTestRule()

    @Before
    fun fillProfileAndMedication() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())
    }

    @Test
    fun aTimeZoneChangeDoesNotMoveTheScheduledTimeOfDay() = runTest {
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = INTAKE_ID)))
        val beforeFlight = requireNotNull(db.intakes.getById(INTAKE_ID)).scheduledAt

        val clock = FixedTimeSource(MOMENT, ZoneId.of("Europe/Moscow"))
        clock.moveTo(ZoneId.of("Asia/Almaty"))

        val afterFlight = requireNotNull(db.intakes.getById(INTAKE_ID)).scheduledAt
        assertEquals("2026-09-25T08:00", beforeFlight)
        assertEquals(beforeFlight, afterFlight)
    }

    @Test
    fun aTimeZoneChangeDoesNotRewriteAnExistingMarking() = runTest {
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = INTAKE_ID)))
        db.intakes.markIfPending(
            intakeId = INTAKE_ID,
            status = IntakeStatus.TAKEN.name,
            actedAt = MOMENT.toEpochMilli(),
            source = IntakeSource.PHONE.name,
            actorProfileId = null,
        )

        val clock = FixedTimeSource(MOMENT, ZoneId.of("Europe/Moscow"))
        clock.moveTo(ZoneId.of("America/Chicago"))

        assertEquals(MOMENT.toEpochMilli(), requireNotNull(db.intakes.getById(INTAKE_ID)).actedAt)
    }

    @Test
    fun theDayBoundaryIsLocalMidnightAndNotAMinusTwentyFourHoursArithmetic() = runTest {
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(id = INTAKE_ID, scheduledAt = TestData.LATE_EVENING),
                TestData.scheduledIntake(id = NEXT_DAY_INTAKE, scheduledAt = TestData.MORNING.plusDays(1)),
            ),
        )

        val day = db.intakes.observeDay(TestData.OWNER_ID, DAY_START, NEXT_DAY_START).first()

        assertEquals(listOf(INTAKE_ID), day.map { it.id })
    }

    private companion object {
        const val INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000001"
        const val NEXT_DAY_INTAKE = "aaaaaaaa-0000-4000-8000-000000000002"
        const val DAY_START = "2026-09-25T00:00"
        const val NEXT_DAY_START = "2026-09-26T00:00"
        val MOMENT: Instant = Instant.parse("2026-09-25T05:05:00Z")
    }
}
