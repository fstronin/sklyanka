package ru.stohastika.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("The test reminder walks the whole path and touches nothing")
class TestReminderUseCaseTest {

    private val world = ReminderTestWorld()
    private val schedule = TestReminderUseCase(world.alarms, world.clock)
    private val show = ShowTestReminderUseCase(world.notifications, world.journal, world.clock)

    @Test
    fun `it is planned exactly one minute ahead`() = runTest {
        world.moveTo(LocalDateTime.of(2026, 9, 25, 16, 0))

        val at = schedule()

        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 16, 1)), at)
        assertEquals(at, world.alarms.test)
    }

    @Test
    fun `it creates no intake and changes no stock`() = runTest {
        world.moveTo(LocalDateTime.of(2026, 9, 25, 16, 0))
        val stockBefore = world.medication().stockLeft

        val at = schedule()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 16, 1))
        show(at)

        assertTrue(world.intakes.stored.isEmpty(), "a test reminder must not create an intake (REM-22)")
        assertEquals(stockBefore, world.medication().stockLeft)
        assertEquals(1, world.notifications.tests)
    }

    @Test
    fun `its own record carries the deviation and no intake`() = runTest {
        world.moveTo(LocalDateTime.of(2026, 9, 25, 16, 0))
        val at = schedule()

        world.moveTo(LocalDateTime.of(2026, 9, 25, 16, 1).plusSeconds(4))
        show(at)

        val entry = world.journal.of(ReminderEvent.TEST).single()
        assertEquals(4L, entry.deviationSeconds)
        assertEquals(null, entry.intakeId)
    }
}
