package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.transfer.QuietHours
import ru.stohastika.sklyanka.domain.transfer.ReminderSound
import ru.stohastika.sklyanka.domain.transfer.TransferSettings
import ru.stohastika.sklyanka.domain.transfer.TransferSettingsStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
 *
 * **Два поля делегированы порту настроек напоминания** (SKL-78, `TDD.md §5.6`): «Отложить на»
 * и «Настойчивое напоминание» живут под ключами [ReminderSettings], которые читает движок
 * напоминаний. Собственных копий у переноса больше нет: две копии одного значения рано или
 * поздно назовут разные числа, и выгрузка перестанет отражать то, что видит планировщик.
 * Запись идёт через тот же порт, поэтому загрузка файла оставляет след в журнале (ПЕР-18).
 */
@Singleton
class TransferSettingsDataStore @Inject constructor(
    private val preferences: DataStore<Preferences>,
    private val reminderSettings: ReminderSettings,
) : TransferSettingsStore {

    override suspend fun read(): TransferSettings {
        val stored = preferences.data.first()
        val from = stored[QUIET_FROM]?.let(::time)
        val to = stored[QUIET_TO]?.let(::time)
        val reminders = reminderSettings.read()
        return TransferSettings(
            reminderSound = ReminderSound.ofCode(stored[SOUND]) ?: ReminderSound.DEFAULT,
            persistentReminder = reminders.persistentEnabled,
            snoozeMinutes = reminders.snoozeMinutes,
            // Тихие часы существуют только парой границ: одна граница без второй —
            // это не «половина режима», а его отсутствие.
            quietHours = if (from != null && to != null) QuietHours(from, to) else null,
        )
    }

    override suspend fun write(settings: TransferSettings) {
        // Два поля — через порт: и значение, и след в журнале. Остальное — своими ключами.
        reminderSettings.setPersistentEnabled(settings.persistentReminder)
        reminderSettings.setSnoozeMinutes(settings.snoozeMinutes)
        preferences.edit { mutable ->
            mutable[SOUND] = settings.reminderSound.code
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
        val QUIET_FROM = stringPreferencesKey("quiet_hours_from")
        val QUIET_TO = stringPreferencesKey("quiet_hours_to")
        val LAST_EXPORT = stringPreferencesKey("transfer_last_export_date")

        /**
         * Признак «загрузка началась и не завершилась» (ПЕР-31).
         *
         * Транзакцию откатит хранилище само, а сказать человеку при следующем запуске,
         * что загрузка не состоялась, больше нечем: процесс могли убить между шагами.
         */
        val IMPORT_RUNNING = booleanPreferencesKey("transfer_import_running")

        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.ROOT)
    }
}
