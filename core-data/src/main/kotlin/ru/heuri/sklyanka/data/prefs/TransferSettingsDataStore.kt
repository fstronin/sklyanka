package ru.heuri.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import ru.heuri.sklyanka.domain.transfer.QuietHours
import ru.heuri.sklyanka.domain.transfer.ReminderSound
import ru.heuri.sklyanka.domain.transfer.TransferSettings
import ru.heuri.sklyanka.domain.transfer.TransferSettingsStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Настройки, участвующие в переносе, плюс дата последней выгрузки (ПЕР-4, ПЕР-12).
 *
 * Живут в постоянных настройках приложения, а не в базе: перенос не вводит ни одного нового
 * поля в `Profile`, `Medication` и `Intake` (`TDD.md §11`), а дата последней выгрузки —
 * свойство устройства, а не данных, и переезжать вместе с ними не должна.
 *
 * Экран настроек (SKL-20) будет писать сюда же — здесь только те значения, которые перенос
 * обязан уметь прочитать и записать.
 *
 * Живёт в `core-data`, рядом с [ActiveProfileDataStore], а не в `app`: постоянные настройки —
 * это хранилище, и `DataStore` подключён здесь. В `app` остаётся только то, что требует
 * системных механизмов, — файлы и планировщик.
 */
@Singleton
class TransferSettingsDataStore @Inject constructor(private val preferences: DataStore<Preferences>) :
    TransferSettingsStore {

    override suspend fun read(): TransferSettings {
        val stored = preferences.data.first()
        val from = stored[QUIET_FROM]?.let(::time)
        val to = stored[QUIET_TO]?.let(::time)
        return TransferSettings(
            reminderSound = ReminderSound.ofCode(stored[SOUND]) ?: ReminderSound.DEFAULT,
            persistentReminder = stored[PERSISTENT] ?: false,
            snoozeMinutes = stored[SNOOZE] ?: TransferSettings.DEFAULT_SNOOZE_MINUTES,
            // Тихие часы существуют только парой границ: одна граница без второй —
            // это не «половина режима», а его отсутствие.
            quietHours = if (from != null && to != null) QuietHours(from, to) else null,
            watchEnabled = stored[WATCH] ?: false,
            watchSnoozeButton = stored[WATCH_SNOOZE] ?: false,
        )
    }

    override suspend fun write(settings: TransferSettings) {
        preferences.edit { mutable ->
            mutable[SOUND] = settings.reminderSound.code
            mutable[PERSISTENT] = settings.persistentReminder
            mutable[SNOOZE] = settings.snoozeMinutes
            mutable[WATCH] = settings.watchEnabled
            mutable[WATCH_SNOOZE] = settings.watchSnoozeButton
            val quiet = settings.quietHours
            if (quiet == null) {
                mutable.remove(QUIET_FROM)
                mutable.remove(QUIET_TO)
            } else {
                mutable[QUIET_FROM] = quiet.from.format(TIME)
                mutable[QUIET_TO] = quiet.to.format(TIME)
            }
        }
    }

    /** Null означает «выгрузки не было ни разу»: тогда строки о ней на экране нет (критерий 5). */
    override suspend fun lastExportDate(): LocalDate? =
        preferences.data.first()[LAST_EXPORT]?.let { runCatching { LocalDate.parse(it, DATE) }.getOrNull() }

    override suspend fun setLastExportDate(date: LocalDate) {
        preferences.edit { it[LAST_EXPORT] = date.format(DATE) }
    }

    override suspend fun importInProgress(): Boolean = preferences.data.first()[IMPORT_RUNNING] ?: false

    override suspend fun setImportInProgress(value: Boolean) {
        preferences.edit { it[IMPORT_RUNNING] = value }
    }

    private fun time(raw: String): LocalTime? = runCatching { LocalTime.parse(raw, TIME) }.getOrNull()

    private companion object {
        val SOUND = stringPreferencesKey("reminder_sound")
        val PERSISTENT = booleanPreferencesKey("reminder_persistent")
        val SNOOZE = intPreferencesKey("reminder_snooze_minutes")
        val QUIET_FROM = stringPreferencesKey("quiet_hours_from")
        val QUIET_TO = stringPreferencesKey("quiet_hours_to")
        val WATCH = booleanPreferencesKey("watch_enabled")
        val WATCH_SNOOZE = booleanPreferencesKey("watch_snooze_button")
        val LAST_EXPORT = stringPreferencesKey("transfer_last_export_date")

        /**
         * Признак «загрузка началась и не завершилась» (ПЕР-31).
         *
         * Транзакцию откатит хранилище само, а сказать человеку при следующем запуске,
         * что загрузка не состоялась, больше нечем: процесс могли убить между шагами.
         */
        val IMPORT_RUNNING = booleanPreferencesKey("transfer_import_running")

        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
