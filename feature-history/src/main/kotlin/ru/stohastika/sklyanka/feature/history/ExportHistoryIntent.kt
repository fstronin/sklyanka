package ru.stohastika.sklyanka.feature.history

import ru.stohastika.sklyanka.domain.history.ReportPreset
import java.time.LocalDate

/**
 * Намерения шторки экспорта истории (SKL-119, §5.16).
 *
 * [SaveRequested] и [Back] обрабатывает не `ViewModel`, а разметка и маршрут: первое требует
 * системного выбора места, чей результат умеет получить только `composable`; второе — переход.
 * Признак шторки живёт в её состоянии, потому что системная «Назад» должна закрывать шторку,
 * а не уводить с экрана под ней.
 */
sealed interface ExportHistoryIntent {

    /**
     * Нажатие строки «Экспорт истории» в настройках.
     *
     * Открывает шторку выгрузки: файл собирается тем же расчётом, что экран истории,
     * и доступен всегда (Р-86).
     */
    data object Requested : ExportHistoryIntent

    data object Dismissed : ExportHistoryIntent

    /** Выбран пресет периода: состав пересобирается, сообщение исхода сбрасывается. */
    data class PeriodChosen(val preset: ReportPreset) : ExportHistoryIntent

    /** Подтверждено начало «Другого периода»: «По» подтягивается, если начало позже него. */
    data class StartPicked(val date: LocalDate) : ExportHistoryIntent

    /** Подтверждено окончание «Другого периода»: не раньше начала и не позже сегодня. */
    data class EndPicked(val date: LocalDate) : ExportHistoryIntent

    /**
     * Передать файл системным обменом.
     *
     * Исход решения ставится только после возврата системного выбора, поэтому намерение
     * не означает «передано» — оно означает «открыть выбор и дождаться решения» (П-7).
     */
    data object ShareRequested : ExportHistoryIntent

    /** Сохранить файл: маршрут открывает системный выбор места (НФТ-17). */
    data object SaveRequested : ExportHistoryIntent

    /** Человек выбрал место: ссылка непрозрачная, о `Uri` экран не знает. */
    data class SaveTo(val target: String) : ExportHistoryIntent

    /** Человек закрыл системный выбор места: отмена видна словами, след остаётся. */
    data object SaveCancelled : ExportHistoryIntent

    /** Сохранить некому: в устройстве нет обработчика системного выбора места (SKL-46). */
    data object SaveUnavailable : ExportHistoryIntent
}
