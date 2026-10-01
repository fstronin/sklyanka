package ru.stohastika.sklyanka.platform.reminder

import ru.stohastika.sklyanka.BuildConfig
import ru.stohastika.sklyanka.domain.reminder.port.ReminderMeasurement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Режим измерения поверх флага сборки (SKL-85, `TDD.md §5.8`).
 *
 * Флаг `REMINDER_MEASUREMENT` — свойство артефакта, а не состояние данных: он приходит
 * из `BuildConfig` и в обычной сборке всегда `false`. Переключателя варианта в ней нет,
 * и сохранённое значение на планирование не влияет (ПМ-20, ADR-0011).
 */
@Singleton
class BuildMeasurementMode @Inject constructor() : ReminderMeasurement {

    override fun available(): Boolean = BuildConfig.REMINDER_MEASUREMENT
}
