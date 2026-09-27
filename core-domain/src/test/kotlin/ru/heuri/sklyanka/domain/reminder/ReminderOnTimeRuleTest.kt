package ru.heuri.sklyanka.domain.reminder

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

    private fun fired(deviation: Long?, event: ReminderEvent = ReminderEvent.FIRED) = ReminderLogEntry(
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
    fun `the summary counts firings and how many of them were on time`() {
        val summary = ReminderOnTimeRule.summarize(
            listOf(fired(10), fired(119), fired(600), fired(null)),
        )

        assertEquals(4, summary.fired)
        assertEquals(2, summary.onTime)
        assertEquals(50, summary.percent)
        assertTrue(summary.hasData)
    }

    @Test
    fun `only firings are counted`() {
        // Запись о показе несёт то же отклонение: счёт по обоим видам удваивал бы
        // каждое срабатывание, а пробное напоминание меряет нажатие кнопки.
        val summary = ReminderOnTimeRule.summarize(
            listOf(
                fired(10),
                fired(10, ReminderEvent.SHOWN),
                fired(10, ReminderEvent.TEST),
                fired(10, ReminderEvent.RESCHEDULED),
            ),
        )

        assertEquals(1, summary.fired)
        assertEquals(1, summary.onTime)
    }

    @Test
    fun `without firings there is no share at all`() {
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
