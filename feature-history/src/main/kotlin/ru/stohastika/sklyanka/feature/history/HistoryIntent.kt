package ru.stohastika.sklyanka.feature.history

import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.domain.history.ReportScope
import java.time.LocalDate

/**
 * Намерения экрана истории.
 *
 * [Back] и [SaveRequested] экран не обрабатывает сам: первое — переход, и куда он ведёт,
 * решает тот, кто собрал маршрут (возврат идёт туда, откуда человек пришёл); второе
 * требует системного выбора места, а получить его результат может только composable.
 */
sealed interface HistoryIntent {

    data object Back : HistoryIntent

    data object PreviousMonth : HistoryIntent

    data object NextMonth : HistoryIntent

    /** «Отчёт врачу»: собрать состав и показать его шторкой. */
    data object ReportRequested : HistoryIntent

    data object ReportDismissed : HistoryIntent

    /**
     * Выбран состав отчёта в шторке (Р-85 п. 1, §5.15).
     *
     * Печатается тот состав, который отмечен: расширенный собирается с разделами «Про»
     * наравне с базовым.
     */
    data class ReportScopeChosen(val scope: ReportScope) : HistoryIntent

    /** Выбран пресет периода в шторке: состав пересобирается, сообщение исхода сбрасывается. */
    data class ReportPresetChosen(val preset: ReportPreset) : HistoryIntent

    /** Подтверждено начало «Другого периода»: «По» подтягивается, если начало позже него. */
    data class ReportStartPicked(val date: LocalDate) : HistoryIntent

    /** Подтверждено окончание «Другого периода»: не раньше начала и не позже сегодня. */
    data class ReportEndPicked(val date: LocalDate) : HistoryIntent

    /**
     * Передать отчёт системным способом обмена.
     *
     * Исход решения ставится только после возврата системного выбора, поэтому намерение
     * не означает «передано» — оно означает «открыть выбор и дождаться решения» (SKL-101).
     */
    data object ShareReport : HistoryIntent

    /** Сохранить отчёт файлом: маршрут открывает системный выбор места (НФТ-17). */
    data object SaveRequested : HistoryIntent

    /** Человек выбрал место: ссылка непрозрачная, о `Uri` экран не знает. */
    data class SaveTo(val target: String) : HistoryIntent

    /**
     * Человек закрыл системный выбор места.
     *
     * Отдельное намерение, а не молчание: отмена выглядит для человека так же, как
     * ошибка записи, и след о ней обязан остаться (`TDD.md §11`).
     */
    data object SaveCancelled : HistoryIntent

    /**
     * Сохранить файл некому: в устройстве нет обработчика системного выбора места.
     *
     * Маршрут решает это до запуска и отдаёт готовый исход: сам системный диалог —
     * чужая активность, и получить отказ можно только у системы (SKL-46).
     */
    data object SaveUnavailable : HistoryIntent
}
