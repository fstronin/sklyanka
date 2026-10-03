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
import ru.stohastika.sklyanka.analytics.di.ExperimentsModule
import ru.stohastika.sklyanka.data.analytics.AnalyticsConsentPreferences
import ru.stohastika.sklyanka.data.di.DataModule
import ru.stohastika.sklyanka.data.di.DirectBootModule
import ru.stohastika.sklyanka.data.di.DirectBootPreferences
import ru.stohastika.sklyanka.data.experiment.ExperimentCachePreferences
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.data.room.dao.MedicationDao
import ru.stohastika.sklyanka.data.room.dao.OpLogDao
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import ru.stohastika.sklyanka.di.TimeModule
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import ru.stohastika.sklyanka.domain.analytics.AnalyticsSink
import ru.stohastika.sklyanka.domain.analytics.ConsentedAnalytics
import ru.stohastika.sklyanka.domain.experiment.Experiments
import ru.stohastika.sklyanka.domain.experiment.NoOpExperiments
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

    @Provides
    @Singleton
    @ExperimentCachePreferences
    fun provideExperimentCachePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        DeviceTestStores.experimentCache(context)
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

/** Аналитика без вендора: настоящий гейт согласия, записывающий sink (TDD §4.3). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AnalyticsModule::class])
internal object DeviceAnalyticsModule {

    @Provides
    @Singleton
    fun provideAnalyticsLog(): AnalyticsLog = SilentAnalyticsLog

    @Provides
    @Singleton
    fun provideAnalyticsSink(): AnalyticsSink = RecordingAnalyticsSink

    @Provides
    @Singleton
    fun provideAnalytics(sink: AnalyticsSink, log: AnalyticsLog): Analytics =
        ConsentedAnalytics(sink = sink, debuggable = false, log = log)
}

/**
 * Эксперименты в тестовом графе: вариант всегда базовый, вендор не вызывается (SKL-83).
 *
 * Проверкам волны A/B не предмет: базовый вариант — это ровно то, что продукт показывает
 * без эксперимента, и реальный AGC в инструментальных проверках не нужен (НФТ-39).
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ExperimentsModule::class])
internal object DeviceExperimentModule {

    @Provides
    @Singleton
    fun provideExperiments(): Experiments = NoOpExperiments
}
