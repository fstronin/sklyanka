package ru.stohastika.sklyanka.data.onboarding

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

/**
 * Хранилище онбординга: флаги «видел» и трёх подтверждений — и ни одного состояния
 * разрешения (`TDD.md §4`).
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
        assertFalse(store.appStartupConfirmed())
        assertFalse(store.batterySavingConfirmed())
        assertFalse(store.recentsPinConfirmed())
    }

    @Test
    fun `the seen flag survives a new instance over the same file`() = runTest {
        store().markSeen()

        // Новый экземпляр поверх того же файла — это и есть перезапуск приложения.
        assertTrue(store().seen())
    }

    @Test
    fun `each vendor confirmation is written and read back on its own key`() = runTest {
        val store = store()

        store.setAppStartupConfirmed(true)
        store.setBatterySavingConfirmed(true)
        store.setRecentsPinConfirmed(true)

        // Новый экземпляр поверх того же файла: значения действительно сохранены, а не лежат в памяти.
        assertTrue(store().appStartupConfirmed())
        assertTrue(store().batterySavingConfirmed())
        assertTrue(store().recentsPinConfirmed())
    }

    @Test
    fun `a vendor confirmation can be withdrawn`() = runTest {
        val store = store()
        store.setBatterySavingConfirmed(true)

        store.setBatterySavingConfirmed(false)

        assertFalse(store().batterySavingConfirmed())
    }

    @Test
    fun `the legacy autostart confirmation is read as the app-startup confirmation`() = runTest {
        // Обновление поверх сборки, где человек подтвердил единственный тогда автозапуск:
        // его подтверждение не должно пропасть (критерий приёмки 8 чек-листа устройства).
        preferences.value.edit { it[OnboardingStateDataStore.LEGACY_AUTOSTART_CONFIRMED] = true }

        assertTrue(store().appStartupConfirmed())
    }

    @Test
    fun `the new key wins over the legacy one as soon as it is written`() = runTest {
        preferences.value.edit { it[OnboardingStateDataStore.LEGACY_AUTOSTART_CONFIRMED] = true }
        store().setAppStartupConfirmed(false)

        // После первого касания нового ключа legacy перестаёт влиять: «снято» — это «снято».
        assertFalse(store().appStartupConfirmed())
    }

    @Test
    fun `the legacy confirmation never leaks into battery saving or recents`() = runTest {
        preferences.value.edit { it[OnboardingStateDataStore.LEGACY_AUTOSTART_CONFIRMED] = true }

        // До этой задачи энергосбережение и закрепление не подтверждались вовсе —
        // выдумывать им legacy-значение нельзя (TDD §4).
        assertFalse(store().batterySavingConfirmed())
        assertFalse(store().recentsPinConfirmed())
    }

    @Test
    fun `after writing everything it can write, the file holds no permission state`() = runTest {
        val store = store()
        // Legacy-ключ остаётся в файле от прежних сборок; он входит в состав известных ключей.
        preferences.value.edit { it[OnboardingStateDataStore.LEGACY_AUTOSTART_CONFIRMED] = true }
        store.markSeen()
        store.setAppStartupConfirmed(true)
        store.setBatterySavingConfirmed(true)
        store.setRecentsPinConfirmed(true)

        val stored: Set<String> = preferences.value.data.first().asMap().keys.map(Preferences.Key<*>::name).toSet()

        // НФТ-52: состояние разрешения, сохранённое здесь, начало бы врать в момент отзыва.
        assertEquals(OnboardingStateDataStore.KEYS, stored)
        assertFalse(stored.any { it.contains("notification") })
        assertFalse(stored.any { it.contains("exact") })
        assertFalse(stored.any { it.contains("granted") })
    }
}
