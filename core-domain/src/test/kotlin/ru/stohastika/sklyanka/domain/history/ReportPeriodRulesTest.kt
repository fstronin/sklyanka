package ru.stohastika.sklyanka.domain.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Правила периода отчёта врачу (ОВ-125).
 *
 * Проверяются на JVM без Android: пресеты считаются от сегодняшнего дня календарными
 * месяцами, а границы «С»/«По» подчиняются правилам «не в будущем» и «окно не
 * переворачивается». Эти правила — единственная причина, по которой отчёт и «История»
 * могут разойтись, и живут они одним объектом.
 */
class ReportPeriodRulesTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 4)

    @Test
    fun `this month spans the whole current calendar month`() {
        val expected = HistoryWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(expected, window(ReportPreset.THIS_MONTH))
    }

    @Test
    fun `the previous month spans the whole previous calendar month`() {
        val expected = HistoryWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals(expected, window(ReportPreset.PREVIOUS_MONTH))
    }

    @Test
    fun `the last three months span three calendar months including the current one`() {
        val expected = HistoryWindow(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31))
        assertEquals(expected, window(ReportPreset.LAST_THREE_MONTHS))
    }

    @Test
    fun `the custom preset defaults to the first day of the current month through today`() {
        assertEquals(HistoryWindow(LocalDate.of(2026, 10, 1), today), window(ReportPreset.CUSTOM))
        assertEquals(HistoryWindow(LocalDate.of(2026, 10, 1), today), ReportPeriodRules.customDefaults(today))
    }

    @Test
    fun `an earlier start keeps the current end`() {
        val current = HistoryWindow(LocalDate.of(2026, 10, 1), today)

        val next = ReportPeriodRules.withStart(LocalDate.of(2026, 9, 20), current, today)

        assertEquals(HistoryWindow(LocalDate.of(2026, 9, 20), today), next)
    }

    @Test
    fun `a start later than the end pulls the end along`() {
        val current = HistoryWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))

        val next = ReportPeriodRules.withStart(LocalDate.of(2026, 9, 20), current, today)

        assertEquals(HistoryWindow(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20)), next)
    }

    @Test
    fun `a start in the future is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReportPeriodRules.withStart(today.plusDays(1), HistoryWindow(LocalDate.of(2026, 10, 1), today), today)
        }
    }

    @Test
    fun `an end within the window is accepted`() {
        val current = HistoryWindow(LocalDate.of(2026, 10, 1), today)

        val next = ReportPeriodRules.withEnd(LocalDate.of(2026, 10, 2), current, today)

        assertEquals(HistoryWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), next)
    }

    @Test
    fun `an end before the start is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReportPeriodRules.withEnd(LocalDate.of(2026, 9, 30), HistoryWindow(LocalDate.of(2026, 10, 1), today), today)
        }
    }

    @Test
    fun `an end in the future is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReportPeriodRules.withEnd(today.plusDays(1), HistoryWindow(LocalDate.of(2026, 10, 1), today), today)
        }
    }

    private fun window(preset: ReportPreset): HistoryWindow = ReportPeriodRules.window(preset, today)
}
