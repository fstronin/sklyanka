package ru.stohastika.sklyanka.feature.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason

/**
 * Полнота соответствий «значение — строка» (НФТ-42).
 *
 * Причины пересборки и виды записей журнала переводятся картой, а карта не проверяется
 * компилятором. Без этого теста новая причина появлялась бы в журнале и падала на экране
 * диагностики — то есть ровно там, куда человек пришёл разбираться, почему не пришло
 * напоминание.
 */
class ReminderLabelsTest {

    @Test
    fun `every reschedule reason has a label`() {
        assertEquals(RescheduleReason.entries.toSet(), REASON_LABELS.keys)
    }

    @Test
    fun `every journal event has a label`() {
        assertEquals(ReminderEvent.entries.toSet(), EVENT_LABELS.keys)
    }
}
