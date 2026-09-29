package ru.stohastika.sklyanka.feature.meds.form

import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import java.time.LocalDate
import java.time.LocalTime

/**
 * Намерения формы лекарства.
 *
 * Числовые поля приходят текстом, а не числом: разбор текста — дело одного места,
 * и «−5», набранное человеком, обязано дожить до сообщения об отказе.
 *
 * Счётчика приёмов среди намерений нет: число приёмов в сутки — производная длины
 * списка времён (`TDD.md §9.2`), поэтому «Увеличить» добавляет строку времени,
 * а «Уменьшить» её убирает.
 */
sealed interface MedFormIntent {

    data class NameChanged(val value: String) : MedFormIntent

    data class ReleaseFormChanged(val form: MedicationForm) : MedFormIntent

    data class ShapeChanged(val shape: TabletShape) : MedFormIntent

    data class DoseAmountChanged(val text: String) : MedFormIntent

    data class StrengthChanged(val text: String) : MedFormIntent

    data class ModeChanged(val mode: ScheduleMode) : MedFormIntent

    /** «Увеличить» и «Добавить время» — одно и то же действие над списком времён. */
    data object TimeAdded : MedFormIntent

    /** «Уменьшить»: убирает последнюю строку времени. */
    data object TimeRemovedFromEnd : MedFormIntent

    data class TimeRemoved(val index: Int) : MedFormIntent

    data class TimeChanged(val index: Int, val time: LocalTime) : MedFormIntent

    data object MaxPerDayIncreased : MedFormIntent

    data object MaxPerDayDecreased : MedFormIntent

    data class FoodRelationChanged(val relation: FoodRelation) : MedFormIntent

    data class StockChanged(val text: String) : MedFormIntent

    /** «Напомнить о покупке»: включает и выключает порог остатка. */
    data class StockReminderToggled(val enabled: Boolean) : MedFormIntent

    data class StartDateChanged(val date: LocalDate) : MedFormIntent

    data class EndDateChanged(val date: LocalDate) : MedFormIntent

    /** «Принимать бессрочно»: пустая дата окончания и есть бессрочный приём (ПР-6). */
    data class IndefiniteToggled(val indefinite: Boolean) : MedFormIntent

    /** Единственное действие сохранения формы — нижняя кнопка (Д-3). */
    data object Save : MedFormIntent
}
