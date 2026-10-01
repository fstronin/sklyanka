package ru.stohastika.sklyanka.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Квалификатор DataStore снимка Direct Boot.
 *
 * Отдельная именованная зависимость, а не переиспользование `sklyanka_settings`: настройки
 * и активный профиль лежат в CE-хранилище и в DE не переезжают (ADR-0002). Снимок рождается
 * в DE и живёт там же.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DirectBootPreferences

/**
 * Сборка device-protected хранилища снимка (SKL-87).
 *
 * Файл создаётся от `createDeviceProtectedStorageContext()`: до первой разблокировки CE-файлы
 * закрыты, а этот доступен. `ReplaceFileCorruptionHandler` переводит обрыв записи в пустой
 * снимок — штатную деградацию, а не отказ: следующая пересборка перезапишет файл.
 */
@Module
@InstallIn(SingletonComponent::class)
object DirectBootModule {

    @Provides
    @Singleton
    @DirectBootPreferences
    fun provideDirectBootPreferences(@ApplicationContext context: Context): DataStore<Preferences> {
        val deviceProtectedContext = context.createDeviceProtectedStorageContext()
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) { File(deviceProtectedContext.filesDir, "$DATASTORE_DIR/$FILE_NAME.preferences_pb") }
    }

    private const val DATASTORE_DIR = "datastore"

    /** Имя файла снимка. Отдельное от настроек и базы, чтобы не переносить CE-данные в DE. */
    private const val FILE_NAME = "sklyanka_direct_boot"
}
