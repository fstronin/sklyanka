package ru.stohastika.sklyanka.feature.history

import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.analytics.OperationResult
import ru.stohastika.sklyanka.domain.analytics.ReportExported

/**
 * Исход передачи отчёта — в событие аналитики (SKL-165, ПМ-26, TDD §4).
 *
 * Отображение живёт одним местом на обе поверхности — отчёт врачу и выгрузку истории:
 * разойдясь, они назвали бы один и тот же исход разными значениями каталога (АН-3).
 * Сами исходы остаются экранными: событие знает только три значения каталога, и
 * «места не хватило» — это отказ, а не отдельный исход аналитики.
 *
 * Событие пишется из одной точки сходимости путей передачи: успех обмена и успех записи,
 * отмена выбора места и отказ — все они проходят через неё, и второго события на одно
 * нажатие не появляется.
 */
internal fun AnalyticsRecorder.recordReportExported(outcome: ReportOutcome) {
    record(ReportExported(outcome.operationResult()))
}

/** Тем же правилом — выгрузка истории: исход у неё свой, значение каталога общее. */
internal fun AnalyticsRecorder.recordReportExported(outcome: ExportOutcome) {
    record(ReportExported(outcome.operationResult()))
}

/** Успех — то, что человек получил; отмена — не ошибка; всё остальное — отказ. */
private fun ReportOutcome.operationResult(): OperationResult = when (this) {
    ReportOutcome.SHARED, ReportOutcome.SAVED -> OperationResult.SUCCESS
    ReportOutcome.CANCELLED -> OperationResult.CANCELLED
    ReportOutcome.FAILED_SHARE, ReportOutcome.FAILED_SAVE -> OperationResult.ERROR
}

private fun ExportOutcome.operationResult(): OperationResult = when (this) {
    ExportOutcome.SHARED, ExportOutcome.SAVED -> OperationResult.SUCCESS
    ExportOutcome.CANCELLED -> OperationResult.CANCELLED
    ExportOutcome.FAILED_SHARE, ExportOutcome.FAILED_SAVE, ExportOutcome.NO_SPACE -> OperationResult.ERROR
}
