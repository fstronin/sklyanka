package ru.stohastika.sklyanka.domain.pro

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.domain.model.Stage
import java.math.BigDecimal
import java.time.DayOfWeek

/**
 * Таблица гейта «Про» на создание сложного расписания (`docs/tasks/SKL-117/TDD.md` §6).
 *
 * Проверка на JVM без Android и без базы: правило предметное, и экран обязан показывать
 * ровно тот ответ, который даёт домен.
 */
@DisplayName(
    "Creating a complex schedule is closed without a subscription",
)
class ComplexScheduleGateTest {

    @Test
    fun `a new course on the daily rule is always allowed`() {
        assertFalse(
            ComplexScheduleGate.isCreationOfComplex(original = null, next = ScheduleRule.Daily, included = false),
        )
    }

    @Test
    fun `a new course on a single weekday is base and allowed`() {
        val oneDay = ScheduleRule.Weekdays(setOf(DayOfWeek.WEDNESDAY))

        assertFalse(ComplexScheduleGate.isCreationOfComplex(original = null, next = oneDay, included = false))
    }

    @Test
    fun `a new course every other day is degenerate and daily`() {
        assertFalse(
            ComplexScheduleGate.isCreationOfComplex(
                original = null,
                next = ScheduleRule.EveryNDays(days = 1),
                included = false,
            ),
        )
    }

    @Test
    fun `a new course on a complex kind is closed without a subscription`() {
        val kinds = listOf(
            ScheduleRule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            ScheduleRule.EveryNDays(days = 2),
            ScheduleRule.Cycles(onDays = 21, offDays = 7),
            ScheduleRule.Staged(listOf(Stage(BigDecimal("0.5"), 7))),
        )

        kinds.forEach { kind ->
            assertTrue(
                ComplexScheduleGate.isCreationOfComplex(original = null, next = kind, included = false),
                "creating $kind without a subscription must be closed",
            )
            assertFalse(
                ComplexScheduleGate.isCreationOfComplex(original = null, next = kind, included = true),
                "creating $kind with a subscription must pass",
            )
        }
    }

    @Test
    fun `moving a daily course to a complex kind is closed without a subscription`() {
        assertTrue(
            ComplexScheduleGate.isCreationOfComplex(
                original = ScheduleRule.Daily,
                next = ScheduleRule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
                included = false,
            ),
        )
    }

    @Test
    fun `widening a single weekday to two is a creation of complex`() {
        assertTrue(
            ComplexScheduleGate.isCreationOfComplex(
                original = ScheduleRule.Weekdays(setOf(DayOfWeek.MONDAY)),
                next = ScheduleRule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
                included = false,
            ),
        )
    }

    @Test
    fun `editing an already complex course passes whole, a complex kind to another included`() {
        val complex = ScheduleRule.Cycles(onDays = 21, offDays = 7)
        val another = ScheduleRule.Staged(listOf(Stage(BigDecimal.ONE, 30)))

        assertFalse(
            ComplexScheduleGate.isCreationOfComplex(
                original = ScheduleRule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
                next = another,
                included = false,
            ),
            "changing one complex kind to another is the same already subscribed course",
        )
        assertFalse(
            ComplexScheduleGate.isCreationOfComplex(
                original = complex,
                next = complex.copy(onDays = 14),
                included = false,
            ),
        )
    }

    @Test
    fun `moving a complex course to a base kind is always allowed`() {
        val complex = ScheduleRule.Cycles(onDays = 21, offDays = 7)

        assertFalse(
            ComplexScheduleGate.isCreationOfComplex(
                original = complex,
                next = ScheduleRule.Daily,
                included = false,
            ),
        )
        assertFalse(
            ComplexScheduleGate.isCreationOfComplex(
                original = complex,
                next = ScheduleRule.Weekdays(setOf(DayOfWeek.SUNDAY)),
                included = false,
            ),
        )
    }
}
