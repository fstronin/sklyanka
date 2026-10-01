package ru.stohastika.sklyanka.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Пересчёт даты между моделью и `DatePickerState` (SKL-69, TDD §10.1).
 *
 * Проверка на JVM без платформы: пересчёт — чистая арифметика над `java.time`,
 * и главная ловушка здесь — применить к дате локальный пояс и съехать на день назад.
 * Границы поясов (Кирибати +14, Гавайи −10) не участвуют в формуле вовсе, потому что
 * пересчёт идёт строго в UTC.
 */
class DateTimeConversionTest {

    @Test
    fun `a date survives a round trip through utc millis`() {
        val date = LocalDate.of(2026, 9, 25)

        assertEquals(date, date.toUtcMillis().toLocalDateUtc())
    }

    @Test
    fun `the millis are the utc start of that day`() {
        val date = LocalDate.of(2026, 9, 25)

        assertEquals(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), date.toUtcMillis())
    }

    @Test
    fun `the day does not slide at a positive local offset`() {
        // Кирибати (+14): локальная полночь — это середина предыдущего дня в UTC.
        // Если бы пересчёт применял локальный пояс, дата съехала бы на 24 сентября.
        val date = LocalDate.of(2026, 9, 25)
        val minusOneDay = date.toUtcMillis() - DAY_MILLIS

        assertEquals(LocalDate.of(2026, 9, 24), minusOneDay.toLocalDateUtc())
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
