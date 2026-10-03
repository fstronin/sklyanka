package ru.stohastika.sklyanka.screen.support

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Хранилища тестового графа (SKL-97, TDD §6.1).
 *
 * DataStore запрещает два активных экземпляра на файл, а Hilt собирает граф на каждую
 * проверку: экземпляры кэшируются по имени файла и живут до конца процесса. Перед каждой
 * проверкой `clearAll` очищает открытые хранилища и удаляет ещё не открытые файлы,
 * поэтому проверка не наследует данные предыдущей — даже после прогона без стирания
 * данных приложения (`--reuse`).
 */
internal object DeviceTestStores {

    private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

    fun settings(context: Context): DataStore<Preferences> = store(context, SETTINGS_NAME)

    fun analyticsConsent(context: Context): DataStore<Preferences> = store(context, CONSENT_NAME)

    fun directBoot(context: Context): DataStore<Preferences> =
        store(context.createDeviceProtectedStorageContext(), DIRECT_BOOT_NAME)

    private fun store(context: Context, name: String): DataStore<Preferences> = stores.getOrPut(name) {
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(name) }
    }

    fun clearAll(context: Context) {
        runBlocking {
            stores.values.forEach { store -> store.edit { preferences -> preferences.clear() } }
            deleteOrphanFiles(context.filesDir)
            deleteOrphanFiles(context.createDeviceProtectedStorageContext().filesDir)
        }
    }

    private fun deleteOrphanFiles(filesDir: File) {
        File(filesDir, DATASTORE_DIR).listFiles().orEmpty()
            .filter { it.name.endsWith(DATASTORE_SUFFIX) }
            .filterNot { it.name.removeSuffix(DATASTORE_SUFFIX) in stores.keys }
            .forEach { it.delete() }
    }

    private const val SETTINGS_NAME = "sklyanka_settings"
    private const val CONSENT_NAME = "sklyanka_analytics_consent"
    private const val DIRECT_BOOT_NAME = "sklyanka_direct_boot"
    private const val DATASTORE_DIR = "datastore"
    private const val DATASTORE_SUFFIX = ".preferences_pb"
}
