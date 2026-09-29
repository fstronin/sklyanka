package ru.stohastika.sklyanka.feature.history

import androidx.compose.runtime.Immutable
import ru.stohastika.sklyanka.domain.history.AdherencePeriod
import ru.stohastika.sklyanka.domain.history.ReportDocument
import java.time.LocalTime
import java.time.YearMonth

/**
 * Состояние экрана истории — единственный источник правды разметки.
 *
 * Состояний «загрузка» и «ошибка» здесь нет по тем же причинам, что на экране «Сегодня»
 * и в карточке лекарства: данные локальные, скелета загрузки в продукте нет, а отказ
 * чтения базы — аварийная ситуация, а не экранное состояние. До первого значения потока
 * экран показывает месяц с пустой сводкой, а не индикатор.
 *
 * Готовых русских строк в состоянии нет: склонения, названия месяцев и формулировка
 * строки-вывода живут в ресурсах (НФТ-42, НФТ-44).
 */
@Immutable
data class HistoryUiState(
    val month: YearMonth,
    /** Всё посчитанное доменом: плитки, календарь, разбивка (`AdherencePeriod`). */
    val period: AdherencePeriod,
    /**
     * Время самого пропускаемого приёма; `null` — строка-вывод не показывается.
     *
     * Решение принимает домен (`MissedPatternRule`), а не разметка: ложный вывод хуже
     * отсутствующего, и порог «явного лидера» обязан быть проверяем тестом.
     */
    val insightTime: LocalTime? = null,
    /** Открытый отчёт врачу; `null` — кнопка «Отчёт врачу» ещё не нажата. */
    val report: ReportUi? = null,
    /** Исход передачи отчёта: то, что человеку нельзя проглотить молча. */
    val notice: HistoryNotice? = null,
) {

    /** Данных за месяц нет: плитки показывают прочерки, блок «По лекарствам» скрыт. */
    val isEmptyMonth: Boolean get() = period.isEmpty
}

/**
 * Готовый отчёт врачу, показанный с экрана.
 *
 * Документ приходит собранным ([document]): экран его показывает, сохраняет и передаёт,
 * но не собирает. Формат артефакта не подтверждён (ОВ-15), и когда он изменится,
 * поменяется отрисовка, а не экран.
 */
@Immutable
data class ReportUi(val document: ReportDocument)

/**
 * Что сказано человеку после попытки передать отчёт.
 *
 * Отмена системного диалога — не ошибка и не молчание: для человека она выглядит точно
 * так же, как отказ записи, и различить их обязано приложение, а не человек.
 */
enum class HistoryNotice {
    REPORT_SHARED,
    REPORT_SAVED,
    REPORT_CANCELLED,
    REPORT_FAILED,
}
