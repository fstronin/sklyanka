package ru.stohastika.sklyanka.screen.support.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import ru.stohastika.sklyanka.analytics.di.AnalyticsModule
import ru.stohastika.sklyanka.data.analytics.AnalyticsConsentPreferences
import ru.stohastika.sklyanka.data.di.DataModule
import ru.stohastika.sklyanka.data.di.DirectBootModule
import ru.stohastika.sklyanka.data.di.DirectBootPreferences
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.data.room.dao.MedicationDao
import ru.stohastika.sklyanka.data.room.dao.OpLogDao
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import ru.stohastika.sklyanka.di.TimeModule
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import ru.stohastika.sklyanka.domain.analytics.NoOpAnalytics
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestStores
import javax.inject.Singleton

/**
 * Подмены тестового графа (SKL-97, TDD §4.3).
 *
 * Каждый модуль заменяет ровно один продуктовый: базу и DataStore, DE-снимок будильников,
 * часы и аналитику. Продуктовые экраны, сценарии и мапперы остаются настоящими; вендорский
 * SDK в проверках не активируется.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
internal object DeviceStorageModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SklyankaDatabase =
        Room.inMemoryDatabaseBuilder(context, SklyankaDatabase::class.java).build()

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
        DeviceTestStores.settings(context)

    @Provides
    @Singleton
    @AnalyticsConsentPreferences
    fun provideAnalyticsConsentPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        DeviceTestStores.analyticsConsent(context)
}

/**
 * DE-хранилище снимка будильников: отметка и сохранение касаются снятия будильников,
 * и зеркало не должно переезжать между проверками (TDD §6.1).
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DirectBootModule::class])
internal object DeviceDirectBootModule {

    @Provides
    @Singleton
    @DirectBootPreferences
    fun provideDirectBootPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        DeviceTestStores.directBoot(context)
}

/** Управляемые часы проверки вместо системных (НФТ-48, TDD §5). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [TimeModule::class])
internal object DeviceTimeModule {

    @Provides
    @Singleton
    fun provideTimeSource(): TimeSource = DeviceTestClock.source
}

/** Аналитика без вендора: заглушка NoOp и тихий журнал (SKL-100, ПМ-31). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AnalyticsModule::class])
internal object DeviceAnalyticsModule {

    @Provides
    @Singleton
    fun provideAnalyticsLog(): AnalyticsLog = SilentAnalyticsLog

    @Provides
    @Singleton
    fun provideAnalytics(): Analytics = NoOpAnalytics
}
