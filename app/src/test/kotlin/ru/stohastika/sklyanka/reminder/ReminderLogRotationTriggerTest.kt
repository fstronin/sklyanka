package ru.stohastika.sklyanka.reminder

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.domain.reminder.ReminderLogRotation

/**
 * Ротация журнала запускается вне критического пути (ПМ-30, п. 5).
 *
 * Проверяется именно планирование: `schedule()` обязан вернуть управление до того,
 * как уборка начнётся, иначе запуск приложения ждал бы полного прохода по журналу.
 */
class ReminderLogRotationTriggerTest {

    @Test
    fun `a rotation is scheduled without holding the caller`() = runTest {
        var rotations = 0
        val trigger = ReminderLogRotationTrigger(
            rotate = {
                rotations++
                ReminderLogRotation(removedByAge = 1, removedBySize = 0)
            },
            scope = this,
        )

        trigger.schedule()

        assertEquals("schedule() must return before the rotation runs", 0, rotations)
        advanceUntilIdle()
        assertEquals("the scheduled rotation must reach the operation", 1, rotations)
    }
}
