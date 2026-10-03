package ru.stohastika.sklyanka.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.data.analytics.AnalyticsConsentPreferences
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.SklyankaMigrations
import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.data.room.dao.MedicationDao
import ru.stohastika.sklyanka.data.room.dao.OpLogDao
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import javax.inject.Singleton

/**
 * Сборка хранилища.
 *
 * Разрушающая миграция здесь не вызывается и не появится: она запрещена во всех
 * сборках, включая отладочную (МГ-2). Статических держателей базы и синглтонов
 * «по месту» нет — всё приходит через граф.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SklyankaDatabase {
        val builder = Room.databaseBuilder(context, SklyankaDatabase::class.java, SklyankaDatabase.NAME)
        SklyankaMigrations.ALL.forEach { builder.addMigrations(it) }
        return builder.build()
    }

    @Provides
    fun provideProfileDao(database: SklyankaDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideMedicationDao(database: SklyankaDatabase): MedicationDao = database.medicationDao()

    @Provides
    fun provideIntakeDao(database: SklyankaDatabase): IntakeDao = database.intakeDao()

    @Provides
    fun provideOpLogDao(database: SklyankaDatabase): OpLogDao = database.opLogDao()

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(SETTINGS_NAME)
        }

    /**
     * Файл согласия на аналитику — отдельно от настроек (SKL-82, ПЕР-37).
     *
     * Согласие принадлежит установке и не участвует в переносе данных; отдельный файл
     * делает это свойством хранилища, а не обещанием.
     */
    @Provides
    @Singleton
    @AnalyticsConsentPreferences
    fun provideAnalyticsConsentPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(ANALYTICS_CONSENT_NAME)
        }

    /** Имя файла постоянных настроек. Активный профиль живёт здесь, а не в памяти процесса (ПРФ-17). */
    private const val SETTINGS_NAME = "sklyanka_settings"

    /** Имя файла согласия на аналитику. */
    private const val ANALYTICS_CONSENT_NAME = "sklyanka_analytics_consent"
}
