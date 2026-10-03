package ru.stohastika.sklyanka.data.experiment

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.stohastika.sklyanka.domain.experiment.ExperimentId
import ru.stohastika.sklyanka.domain.experiment.VariantKey

/**
 * Кэш вариантов A/B: отдельный файл, снимок целиком и безопасное чтение (SKL-83, TDD §9).
 *
 * Проверяется на JVM с файлом во временном каталоге: `DataStore` для этого не нужен
 * ни `Context`, ни устройство. Отдельный файл — свойство ПЕР-37: перенос данных
 * в него не пишет.
 */
class ExperimentCacheDataStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val preferences = lazy {
        PreferenceDataStoreFactory.create { folder.newFile("experiment-cache.preferences_pb") }
    }

    private fun store() = ExperimentCacheDataStore(preferences.value)

    @Test
    fun `an empty file means no variants`() = runTest {
        assertEquals(emptyMap<ExperimentId, VariantKey>(), store().read())
    }

    @Test
    fun `the snapshot survives a new instance over the same file`() = runTest {
        store().write(mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A))

        // Новый экземпляр поверх того же файла — это и есть перезапуск приложения.
        assertEquals(
            mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A),
            store().read(),
        )
    }

    @Test
    fun `a new snapshot removes an experiment that is no longer in it`() = runTest {
        store().write(
            mapOf(
                ExperimentId.TODAY_EMPTY_STATE to VariantKey.A,
                ExperimentId.SETTINGS_TRANSFER_HINT to VariantKey.A,
            ),
        )

        store().write(mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A))

        assertEquals(mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A), store().read())
    }

    @Test
    fun `an unknown value is read as no variant`() = runTest {
        preferences.value.edit { it[ExperimentCacheDataStore.keyOf(ExperimentId.TODAY_EMPTY_STATE)] = "unknown" }

        assertEquals(emptyMap<ExperimentId, VariantKey>(), store().read())
    }

    @Test
    fun `a variant the experiment does not allow is read as no variant`() = runTest {
        val key = ExperimentCacheDataStore.keyOf(ExperimentId.SETTINGS_TRANSFER_HINT)
        preferences.value.edit { it[key] = "b" }

        assertEquals(emptyMap<ExperimentId, VariantKey>(), store().read())
    }

    @Test
    fun `the cache file holds only experiment keys and no transfer settings`() = runTest {
        store().write(mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A))

        val stored = preferences.value.data.first().asMap().keys.map(Preferences.Key<*>::name).toSet()

        assertEquals(setOf(ExperimentCacheDataStore.keyOf(ExperimentId.TODAY_EMPTY_STATE).name), stored)
        assertTrue(stored.all { it in ExperimentCacheDataStore.KEYS })
        assertFalse(stored.any(::isForeignKey))
    }

    private fun isForeignKey(key: String): Boolean =
        listOf("transfer", "snooze", "profile", "analytics").any { it in key }
}
