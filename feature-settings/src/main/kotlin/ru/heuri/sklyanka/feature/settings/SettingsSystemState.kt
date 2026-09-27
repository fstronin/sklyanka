package ru.heuri.sklyanka.feature.settings

import ru.heuri.sklyanka.domain.reminder.port.PermissionStateReader
import ru.heuri.sklyanka.domain.reminder.port.ReminderSettings
import ru.heuri.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.heuri.sklyanka.domain.watch.WatchLinkState
import ru.heuri.sklyanka.domain.watch.WatchLinkStateReader
import javax.inject.Inject

/** Снимок того, что экран настроек не хранит, а спрашивает. */
data class SettingsSystemState(
    val reminders: ReminderSettingsValues,
    val watch: WatchLinkState,
    val permissions: PermissionsSummary,
)

/**
 * Разовое чтение состояния, которое приложению не принадлежит (НФТ-52).
 *
 * Три источника собраны в одно чтение не ради краткости конструктора, а потому что
 * читаются они всегда вместе и всегда заново: разрешения и связь с часами меняются мимо
 * приложения, а значения настроек напоминания порт отдаёт разовым чтением, не потоком.
 * Одно место чтения означает, что забыть перечитать один из трёх источников при
 * возвращении на экран попросту негде.
 */
class SettingsSystemStateReader @Inject constructor(
    private val reminders: ReminderSettings,
    private val permissions: PermissionStateReader,
    private val watch: WatchLinkStateReader,
) {

    suspend fun read(): SettingsSystemState = SettingsSystemState(
        reminders = reminders.read(),
        watch = watch.read(),
        permissions = PermissionsSummary.of(permissions.all()),
    )

    /** Настойчивое напоминание (SKL-13): записать и вернуть то, что действительно сохранено. */
    suspend fun setPersistentEnabled(enabled: Boolean): ReminderSettingsValues {
        reminders.setPersistentEnabled(enabled)
        return reminders.read()
    }
}
