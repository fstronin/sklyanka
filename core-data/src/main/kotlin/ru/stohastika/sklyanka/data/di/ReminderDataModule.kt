package ru.stohastika.sklyanka.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.data.prefs.AlarmRegistryStore
import ru.stohastika.sklyanka.data.prefs.DeliveryCheckAcknowledgementsStore
import ru.stohastika.sklyanka.data.prefs.JournalingReminderSettings
import ru.stohastika.sklyanka.data.reminder.ReminderDataSource
import ru.stohastika.sklyanka.data.reminder.RoomDeferredReminders
import ru.stohastika.sklyanka.data.reminder.RoomReminderJournal
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.dao.DeferredReminderDao
import ru.stohastika.sklyanka.data.room.dao.ReminderDao
import ru.stohastika.sklyanka.data.room.dao.ReminderLogDao
import ru.stohastika.sklyanka.domain.reminder.port.AlarmRegistry
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminders
import ru.stohastika.sklyanka.domain.reminder.port.DeliveryCheckAcknowledgements
import ru.stohastika.sklyanka.domain.reminder.port.ReminderCardSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
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

    @Provides
    fun provideDeferredReminderDao(database: SklyankaDatabase): DeferredReminderDao = database.deferredReminderDao()
}

/** Интерфейсы домена напоминаний соединяются с реализациями слоя данных. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderDataBindings {

    @Binds
    @Singleton
    abstract fun bindReminderJournal(impl: RoomReminderJournal): ReminderJournal

    /**
     * Настройки напоминания подставляются обёрткой со следом в журнале (НАП-14).
     *
     * Само хранилище наружу не выдаётся намеренно: изменение настройки обязано оставлять
     * запись при любом пути — из настроек, из онбординга, из загрузки файла переноса, —
     * а привязка прямо на хранилище тихо вернула бы молчаливый путь.
     */
    @Binds
    @Singleton
    abstract fun bindReminderSettings(impl: JournalingReminderSettings): ReminderSettings

    @Binds
    @Singleton
    abstract fun bindAlarmRegistry(impl: AlarmRegistryStore): AlarmRegistry

    @Binds
    @Singleton
    abstract fun bindReminderPlanSource(impl: ReminderDataSource): ReminderPlanSource

    @Binds
    @Singleton
    abstract fun bindReminderCardSource(impl: ReminderDataSource): ReminderCardSource

    /**
     * Отложенные срабатывания хранятся в той же базе, что и данные (SKL-35, ОВ-75).
     *
     * Запись обязана переживать перезагрузку — этим она и отличается от будильника;
     * память о поставленных будильниках ([AlarmRegistry]) для восстановления не годится,
     * потому что после перезагрузки живых намерений нет.
     */
    @Binds
    @Singleton
    abstract fun bindDeferredReminders(impl: RoomDeferredReminders): DeferredReminders

    /**
     * Подтверждение результата самопроверки (SKL-86).
     *
     * Постоянное, а не в памяти процесса: результат проверки не кончается сам, и без
     * записи «объявлено» баннер о вчерашней неудаче возвращался бы при каждом входе.
     */
    @Binds
    @Singleton
    abstract fun bindDeliveryCheckAcknowledgements(
        impl: DeliveryCheckAcknowledgementsStore,
    ): DeliveryCheckAcknowledgements
}
