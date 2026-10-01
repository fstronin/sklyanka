package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Reminder vibration patterns are frozen and distinguishable")
class ReminderVibrationTest {

    @Test
    fun `every pattern is well formed`() {
        ReminderChannel.entries.forEach { channel ->
            val pattern = ReminderVibration.of(channel)

            assertTrue(pattern.isNotEmpty(), "${channel.id} must not be empty")
            assertEquals(0, pattern.size % 2, "${channel.id} must pair pauses with strikes")
            assertEquals(0L, pattern.first(), "${channel.id} must start without a delay")
        }
    }

    @Test
    fun `the frozen values do not change silently`() {
        // Числа здесь — и есть заморозка (ADR-0004, ПМ-11): изменить их можно только
        // сознательно, вместе с этим тестом, а не «заодно» с правкой платформенного слоя.
        assertArrayEquals(
            longArrayOf(0, 350, 250, 350),
            ReminderVibration.of(ReminderChannel.REGULAR),
        )
        assertArrayEquals(
            longArrayOf(0, 700, 250, 700, 250, 700),
            ReminderVibration.of(ReminderChannel.PERSISTENT),
        )
    }

    @Test
    fun `the persistent pattern is the regular rhythm, longer and repeated`() {
        val regular = ReminderVibration.of(ReminderChannel.REGULAR)
        val persistent = ReminderVibration.of(ReminderChannel.PERSISTENT)

        assertTrue(strikes(persistent).size > strikes(regular).size, "the repeat must add a strike")
        assertTrue(strikes(persistent).max() > strikes(regular).max(), "the repeat strikes must be longer")
        assertEquals(pauses(regular).toSet(), pauses(persistent).toSet(), "the rhythm itself must stay the same")
        assertTrue(persistent.sum() > regular.sum(), "the repeat must last longer")
        assertFalse(persistent.contentEquals(regular), "the two patterns must not be equal")
    }

    @Test
    fun `of returns a copy so the frozen value cannot be changed`() {
        val pattern = ReminderVibration.of(ReminderChannel.REGULAR)
        pattern[0] = 999

        assertArrayEquals(
            longArrayOf(0, 350, 250, 350),
            ReminderVibration.of(ReminderChannel.REGULAR),
        )
    }

    /** Длительности ударов: нечётные позиции паттерна. */
    private fun strikes(pattern: LongArray): List<Long> = pattern.drop(1).filterIndexed { index, _ -> index % 2 == 0 }

    /** Длительности пауз: чётные позиции, кроме начальной задержки. */
    private fun pauses(pattern: LongArray): List<Long> = pattern.drop(2).filterIndexed { index, _ -> index % 2 == 0 }
}
