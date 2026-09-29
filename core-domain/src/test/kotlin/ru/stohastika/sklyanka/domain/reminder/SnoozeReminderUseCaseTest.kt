package ru.stohastika.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminder
import java.time.LocalDateTime

@DisplayName("Snoozing moves the alarm and never rewrites the planned time")
class SnoozeReminderUseCaseTest {

    private val world = ReminderTestWorld()

    @Test
    fun `the reminder comes back one interval later and the intake stays unmarked`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 1))

        assertTrue(world.snooze(Fixtures.INTAKE_ID, minutes = 15))

        assertEquals(
            world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 16)),
            world.alarms.alarms[Fixtures.INTAKE_ID],
        )
        assertEquals(IntakeStatus.PENDING, world.stored().status)
        assertTrue(world.notifications.cancelled.isNotEmpty(), "the card disappears until the repeat")
    }

    @Test
    fun `the planned time of the intake is not rewritten`() = runTest {
        val before = world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 1))

        world.snooze(Fixtures.INTAKE_ID, minutes = 15)

        // На этом держится честность истории «принято вовремя» и отчёта врачу (НАП-31).
        assertEquals(before.scheduledAt, world.stored().scheduledAt)
        assertEquals(LocalDateTime.of(2026, 9, 25, 14, 0), world.stored().scheduledAt)
    }

    /**
     * Запись — обещание, что повтор переживёт перезагрузку (SKL-35, ОВ-75).
     *
     * Без неё восстановление собирало бы окно из приёмов, а `scheduled_at` отложенного
     * приёма уже в прошлом и в окно не попадает: человек не получил бы обещанного повтора.
     */
    @Test
    fun `a snooze leaves a deferred record with the moment and the first show number`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 1))

        world.snooze(Fixtures.INTAKE_ID, minutes = 15)

        assertEquals(
            DeferredReminder(
                intakeId = Fixtures.INTAKE_ID,
                fireAt = world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 16)),
                attempt = 0,
            ),
            world.deferred.stored.getValue(Fixtures.INTAKE_ID),
        )
    }

    @ParameterizedTest(name = "an interval of {0} minutes brings the reminder back at {1}")
    @CsvSource("15,14:15", "30,14:30", "60,15:00")
    fun `the interval of the repeat comes from the setting`(minutes: Int, expected: String) = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))

        world.snooze(Fixtures.INTAKE_ID, minutes = minutes)

        val at = LocalDateTime.parse("2026-09-25T$expected")
        assertEquals(world.momentOf(at), world.alarms.alarms[Fixtures.INTAKE_ID])
    }

    @Test
    fun `an already marked intake cannot be snoozed`() = runTest {
        world.pending()
        world.take()

        assertFalse(world.snooze(Fixtures.INTAKE_ID, minutes = 15))
        assertTrue(world.deferred.stored.isEmpty(), "a closed intake leaves no deferred promise")
    }

    @Test
    fun `snoozing leaves its own trace with the interval`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 1))

        world.snooze(Fixtures.INTAKE_ID, minutes = 30)

        val entry = world.journal.of(ReminderEvent.SNOOZED).single()
        assertEquals(30, entry.minutes)
        assertEquals(Fixtures.INTAKE_ID, entry.intakeId)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 31)), entry.firedAt)
    }
}
