package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Сводка «вовремя за 7 дней» (НФТ-51) и правило НАП-2.
 *
 * Границы проверяются числами требования, а не «примерно двумя минутами»: 120 секунд
 * входят, 121 — нет, и отрицательное отклонение вовремя не считается.
 */
class ReminderOnTimeRuleTest {

    private val now: Instant = Instant.parse("2026-09-25T11:00:00Z")

    private fun shown(deviation: Long?, event: ReminderEvent = ReminderEvent.SHOWN) = ReminderLogEntry(
        occurredAt = now,
        event = event,
        intakeId = "intake-1",
        deviationSeconds = deviation,
    )

    @Test
    fun `the on time boundary is the one the requirement names`() {
        assertTrue(ReminderOnTimeRule.isOnTime(0))
        assertTrue(ReminderOnTimeRule.isOnTime(120))
        assertFalse(ReminderOnTimeRule.isOnTime(121))
    }

    @Test
    fun `firing before the planned moment is not counted as on time`() {
        // Срабатывание раньше плана — дефект планирования, и прятать его в долю
        // «вовремя» значит прятать ошибку.
        assertFalse(ReminderOnTimeRule.isOnTime(-1))
    }

    @Test
    fun `an entry without a deviation is not on time`() {
        assertFalse(ReminderOnTimeRule.isOnTime(null))
    }

    @Test
    fun `the summary counts presentations and how many of them were on time`() {
        val summary = ReminderOnTimeRule.summarize(
            listOf(shown(10), shown(119), shown(600), shown(null)),
        )

        assertEquals(4, summary.fired)
        assertEquals(2, summary.onTime)
        assertEquals(50, summary.percent)
        assertTrue(summary.hasData)
    }

    @Test
    fun `only presentations are counted`() {
        // Получение управления показом не является (`04-reminders.md` §1), повтор опаздывает
        // от того же планового времени по построению, а пробное меряет нажатие кнопки.
        val summary = ReminderOnTimeRule.summarize(
            listOf(
                shown(10),
                shown(10, ReminderEvent.FIRED),
                shown(10, ReminderEvent.REPEATED),
                shown(10, ReminderEvent.TEST),
                shown(10, ReminderEvent.RESCHEDULED),
            ),
        )

        assertEquals(1, summary.fired)
        assertEquals(1, summary.onTime)
    }

    @Test
    fun `a firing that never reached the shade is not in the share`() {
        // Тихий отказ показа: будильник дошёл до приложения, уведомление до человека — нет.
        // Счёт по FIRED засчитал бы его вовремя, и доля обещала бы не то, что измеряет.
        val summary = ReminderOnTimeRule.summarize(
            listOf(shown(3, ReminderEvent.FIRED), shown(3, ReminderEvent.SUPPRESSED)),
        )

        assertFalse(summary.hasData, "a reminder the person never saw cannot make the share")
        assertNull(summary.percent)
    }

    @Test
    fun `without presentations there is no share at all`() {
        val summary = ReminderOnTimeRule.summarize(emptyList())

        assertFalse(summary.hasData)
        assertNull(summary.percent, "a share of 0 % without any firing is a lie the person would act on")
    }

    @Test
    fun `the window is the seven days the requirement names`() {
        assertEquals(7L, ReminderOnTimeRule.WINDOW.toDays())
        assertEquals(7L, ReminderOnTimeRule.summarize(emptyList()).days)
    }
}
