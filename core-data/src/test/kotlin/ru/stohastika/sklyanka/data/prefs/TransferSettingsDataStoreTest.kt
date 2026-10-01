package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.domain.transfer.TransferSettings

/**
 * Единый источник значения «Отложить на» и «Настойчивого напоминания» (SKL-78, TDD §5.6).
 *
 * Перенос перестал держать собственные копии этих двух настроек: и план, и файл читают
 * и пишут один порт. Проверка держит именно это — иначе выгрузка однажды назвала бы число,
 * которого планировщик не знает.
 */
class TransferSettingsDataStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(reminders: ReminderSettings = FakeReminderSettings()) = TransferSettingsDataStore(
        preferences = PreferenceDataStoreFactory.create { folder.newFile("transfer.preferences_pb") },
        reminderSettings = reminders,
    )

    @Test
    fun `snooze and persistent are read from the reminder settings port`() = runTest {
        val reminders = FakeReminderSettings(ReminderSettingsValues(persistentEnabled = true, snoozeMinutes = 30))

        val values = store(reminders).read()

        assertTrue(values.persistentReminder)
        assertEquals(30, values.snoozeMinutes)
    }

    @Test
    fun `writing transfer settings stores both fields through the port`() = runTest {
        val reminders = FakeReminderSettings()

        store(reminders).write(TransferSettings(persistentReminder = true, snoozeMinutes = 60))

        assertEquals(true, reminders.values.persistentEnabled)
        assertEquals(60, reminders.values.snoozeMinutes)
    }

    /** Круг «запись файла → чтение» не должен разойтись с тем, что видит планировщик. */
    @Test
    fun `what the engine sees is what the transfer file carries`() = runTest {
        val reminders = FakeReminderSettings(ReminderSettingsValues(persistentEnabled = true, snoozeMinutes = 10))
        val store = store(reminders)

        store.write(store.read())

        assertEquals(10, reminders.values.snoozeMinutes)
        assertEquals(true, reminders.values.persistentEnabled)
    }

    private class FakeReminderSettings(var values: ReminderSettingsValues = ReminderSettingsValues()) :
        ReminderSettings {

        override suspend fun read(): ReminderSettingsValues = values

        override suspend fun setPersistentEnabled(enabled: Boolean) {
            values = values.copy(persistentEnabled = enabled)
        }

        override suspend fun setSnoozeMinutes(minutes: Int) {
            values = values.copy(snoozeMinutes = minutes)
        }

        override suspend fun setAlarmStrategy(kind: AlarmKind) {
            values = values.copy(alarmStrategy = kind)
        }
    }
}
