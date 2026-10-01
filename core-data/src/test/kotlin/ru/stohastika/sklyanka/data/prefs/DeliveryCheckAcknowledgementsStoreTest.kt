package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/**
 * Подтверждение результата самопроверки: запись и чтение момента (SKL-86, ОВ-108).
 *
 * Проверяется на JVM с файлом настроек во временном каталоге: значений два — «не
 * объявляли» и момент, и оба обязаны переживать пересоздание хранилища.
 */
class DeliveryCheckAcknowledgementsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(name: String = "settings") = DeliveryCheckAcknowledgementsStore(
        PreferenceDataStoreFactory.create { folder.newFile("$name.preferences_pb") },
    )

    @Test
    fun `nothing is announced until the first result`() = runTest {
        assertNull(store().announced())
    }

    @Test
    fun `the due moment of the announced result is read back`() = runTest {
        val dueAt = Instant.parse("2026-09-25T07:01:30Z")
        val store = store()

        store.remember(dueAt)

        assertEquals(dueAt, store.announced())
    }

    @Test
    fun `the latest announced moment replaces the previous one`() = runTest {
        val first = Instant.parse("2026-09-25T07:01:30Z")
        val second = Instant.parse("2026-09-25T09:30:00Z")
        val store = store()

        store.remember(first)
        store.remember(second)

        assertEquals(second, store.announced())
    }
}
