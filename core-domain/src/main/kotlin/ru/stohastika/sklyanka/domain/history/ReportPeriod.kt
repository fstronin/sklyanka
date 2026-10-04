package ru.stohastika.sklyanka.domain.history

import java.time.LocalDate
import java.time.YearMonth

/**
 * Пресет периода отчёта врачу (ОВ-125).
 *
 * Период выгрузки больше не привязан к месяцу «Истории»: чипы шторки задают его сами.
 * Пресеты считаются от сегодняшнего дня — календарными месяцами, а не скользящими
 * сутками: неровные даты хуже читаются в PDF и не совпадают ни с одним пресетом
 * (решение дизайна 04.10.2026).
 */
enum class ReportPreset {
    THIS_MONTH,
    PREVIOUS_MONTH,
    LAST_THREE_MONTHS,
    CUSTOM,
}

/**
 * Границы периода отчёта, считаемые от сегодняшнего дня (`TDD.md`).
 *
 * Отдельный чистый объект, а не арифметика в `ViewModel`: правила «По» не раньше «С»
 * и не позже сегодня, «С» позже «По» подтягивает «По» к «С» — проверяются на JVM
 * без Android (НФТ-46), и у них одно место.
 */
object ReportPeriodRules {

    fun window(preset: ReportPreset, today: LocalDate): HistoryWindow = when (preset) {
        ReportPreset.THIS_MONTH -> monthOf(today)
        ReportPreset.PREVIOUS_MONTH -> monthOf(today.minusMonths(1))
        ReportPreset.LAST_THREE_MONTHS -> HistoryWindow(
            from = YearMonth.from(today).minusMonths(2).atDay(1),
            to = YearMonth.from(today).atEndOfMonth(),
        )
        ReportPreset.CUSTOM -> customDefaults(today)
    }

    /** «Другой период» при первом открытии: с первого числа текущего месяца по сегодня. */
    fun customDefaults(today: LocalDate): HistoryWindow = HistoryWindow(today.withDayOfMonth(1), today)

    /**
     * Новое начало периода. «С» позже текущего «По» не запрещается: «По» подтягивается
     * к выбранной дате — перевёрнутого окна не бывает (ОВ-125).
     */
    fun withStart(start: LocalDate, current: HistoryWindow, today: LocalDate): HistoryWindow {
        require(!start.isAfter(today)) { "report period must not start in the future: $start" }
        return if (start.isAfter(current.to)) HistoryWindow(start, start) else HistoryWindow(start, current.to)
    }

    /** Новое окончание: не раньше начала и не позже сегодня — границы задаёт диалог даты. */
    fun withEnd(end: LocalDate, current: HistoryWindow, today: LocalDate): HistoryWindow {
        require(!end.isAfter(today)) { "report period must not end in the future: $end" }
        require(!end.isBefore(current.from)) { "report period must not end before it starts: $end < ${current.from}" }
        return HistoryWindow(current.from, end)
    }

    private fun monthOf(date: LocalDate): HistoryWindow = HistoryWindow.of(YearMonth.from(date))
}
