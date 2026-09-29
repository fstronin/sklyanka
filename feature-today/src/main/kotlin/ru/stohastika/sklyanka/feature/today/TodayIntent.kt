package ru.stohastika.sklyanka.feature.today

import ru.stohastika.sklyanka.domain.today.PartOfDay
import java.time.LocalTime

/**
 * Намерения экрана «Сегодня».
 *
 * Разметка состояние не меняет: результат отметки приходит новым значением потока,
 * а не возвратом из обработчика (`TDD.md §3.1`). Это убирает расхождение между тем,
 * что показано, и тем, что записано, и само собой покрывает идемпотентность (ПР-2) —
 * повторная отметка не меняет запись, значит поток не выдаёт нового значения, значит
 * карточка не мигает.
 */
sealed interface TodayIntent {

    /** «Принято» и круглая кнопка-галочка — одно и то же действие (`docs/spec/03-screens.md`). */
    data class MarkTaken(val intakeId: String) : TodayIntent

    data class MarkSkipped(val intakeId: String) : TodayIntent

    /** «Отметить все три»: одна операция на всю группу (решение ОВ-32). */
    data class MarkGroupTaken(val intakeIds: List<String>) : TodayIntent

    /** «По одному»: групповая карточка разворачивается в строки, ничего не отмечая. */
    data class ExpandGroup(val time: LocalTime) : TodayIntent

    /** Нажатие на строку секции плотного дня. */
    data class ToggleSection(val partOfDay: PartOfDay) : TodayIntent

    /** Свёрнутый блок «по потребности» в плотном дне. */
    data object TogglePrnBlock : TodayIntent

    /** «Принять» в блоке «по потребности»: приём создаётся в момент нажатия (ПР-4). */
    data class TakePrn(val medicationId: String) : TodayIntent

    // --- Переходы наружу: состояние экрана они не меняют, их разбирает маршрут ---

    /** Нажатие на строку приёма — карточка лекарства (SKL-10). */
    data class OpenMedication(val medicationId: String) : TodayIntent

    /** «Купить» в плашке остатка — карточка лекарства на блоке остатка (SKL-12). */
    data class OpenStock(val medicationId: String) : TodayIntent

    /** «Добавить лекарство» из пустого состояния — форма нового лекарства (SKL-09). */
    data object AddMedication : TodayIntent

    data object OpenMedicationList : TodayIntent

    data object OpenSettings : TodayIntent
}
