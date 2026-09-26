package ru.heuri.sklyanka.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.heuri.sklyanka.data.prefs.AlarmRegistryStore
import ru.heuri.sklyanka.data.prefs.ReminderSettingsStore
import ru.heuri.sklyanka.data.reminder.ReminderDataSource
import ru.heuri.sklyanka.data.reminder.RoomReminderJournal
import ru.heuri.sklyanka.data.room.SklyankaDatabase
import ru.heuri.sklyanka.data.room.dao.ReminderDao
import ru.heuri.sklyanka.data.room.dao.ReminderLogDao
import ru.heuri.sklyanka.domain.reminder.port.AlarmRegistry
import ru.heuri.sklyanka.domain.reminder.port.ReminderCardSource
import ru.heuri.sklyanka.domain.reminder.port.ReminderJournal
import ru.heuri.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.heuri.sklyanka.domain.reminder.port.ReminderSettings
import javax.inject.Singleton

/**
 * Хранилище подсистемы напоминаний (SKL-13).
 *
 * Отдельный модуль, а не дополнение к `DataModule` и `DataBindings`: подсистема заводится
 * своей задачей, и её привязки должны быть видны одним файлом — вместе с причиной, почему
 * межпрофильная выборка планировщика законна (НАП-12), а журнал срабатываний живёт
 * отдельно от журнала операций.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReminderDataModule {

    @Provides
    fun provideReminderLogDao(database: SklyankaDatabase): ReminderLogDao = database.reminderLogDao()

    @Provides
    fun provideReminderDao(database: SklyankaDatabase): ReminderDao = database.reminderDao()
}

/** Интерфейсы домена напоминаний соединяются с реализациями слоя данных. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderDataBindings {

    @Binds
    @Singleton
    abstract fun bindReminderJournal(impl: RoomReminderJournal): ReminderJournal

    @Binds
    @Singleton
    abstract fun bindReminderSettings(impl: ReminderSettingsStore): ReminderSettings

    @Binds
    @Singleton
    abstract fun bindAlarmRegistry(impl: AlarmRegistryStore): AlarmRegistry

    @Binds
    @Singleton
    abstract fun bindReminderPlanSource(impl: ReminderDataSource): ReminderPlanSource

    @Binds
    @Singleton
    abstract fun bindReminderCardSource(impl: ReminderDataSource): ReminderCardSource
}
