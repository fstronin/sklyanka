package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Настройки напоминания в постоянных настройках приложения (`TDD.md §4.2`).
 *
 * Не в Room: значения атомарны, не участвуют в выборках и не связаны отношениями.
 * Не в `SharedPreferences`: проект уже читает настройки через `DataStore`, и второй
 * механизм ради трёх ключей — лишняя поверхность.
 *
 * Ключ варианта измерения (`alarm_strategy`) в обычной сборке инертен: переключателя
 * в ней нет, и `EffectiveAlarmStrategy` не читает сохранённое значение. Незнакомый код
 * читается как надёжный механизм (МГ-6).
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
                alarmStrategy = AlarmKind.of(prefs[ALARM_STRATEGY]),
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

    override suspend fun setAlarmStrategy(kind: AlarmKind) {
        store.edit { it[ALARM_STRATEGY] = kind.code }
    }

    private companion object {
        val PERSISTENT = booleanPreferencesKey("persistent_reminder_enabled")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val ALARM_STRATEGY = stringPreferencesKey("alarm_strategy")
    }
}
