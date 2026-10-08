package ru.stohastika.sklyanka.feature.settings

import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import javax.inject.Inject

/** Снимок того, что экран настроек не хранит, а спрашивает. */
data class SettingsSystemState(val reminders: ReminderSettingsValues, val permissions: PermissionsSummary)

/**
 * Разовое чтение состояния, которое приложению не принадлежит (НФТ-52).
 *
 * Два источника собраны в одно чтение не ради краткости конструктора, а потому что
 * читаются они всегда вместе и всегда заново: разрешения меняются мимо
 * приложения, а значения настроек напоминания порт отдаёт разовым чтением, не потоком.
 * Одно место чтения означает, что забыть перечитать один из источников при
 * возвращении на экран попросту негде.
 */
class SettingsSystemStateReader @Inject constructor(
    private val reminders: ReminderSettings,
    private val permissions: PermissionStateReader,
) {

    suspend fun read(): SettingsSystemState = SettingsSystemState(
        reminders = reminders.read(),
        permissions = PermissionsSummary.of(permissions.all()),
    )

    /** Настойчивое напоминание (SKL-13): записать и вернуть то, что действительно сохранено. */
    suspend fun setPersistentEnabled(enabled: Boolean): ReminderSettingsValues {
        reminders.setPersistentEnabled(enabled)
        return reminders.read()
    }

    /** Интервал откладывания (SKL-78): записать и вернуть то, что действительно сохранено. */
    suspend fun setSnoozeMinutes(minutes: Int): ReminderSettingsValues {
        reminders.setSnoozeMinutes(minutes)
        return reminders.read()
    }
}
