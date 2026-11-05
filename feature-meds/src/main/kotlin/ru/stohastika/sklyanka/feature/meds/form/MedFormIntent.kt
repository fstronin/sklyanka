package ru.stohastika.sklyanka.feature.meds.form

import ru.stohastika.sklyanka.domain.medication.CourseEndPreset
import ru.stohastika.sklyanka.domain.medication.StrengthUnit
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import java.time.DayOfWeek
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

    /**
     * Выбор единицы дозировки из закрытого списка (Р-45).
     *
     * Отдельное намерение, а не часть [StrengthChanged]: единица выбирается диалогом
     * и переживает правку числа, иначе выбор терялся бы на первой же цифре.
     */
    data class StrengthUnitChanged(val unit: StrengthUnit) : MedFormIntent

    data class ModeChanged(val mode: ScheduleMode) : MedFormIntent

    /** Выбор вида расписания в строке вида (§5.10, ПР-15). */
    data class ScheduleKindChanged(val kind: ScheduleKind) : MedFormIntent

    /** День недели: выбор множественный (ПР-16, ПР-17). */
    data class WeekdayToggled(val day: DayOfWeek) : MedFormIntent

    /** «Интервал, дней» (ПР-17): значение приходит текстом, как и остальные числа. */
    data class IntervalChanged(val text: String) : MedFormIntent

    /** «Дней приёма подряд» цикла (ПР-18). */
    data class CycleOnChanged(val text: String) : MedFormIntent

    /** «Дней перерыва» цикла (ПР-18). */
    data class CycleOffChanged(val text: String) : MedFormIntent

    /** Доза этапа: поле со словом единицы внутри, без видимой подписи (§5.10). */
    data class StageDoseChanged(val index: Int, val text: String) : MedFormIntent

    /** Длительность этапа в днях (`form_stage_days`, §5.10). */
    data class StageDaysChanged(val index: Int, val text: String) : MedFormIntent

    /** «Добавить этап»: список этапов растёт, число нормой не ограничено (ПР-19). */
    data object StageAdded : MedFormIntent

    /** Крест удаления этапа; у единственного этапа его нет — курс без этапов не собрать. */
    data class StageRemoved(val index: Int) : MedFormIntent

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

    /** Размер упаковки (ОВ-45): необязательное числовое поле под остатком. */
    data class PackageSizeChanged(val text: String) : MedFormIntent

    /** «Напомнить о покупке»: включает и выключает порог остатка. */
    data class StockReminderToggled(val enabled: Boolean) : MedFormIntent

    /** Выбор порога напоминания о покупке (SKL-74, ПМ-13). */
    data class StockThresholdChanged(val days: Int) : MedFormIntent

    data class StartDateChanged(val date: LocalDate) : MedFormIntent

    /**
     * Выбор пресета срока окончания курса (SKL-75, ПМ-12).
     *
     * Пресет — разовое действие, а не связь с датой начала: он задаёт [MedicationDraft.endDate]
     * от текущей [MedicationDraft.startDate] один раз, и последующая смена начала уже
     * выбранную дату окончания не двигает.
     */
    data class CourseEndPresetChosen(val preset: CourseEndPreset) : MedFormIntent

    data class EndDateChanged(val date: LocalDate) : MedFormIntent

    /** «Принимать бессрочно»: пустая дата окончания и есть бессрочный приём (ПР-6). */
    data class IndefiniteToggled(val indefinite: Boolean) : MedFormIntent

    /** Единственное действие сохранения формы — нижняя кнопка (Д-3). */
    data object Save : MedFormIntent
}
