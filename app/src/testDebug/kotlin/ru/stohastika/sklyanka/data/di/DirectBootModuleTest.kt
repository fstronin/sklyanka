package ru.stohastika.sklyanka.data.di

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Расположение файла снимка Direct Boot (SKL-87).
 *
 * `preferencesDataStoreFile()` строит путь от `applicationContext.filesDir`: у device-protected
 * контекста `applicationContext` возвращает CE-приложение, и файл молча уезжает в CE. Здесь
 * проверяется, что провайдер строит файл от `filesDir` device-protected контекста, иначе
 * в locked-фазе приёмник читает пустой DE-стор и будильники не восстанавливаются.
 */
@RunWith(AndroidJUnit4::class)
// Пустое приложение вместо продуктового: граф Hilt при старте сам открывает DE-стор,
// и второй DataStore поверх того же файла запрещён.
@Config(application = Application::class)
class DirectBootModuleTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the snapshot file is created in device protected storage`() = runTest {
        val deviceProtected = context.createDeviceProtectedStorageContext()
        val store = DirectBootModule.provideDirectBootPreferences(context)

        store.edit { it[intPreferencesKey("probe")] = 1 }

        val expected = File(deviceProtected.filesDir, "datastore/sklyanka_direct_boot.preferences_pb")
        assertTrue("snapshot must be in device-protected storage: $expected", expected.isFile)
        assertFalse(
            "snapshot must not fall back to credential-protected storage",
            File(context.filesDir, "datastore/sklyanka_direct_boot.preferences_pb").exists(),
        )
    }
}
