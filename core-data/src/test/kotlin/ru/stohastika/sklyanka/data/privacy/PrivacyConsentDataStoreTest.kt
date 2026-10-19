package ru.stohastika.sklyanka.data.privacy

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
 * Отметка о политике (Р-89): пустой файл — предложение не принято, отметка переживает
 * перезапуск и не заводит второго состояния.
 */
class PrivacyConsentDataStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val preferences = lazy {
        PreferenceDataStoreFactory.create { folder.newFile("privacy.preferences_pb") }
    }

    private fun store() = PrivacyConsentDataStore(preferences.value)

    @Test
    fun `an empty file means the policy has not been acknowledged`() = runTest {
        assertFalse(store().acknowledged())
    }

    @Test
    fun `the acknowledgement survives a new instance over the same file`() = runTest {
        store().acknowledge()

        // Новый экземпляр поверх того же файла — это и есть перезапуск приложения.
        assertTrue(store().acknowledged())
    }

    @Test
    fun `the store writes only its own flag`() = runTest {
        store().acknowledge()

        val stored: Set<String> = preferences.value.data.first().asMap().keys.map(Preferences.Key<*>::name).toSet()

        assertEquals(setOf("privacy_acknowledged"), stored)
    }
}
