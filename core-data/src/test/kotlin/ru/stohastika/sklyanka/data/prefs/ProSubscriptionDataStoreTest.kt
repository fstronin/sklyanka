package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import java.io.File
import java.time.LocalDate

/**
 * Кэш состояния подписки: последний ответ магазина переживает перезапуск (`ADR-0015` п. 1–2).
 *
 * Проверка держит две вещи, ради которых кэш вообще существует: ответ магазина не теряется
 * между запусками и испорченная или незнакомая запись не отнимает доступ — подписка
 * не данные человека, и ронять из-за неё запуск приложения нельзя.
 */
class ProSubscriptionDataStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val november = LocalDate.of(2026, 11, 13)

    private fun preferences(file: File = folder.newFile("pro.preferences_pb")): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { file }

    private fun store(preferences: DataStore<Preferences>) = ProSubscriptionDataStore(preferences)

    @Test
    fun `before the first check there is no answer at all`() = runTest {
        assertNull(store(preferences()).read())
    }

    @Test
    fun `the store answer survives a restart of the store object`() = runTest {
        val file = folder.newFile("persistent.preferences_pb")
        val firstRun = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        ProSubscriptionDataStore(
            PreferenceDataStoreFactory.create(scope = firstRun) { file },
        ).write(ProSubscription.Active(ProProductId.YEARLY, november))
        // Первый DataStore закрывается перед вторым: на один файл не может быть двух живых
        // хранилищ, и «перезапуск» здесь — именно новый объект на том же файле.
        firstRun.cancel()

        assertEquals(ProSubscription.Active(ProProductId.YEARLY, november), store(preferences(file)).read())
    }

    @Test
    fun `an answer without a named product keeps the access and loses only the price`() = runTest {
        val cache = store(preferences())

        cache.write(ProSubscription.Active(null, november))

        assertEquals(ProSubscription.Active(null, november), cache.read())
    }

    @Test
    fun `the store answering that there is no purchase is written over the previous answer`() = runTest {
        val cache = store(preferences())
        cache.write(ProSubscription.Active(ProProductId.MONTHLY, november))

        cache.write(ProSubscription.None)

        assertEquals(ProSubscription.None, cache.read())
    }

    @Test
    fun `a product id the product no longer knows keeps the access without the price`() = runTest {
        val data = preferences()
        data.edit { mutable -> mutable[SUBSCRIPTION] = "active|sklyanka.pro.lifetime|${november.toEpochDay()}" }

        assertEquals(ProSubscription.Active(null, november), store(data).read())
    }

    @Test
    fun `a broken record reads as if the store had never answered`() = runTest {
        val data = preferences()
        data.edit { mutable -> mutable[SUBSCRIPTION] = "active|broken" }

        assertNull(store(data).read())
    }

    private companion object {
        val SUBSCRIPTION = stringPreferencesKey("pro_subscription")
    }
}
