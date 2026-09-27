package ru.heuri.sklyanka.data.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Хранилище онбординга: два флага и ни одного состояния разрешения (`TDD.md §4`).
 *
 * Проверяется на JVM с файлом настроек во временном каталоге: `DataStore` для этого не нужен
 * ни `Context`, ни устройство.
 */
class OnboardingStateDataStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val preferences = lazy {
        PreferenceDataStoreFactory.create { folder.newFile("onboarding.preferences_pb") }
    }

    private fun store() = OnboardingStateDataStore(preferences.value)

    @Test
    fun `an empty file means the person has not seen the screen and confirmed nothing`() = runTest {
        val store = store()

        assertFalse(store.seen())
        assertFalse(store.autostartConfirmed())
    }

    @Test
    fun `the seen flag survives a new instance over the same file`() = runTest {
        store().markSeen()

        // Новый экземпляр поверх того же файла — это и есть перезапуск приложения.
        assertTrue(store().seen())
    }

    @Test
    fun `the manual autostart confirmation is written and read back`() = runTest {
        val store = store()

        store.setAutostartConfirmed(true)

        assertTrue(store().autostartConfirmed())
    }

    @Test
    fun `the manual autostart confirmation can be withdrawn`() = runTest {
        val store = store()
        store.setAutostartConfirmed(true)

        store.setAutostartConfirmed(false)

        assertFalse(store().autostartConfirmed())
    }

    @Test
    fun `after writing everything it can write, the file holds no permission state`() = runTest {
        val store = store()
        store.markSeen()
        store.setAutostartConfirmed(true)

        val stored: Set<String> = preferences.value.data.first().asMap().keys.map(Preferences.Key<*>::name).toSet()

        // НФТ-52: состояние разрешения, сохранённое здесь, начало бы врать в момент отзыва.
        assertEquals(OnboardingStateDataStore.KEYS, stored)
        assertFalse(stored.any { it.contains("notification") })
        assertFalse(stored.any { it.contains("exact") })
        assertFalse(stored.any { it.contains("granted") })
    }
}
