package ru.stohastika.sklyanka.feature.history

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

    /** «Отчёт врачу»: собрать состав и показать его с экрана вместе с предупреждением. */
    data object ReportRequested : HistoryIntent

    data object ReportDismissed : HistoryIntent

    /** Передать отчёт системным способом обмена. */
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

    data object NoticeDismissed : HistoryIntent
}
