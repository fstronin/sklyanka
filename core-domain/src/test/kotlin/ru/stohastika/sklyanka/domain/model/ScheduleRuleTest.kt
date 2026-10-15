package ru.stohastika.sklyanka.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Правило вида расписания отвечает на день курса, а не на день вообще (ПР-15…ПР-19).
 *
 * До начала курса приёмов нет **ни у одного вида**: три разных ответа на один вопрос
 * (ежедневный и ступени отвечали «есть», интервал — «есть», циклы — «нет») — это три
 * правила вместо одного, и следующий вызывающий получил бы от правила не то, что получил
 * предыдущий (SKL-117, критика F8).
 */
class ScheduleRuleTest {

    private val start: LocalDate = LocalDate.of(2026, 10, 13)
    private val dayBefore: LocalDate = start.minusDays(1)

    private val kinds: List<ScheduleRule> = listOf(
        ScheduleRule.Daily,
        ScheduleRule.Weekdays(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)),
        ScheduleRule.EveryNDays(2),
        ScheduleRule.Cycles(onDays = 21, offDays = 7),
        ScheduleRule.Staged(listOf(Stage(BigDecimal("0.5"), days = 7))),
    )

    @Test
    fun `no kind gives an intake on the day before the course starts`() {
        kinds.forEach { rule ->
            assertFalse(rule.includes(dayBefore, start), "$rule must not include the day before the course")
        }
    }

    @Test
    fun `no kind gives a dose on the day before the course starts`() {
        kinds.forEach { rule ->
            assertNull(rule.doseFor(dayBefore, start), "$rule must not name a dose before the course")
        }
    }

    @Test
    fun `the first day of the course stays an intake day`() {
        // Вторник выбран, интервал и цикл считают первый день нулевым, ступени идут
        // от даты начала: приём в день начала есть у всех видов, кроме невыбранного дня.
        kinds.forEach { rule ->
            assertTrue(rule.includes(start, start), "$rule must include the first day of the course")
        }
        assertFalse(
            ScheduleRule.Weekdays(setOf(DayOfWeek.FRIDAY)).includes(start, start),
            "a weekday course must stay silent on an unselected day",
        )
    }

    @Test
    fun `the staged rule names the dose of its first stage from the first day`() {
        val rule = ScheduleRule.Staged(listOf(Stage(BigDecimal("0.5"), days = 7), Stage(BigDecimal.ONE, days = 7)))

        assertEquals(0, BigDecimal("0.5").compareTo(rule.doseFor(start, start)))
        assertEquals(0, BigDecimal("0.5").compareTo(rule.doseFor(start.plusDays(6), start)))
        assertEquals(0, BigDecimal.ONE.compareTo(rule.doseFor(start.plusDays(7), start)))
    }
}
