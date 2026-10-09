package ru.stohastika.sklyanka.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.data.room.DatabaseState
import ru.stohastika.sklyanka.data.room.MigrationCopyBudget

/**
 * Решение сработавшего будильника (Р-44, `ADR-0012` п. 7, SKL-128).
 *
 * Проверяется на JVM: правило предметное («напоминание, не способное отработать честно,
 * не лжёт»), а устройство здесь ничего не добавляет — приёмник только спрашивает решение.
 */
class WakeUpDecisionTest {

    @Test
    fun `an ordinary database means business as usual`() {
        assertEquals(WakeUpDecision.Handle, wakeUpDecision(DatabaseState.Ready))
    }

    @Test
    fun `a newer database means telling the person to update`() {
        assertEquals(WakeUpDecision.AskToUpdate, wakeUpDecision(DatabaseState.Newer))
    }

    @Test
    fun `a database without room for the migration copy asks to free space, not to update`() {
        // Совет не по причине человек выполнить не может: обновление места не добавляет.
        val budget = MigrationCopyBudget(requiredBytes = 50, freeBytes = 10)

        assertEquals(WakeUpDecision.AskToFreeSpace, wakeUpDecision(DatabaseState.NotEnoughSpace(budget)))
    }
}
