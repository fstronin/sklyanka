package ru.stohastika.sklyanka.data.analytics

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentDecision
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentVersion

/**
 * Согласие на аналитику: отдельный файл, три состояния и версия предмета (TDD §7.1).
 *
 * Проверяется на JVM с файлом во временном каталоге: `DataStore` для этого не нужен
 * ни `Context`, ни устройство. Отдельный файл — свойство ПЕР-37: перенос данных
 * в него не пишет.
 */
class AnalyticsConsentDataStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val preferences = lazy {
        PreferenceDataStoreFactory.create { folder.newFile("analytics-consent.preferences_pb") }
    }

    private fun store() = AnalyticsConsentDataStore(preferences.value)

    @Test
    fun `an empty file means the consent was never asked`() = runTest {
        assertEquals(AnalyticsConsent.NOT_ASKED, store().read())
    }

    @Test
    fun `the decision and the material version survive a new instance over the same file`() = runTest {
        store().record(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)

        // Новый экземпляр поверх того же файла — это и есть перезапуск приложения.
        assertEquals(
            AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL),
            store().read(),
        )
    }

    @Test
    fun `the observed flow reports a new decision`() = runTest {
        val observed = mutableListOf<AnalyticsConsent>()
        val store = store()

        store.observe().first()
        store.record(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)
        observed += store.observe().first()

        assertEquals(
            listOf(AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)),
            observed,
        )
    }

    @Test
    fun `an unknown decision value is read as not asked, not as consent`() = runTest {
        preferences.value.edit { it[AnalyticsConsentDataStore.DECISION] = "SOMETHING_ELSE" }

        assertEquals(AnalyticsConsent.NOT_ASKED, store().read())
    }

    @Test
    fun `the consent file holds only consent keys and no transfer settings`() = runTest {
        store().record(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)

        val stored = preferences.value.data.first().asMap().keys.map(Preferences.Key<*>::name).toSet()

        assertEquals(AnalyticsConsentDataStore.KEYS, stored)
        assertFalse(stored.any { it.contains("transfer") })
        assertFalse(stored.any { it.contains("snooze") })
        assertFalse(stored.any { it.contains("profile") })
    }
}
