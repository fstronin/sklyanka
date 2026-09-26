package ru.heuri.sklyanka.domain.today

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.intake.EndOfDayIntakeWindow
import ru.heuri.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.schedule.FakeIntakeRepository
import ru.heuri.sklyanka.domain.schedule.FakeMedicationRepository
import ru.heuri.sklyanka.domain.schedule.RecordingOperationLog
import ru.heuri.sklyanka.domain.stock.StockWarningForProfileUseCase
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Наблюдаемые сутки экрана: переход через полночь, метка «Сейчас» и смена профиля.
 *
 * Часы идут вместе с виртуальным временем теста ([VirtualTimeSource]), поэтому полночь
 * наступает за миллисекунды и проверяется по-настоящему, а не ожиданием реальных суток.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTodayAgendaUseCaseTest {

    private val losartan = Today.medication("m-losartan", times = listOf(LocalTime.of(21, 0)))
    private val magnesium = Today.medication("m-magnesium", times = listOf(LocalTime.of(14, 0)))

    @Test
    fun `the open screen moves to the next day on its own`() = runTest {
        val world = world(startAt = Today.at(23, 58))
        world.intakes.put(Today.intake("i-today", losartan.id, Today.at(21, 0), IntakeStatus.TAKEN))
        world.intakes.put(Today.intake("i-tomorrow", losartan.id, Today.at(21, 0).plusDays(1)))

        world.observe().test {
            assertEquals(Today.DATE, awaitItem().date)

            // Две минуты виртуального времени — и полночь наступила.
            advanceTimeBy(Duration.ofMinutes(3).toMillis())

            val next = awaitItem()
            assertEquals(Today.DATE.plusDays(1), next.date)
            assertEquals(listOf("i-tomorrow"), next.layout.groups.flatMap { group -> group.items.map { it.id } })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the progress of the new day starts from zero`() = runTest {
        val world = world(startAt = Today.at(23, 58))
        world.intakes.put(Today.intake("i-today", losartan.id, Today.at(21, 0), IntakeStatus.TAKEN))
        world.intakes.put(Today.intake("i-tomorrow", losartan.id, Today.at(21, 0).plusDays(1)))

        world.observe().test {
            assertEquals(1, awaitItem().progress.taken)

            advanceTimeBy(Duration.ofMinutes(3).toMillis())

            val next = awaitItem()
            assertEquals(0, next.progress.taken)
            assertEquals(1, next.progress.planned)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `intakes of past days do not reach today's list`() = runTest {
        val world = world(startAt = Today.at(9, 0).plusDays(1))
        world.intakes.put(Today.intake("i-yesterday", losartan.id, Today.at(21, 0)))
        world.intakes.put(Today.intake("i-today", losartan.id, Today.at(21, 0).plusDays(1)))

        world.observe().test {
            val agenda = awaitItem()

            assertEquals(listOf("i-today"), agenda.layout.groups.flatMap { group -> group.items.map { it.id } })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening the screen expires the intakes left without a mark`() = runTest {
        val world = world(startAt = Today.at(9, 0).plusDays(1))
        world.intakes.put(Today.intake("i-yesterday", losartan.id, Today.at(21, 0)))

        world.observe().test {
            awaitItem()

            assertEquals(IntakeStatus.MISSED, world.intakes.stored.getValue("i-yesterday").status)
            assertTrue(world.log.events().contains(OpEvent.INTAKE_AUTO_MISSED))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the current mark moves between groups as the day goes on`() = runTest {
        val world = world(startAt = Today.at(13, 59))
        world.intakes.put(Today.intake("i-day", magnesium.id, Today.at(14, 0)))
        world.medications.put(magnesium)

        world.observe().test {
            assertFalse(awaitItem().layout.groups.single().isNow)

            advanceTimeBy(Duration.ofMinutes(2).toMillis())

            assertTrue(awaitItem().layout.groups.single().isNow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching the profile rebuilds the screen`() = runTest {
        val world = world(startAt = Today.at(9, 0))
        world.intakes.put(Today.intake("i-mine", losartan.id, Today.at(21, 0)))

        world.observe().test {
            assertEquals(1, awaitItem().layout.groups.size)

            world.activeProfile.switchTo("profile-other")

            assertTrue(awaitItem().layout.groups.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.world(startAt: LocalDateTime): World {
        val clock = VirtualTimeSource(testScheduler, Today.moment(startAt), Today.ZONE)
        val medications = FakeMedicationRepository(clock)
        medications.put(losartan)
        val delegate = FakeIntakeRepository(clock, medications)
        return World(clock, medications, DayStreamIntakeRepository(delegate))
    }

    /** Обвязка сценария: часы, хранилища и собранный сценарий наблюдения суток. */
    private class World(
        val clock: VirtualTimeSource,
        val medications: FakeMedicationRepository,
        val intakes: DayStreamIntakeRepository,
    ) {
        val log = RecordingOperationLog()
        val activeProfile = FakeActiveProfileRepository(Today.PROFILE)

        fun observe() = ObserveTodayAgendaUseCase(
            activeProfile = activeProfile,
            intakes = intakes,
            medications = medications,
            sweepMissed = SweepMissedIntakesUseCase(intakes, EndOfDayIntakeWindow(), clock, log),
            stockWarnings = StockWarningForProfileUseCase(medications, clock, log),
            time = clock,
            build = BuildTodayAgenda(),
        )()
    }
}
