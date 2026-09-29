package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Две настройки напоминания в постоянных настройках приложения (`TDD.md §4.2`).
 *
 * Не в Room: значения атомарны, не участвуют в выборках и не связаны отношениями.
 * Не в `SharedPreferences`: проект уже читает настройки через `DataStore`, и второй
 * механизм ради двух ключей — лишняя поверхность.
 *
 * Ключей тихих часов здесь нет и не появится до решения по НАП-28: настройка, которая
 * глушит напоминание, прямо противоречит НАП-1, а заготовка под неё в коде — приглашение
 * включить полуработающую функцию.
 */
@Singleton
class ReminderSettingsStore @Inject constructor(private val store: DataStore<Preferences>) : ReminderSettings {

    override suspend fun read(): ReminderSettingsValues = store.data
        .map { prefs ->
            ReminderSettingsValues(
                persistentEnabled = prefs[PERSISTENT] ?: false,
                snoozeMinutes = prefs[SNOOZE_MINUTES] ?: ReminderSettingsValues.DEFAULT_SNOOZE_MINUTES,
            )
        }
        .first()

    override suspend fun setPersistentEnabled(enabled: Boolean) {
        store.edit { it[PERSISTENT] = enabled }
    }

    override suspend fun setSnoozeMinutes(minutes: Int) {
        require(minutes > 0) { "the snooze interval must be positive" }
        store.edit { it[SNOOZE_MINUTES] = minutes }
    }

    private companion object {
        val PERSISTENT = booleanPreferencesKey("persistent_reminder_enabled")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
    }
}
