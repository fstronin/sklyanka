package ru.stohastika.sklyanka.platform.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel

/**
 * Правило тревоги показа напоминания (НАП-25, SKL-63).
 *
 * Звук и вибрация уведомления подавляются флагом «тревожить один раз» на обновлении уже
 * показанной карточки. Обычный канал обновляется молча (один слот — одна карточка,
 * НАП-36), а настойчивый повтор обязан звучать и вибрировать заметнее первого, даже если
 * карточка осталась в шторке, — иначе повтор проходит незамеченным ровно в том сценарии,
 * ради которого существует (приём не отмечен).
 */
class ReminderAlertingTest {

    @Test
    fun `a persistent repeat alerts on top of a living card`() {
        assertFalse(
            "a persistent repeat must sound even if the card is still shown",
            onlyAlertOnce(ReminderChannel.PERSISTENT),
        )
    }

    @Test
    fun `a regular card update stays silent`() {
        assertTrue(
            "a second intake of the same slot updates the card without a second sound",
            onlyAlertOnce(ReminderChannel.REGULAR),
        )
    }
}
