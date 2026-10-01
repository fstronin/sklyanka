package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues

/**
 * Набор интервалов откладывания и умолчание (ПМ-15, SKL-78).
 *
 * Состав и порядок — контракт: экран раскладывает [SnoozeIntervals.OPTIONS] строками
 * диалога, и «5, 10, 15, 30, 60» из `docs/tasks/SKL-78/acceptance.feature` обязан
 * совпадать с кодом.
 */
class SnoozeIntervalsTest {

    @Test
    fun `the options are the working set in order`() {
        assertEquals(listOf(5, 10, 15, 30, 60), SnoozeIntervals.OPTIONS)
    }

    @Test
    fun `the options have no duplicates and stay sorted`() {
        assertEquals(SnoozeIntervals.OPTIONS.sorted(), SnoozeIntervals.OPTIONS)
        assertEquals(SnoozeIntervals.OPTIONS.distinct(), SnoozeIntervals.OPTIONS)
    }

    @Test
    fun `the default is fifteen minutes and comes from the settings port`() {
        assertEquals(15, SnoozeIntervals.DEFAULT)
        assertEquals(ReminderSettingsValues.DEFAULT_SNOOZE_MINUTES, SnoozeIntervals.DEFAULT)
    }

    @Test
    fun `only the working set is allowed`() {
        SnoozeIntervals.OPTIONS.forEach { assertTrue(SnoozeIntervals.isAllowed(it)) }
        listOf(0, 1, 14, 16, 45, 90, -5).forEach { assertFalse(SnoozeIntervals.isAllowed(it)) }
    }
}
