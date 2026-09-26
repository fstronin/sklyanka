package ru.heuri.sklyanka.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.heuri.sklyanka.domain.reminder.port.ReminderSettingsValues

/**
 * Настройки напоминания: значения по умолчанию и чтение после записи (`TDD.md §4.2`).
 *
 * Проверяется на JVM с файлом настроек во временном каталоге: `DataStore` для этого
 * не нужен ни `Context`, ни устройство, а значения по умолчанию — часть продуктового
 * поведения («Настойчивое напоминание» выключено, «Отложить на» 15 минут).
 */
class ReminderSettingsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): ReminderSettingsStore = ReminderSettingsStore(
        PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `defaults come from the product decision and not from an empty file`() = runTest {
        val values = store().read()

        assertFalse("persistent reminders are off by default", values.persistentEnabled)
        assertEquals(ReminderSettingsValues.DEFAULT_SNOOZE_MINUTES, values.snoozeMinutes)
        assertEquals(15, values.snoozeMinutes)
    }

    @Test
    fun `written values are read back`() = runTest {
        val store = store()

        store.setPersistentEnabled(true)
        store.setSnoozeMinutes(30)

        val values = store.read()
        assertTrue(values.persistentEnabled)
        assertEquals(30, values.snoozeMinutes)
    }

    @Test
    fun `a non-positive interval is refused`() = runTest {
        val store = store()

        val refused = runCatching { store.setSnoozeMinutes(0) }

        assertTrue("zero minutes would mean an endless repeat", refused.isFailure)
        assertEquals(15, store.read().snoozeMinutes)
    }
}
