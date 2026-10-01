package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues

/**
 * Набор интервалов откладывания (ПМ-15) и значение по умолчанию.
 *
 * Список параметризуемый: состав меняется здесь, а не в вёрстке. Значение хранится
 * как Int (`snooze_minutes`), поэтому неизвестное сохранённое число не отвергается —
 * его может принести файл переноса.
 */
object SnoozeIntervals {

    /** Рабочий состав интервалов, минуты (ПМ-15). Единственное место, где задан набор. */
    val OPTIONS: List<Int> = listOf(5, 10, 15, 30, 60)

    /** Значение по умолчанию берётся из порта настроек, чтобы число не разъехалось. */
    val DEFAULT: Int = ReminderSettingsValues.DEFAULT_SNOOZE_MINUTES

    /** Допустим ли интервал: значение вне набора экран отклонит, хранилище примет. */
    fun isAllowed(minutes: Int): Boolean = minutes in OPTIONS
}
