package ru.stohastika.sklyanka.screen.support.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import ru.stohastika.sklyanka.data.di.DataModule
import ru.stohastika.sklyanka.data.di.DirectBootModule
import ru.stohastika.sklyanka.data.di.DirectBootPreferences
import ru.stohastika.sklyanka.data.room.DatabaseUpgrade
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.dao.AnalyticsDao
import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.data.room.dao.MedicationDao
import ru.stohastika.sklyanka.data.room.dao.OpLogDao
import ru.stohastika.sklyanka.data.room.dao.ProfileDao
import ru.stohastika.sklyanka.di.TimeModule
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.screen.support.DeviceJourneyStorage
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestStores
import javax.inject.Singleton

/**
 * Подмены тестового графа (SKL-97, TDD §4.3).
 *
 * Каждый модуль заменяет ровно один продуктовый: базу и DataStore, DE-снимок будильников
 * и часы. Продуктовые экраны, сценарии и мапперы остаются настоящими.
 *
 * База — память для экранной проверки и настоящий файл для похода (SKL-140): что именно
 * открыть, решает `DeviceJourneyStorage` по аргументу инструментовки.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
internal object DeviceStorageModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SklyankaDatabase = DeviceJourneyStorage.open(context)

    /**
     * Подготовка базы в проверке устройства.
     *
     * База здесь в памяти, и мигрировать нечего: файла у неё нет, копии и отказа быть
     * не может — решение выходит рабочим. Настоящая копия перед миграцией проверяется там,
     * где база настоящая: инструментальными проверками `core-data` и стендом приёмки
     * `SKL-94.feature`.
     */
    @Provides
    @Singleton
    fun provideDatabaseUpgrade(@ApplicationContext context: Context, time: TimeSource): DatabaseUpgrade =
        DatabaseUpgrade(context, time)

    @Provides
    fun provideProfileDao(database: SklyankaDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideMedicationDao(database: SklyankaDatabase): MedicationDao = database.medicationDao()

    @Provides
    fun provideIntakeDao(database: SklyankaDatabase): IntakeDao = database.intakeDao()

    @Provides
    fun provideOpLogDao(database: SklyankaDatabase): OpLogDao = database.opLogDao()

    /**
     * Очередь событий аналитики (SKL-165).
     *
     * Модуль заменяет `DataModule` целиком (`replaces`), поэтому каждый его провайдер
     * обязан быть здесь: иначе очередь аналитики и всё, что от неё зависит, выпадает
     * из графа проверок устройства — ровно так это и всплыло первым прогоном `verifyAll`.
     */
    @Provides
    fun provideAnalyticsDao(database: SklyankaDatabase): AnalyticsDao = database.analyticsDao()

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        DeviceTestStores.settings(context)
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
