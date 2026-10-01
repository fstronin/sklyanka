package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Отклонение словами (ОВ-49, SKL-79).
 *
 * Порог «вовремя» совпадает с НАП-2, а минуты считаются целыми: экран говорит «с опозданием
 * на N мин», и секунды, оставшиеся от деления, слова не заслуживают.
 */
class ReminderDeviationWordsTest {

    @Test
    fun `zero and two minutes are on time`() {
        assertTrue(ReminderDeviationWords.isOnTime(0))
        assertTrue(ReminderDeviationWords.isOnTime(120))
    }

    @Test
    fun `over the tolerance and negative deviations are not on time`() {
        assertFalse(ReminderDeviationWords.isOnTime(121))
        assertFalse(ReminderDeviationWords.isOnTime(-1))
        assertFalse(ReminderDeviationWords.isOnTime(null))
    }

    @Test
    fun `late minutes are whole minutes`() {
        assertEquals(0, ReminderDeviationWords.lateMinutes(59))
        assertEquals(2, ReminderDeviationWords.lateMinutes(121))
        assertEquals(15, ReminderDeviationWords.lateMinutes(900))
    }
}
