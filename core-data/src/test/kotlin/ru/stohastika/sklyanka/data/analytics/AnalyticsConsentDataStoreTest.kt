package ru.stohastika.sklyanka.data.analytics

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState

/**
 * Решение об аналитике (ПМ-25, TDD §3.1): отсутствие ключа — «ещё не спрашивали»,
 * отказ и согласие переживают перезапуск, испорченная запись не включает отправку.
 *
 * Ключ проверяется литералом намеренно: `analytics_consent` — внешний формат файла настроек,
 * и тест обязан заметить, если он поедет.
 */
class AnalyticsConsentDataStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val preferences = lazy {
        PreferenceDataStoreFactory.create { folder.newFile("analytics.preferences_pb") }
    }

    private fun store() = AnalyticsConsentDataStore(preferences.value)

    @Test
    fun `an untouched installation has not been asked`() = runTest {
        assertEquals(AnalyticsConsentState.NOT_ASKED, store().consent.first())
    }

    @Test
    fun `approval is stored as allowed and read back`() = runTest {
        store().decide(AnalyticsConsentState.ALLOWED)

        assertEquals(AnalyticsConsentState.ALLOWED, store().consent.first())
        assertEquals("allowed", storedValue())
    }

    @Test
    fun `refusal survives a restart and is read as declined`() = runTest {
        store().decide(AnalyticsConsentState.DECLINED)

        // Новый экземпляр поверх того же файла — это и есть перезапуск приложения.
        assertEquals(AnalyticsConsentState.DECLINED, store().consent.first())
        assertEquals("declined", storedValue())
    }

    @Test
    fun `a broken value reads as not asked`() = runTest {
        preferences.value.edit { it[stringPreferencesKey("analytics_consent")] = "perhaps" }

        assertEquals(AnalyticsConsentState.NOT_ASKED, store().consent.first())
    }

    @Test
    fun `the store writes only its own key`() = runTest {
        store().decide(AnalyticsConsentState.ALLOWED)

        val stored = preferences.value.data.first().asMap().keys.map { it.name }.toSet()

        assertEquals(setOf("analytics_consent"), stored)
    }

    @Test
    fun `a missing decision cannot be written back`() = runTest {
        try {
            store().decide(AnalyticsConsentState.NOT_ASKED)
            fail("a decline must not be erasable back into the unasked state")
        } catch (_: IllegalArgumentException) {
            assertEquals(AnalyticsConsentState.NOT_ASKED, store().consent.first())
        }
    }

    /**
     * Строка настроек меняется без перезахода на экран: поток того же экземпляра обязан
     * отдать новое решение, а не прочитанное при создании.
     */
    @Test
    fun `the flow reports a new decision without a restart`() = runBlocking {
        val store = store()
        val emissions = Channel<AnalyticsConsentState>(Channel.UNLIMITED)
        val collector = launch { store.consent.collect { emissions.send(it) } }

        assertEquals(AnalyticsConsentState.NOT_ASKED, withTimeout(TIMEOUT_MILLIS) { emissions.receive() })

        store.decide(AnalyticsConsentState.ALLOWED)
        assertEquals(AnalyticsConsentState.ALLOWED, withTimeout(TIMEOUT_MILLIS) { emissions.receive() })

        store.decide(AnalyticsConsentState.DECLINED)
        assertEquals(AnalyticsConsentState.DECLINED, withTimeout(TIMEOUT_MILLIS) { emissions.receive() })

        collector.cancel()
    }

    private suspend fun storedValue(): String? =
        preferences.value.data.first()[stringPreferencesKey("analytics_consent")]

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
