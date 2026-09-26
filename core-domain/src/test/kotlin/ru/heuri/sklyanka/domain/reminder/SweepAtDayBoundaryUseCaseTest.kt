package ru.heuri.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.Fixtures
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.time.LocalDateTime

@DisplayName("At midnight the unmarked intake becomes missed and its card goes away silently")
class SweepAtDayBoundaryUseCaseTest {

    private val world = ReminderTestWorld()

    @Test
    fun `an unmarked intake of the past day becomes missed`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)

        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))
        val expired = world.dayBoundary()

        assertEquals(1, expired)
        assertEquals(IntakeStatus.MISSED, world.stored().status)
    }

    @Test
    fun `the card of the expired intake is removed and no new notification appears`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)
        val shownBefore = world.notifications.shown.size

        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))
        world.dayBoundary()

        assertEquals(emptySet<String>(), world.notifications.retained)
        assertEquals(shownBefore, world.notifications.shown.size)
    }

    @Test
    fun `a still pending intake of the new day keeps its card`() = runTest {
        world.pending(at = LocalDateTime.of(2026, 9, 26, 14, 0))

        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))
        world.dayBoundary()

        assertEquals(setOf(Fixtures.INTAKE_ID), world.notifications.retained)
        assertEquals(IntakeStatus.PENDING, world.stored().status)
    }

    @Test
    fun `the next boundary is planned right away`() = runTest {
        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))

        world.dayBoundary()

        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 27, 0, 0)), world.alarms.dayBoundary)
    }

    @Test
    fun `the pass leaves a trace only when something really expired`() = runTest {
        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))
        world.dayBoundary()
        assertTrue(world.journal.of(ReminderEvent.MISSED_SWEEP).isEmpty())

        world.pending(at = LocalDateTime.of(2026, 9, 25, 14, 0))
        world.dayBoundary()

        val entry = world.journal.of(ReminderEvent.MISSED_SWEEP).single()
        assertEquals(1, entry.alarmsCancelled)
        assertEquals("timer", entry.reason)
    }
}
