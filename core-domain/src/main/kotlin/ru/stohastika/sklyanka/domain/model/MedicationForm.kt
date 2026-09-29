package ru.stohastika.sklyanka.domain.model

/**
 * Форма выпуска лекарства (docs/spec/02-data-model.md §5).
 *
 * Значение хранится строкой — именем константы, а не порядковым номером:
 * номер ломается при вставке нового значения в середину списка и нечитаем
 * в файле переноса данных (МГ-6).
 */
enum class MedicationForm {
    TABLET,
    CAPSULE,
    SPRAY,
    SYRUP,
    DROPS,
    INJECTION,
    ;

    /** Вид таблетки применим только к этим формам (ПР-9, ИНВ-16). */
    val allowsShape: Boolean get() = this == TABLET || this == CAPSULE
}
