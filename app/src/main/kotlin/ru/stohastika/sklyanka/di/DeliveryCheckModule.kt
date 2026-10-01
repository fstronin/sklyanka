package ru.stohastika.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.reminder.AcknowledgeDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.CancelDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.FinishDeliveryCheckOnEntryUseCase
import ru.stohastika.sklyanka.domain.reminder.HandleFiredDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.StartDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.AppForeground
import ru.stohastika.sklyanka.domain.reminder.port.DeliveryCheckAcknowledgements
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.feature.onboarding.DeliveryCheckEntry
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsActivity
import javax.inject.Singleton

/**
 * Сборка самопроверки доставки (SKL-86, ПМ-21).
 *
 * Отдельный модуль, а не дополнение к [ReminderModule]: проверка — не приём, у неё свой
 * набор сценариев и своя точка входа из онбординга, и её привязки должны читаться одним
 * файлом. Сценарии домена о Hilt не знают (НФТ-46).
 */
@Module
@InstallIn(SingletonComponent::class)
object DeliveryCheckModule {

    @Provides
    @Singleton
    fun provideStartDeliveryCheck(
        alarms: AlarmGateway,
        journal: ReminderJournal,
        time: TimeSource,
    ): StartDeliveryCheckUseCase = StartDeliveryCheckUseCase(alarms = alarms, journal = journal, time = time)

    @Provides
    @Singleton
    fun provideHandleFiredDeliveryCheck(
        journal: ReminderJournal,
        notifications: NotificationGateway,
        foreground: AppForeground,
        time: TimeSource,
    ): HandleFiredDeliveryCheckUseCase = HandleFiredDeliveryCheckUseCase(
        journal = journal,
        notifications = notifications,
        foreground = foreground,
        time = time,
    )

    @Provides
    @Singleton
    fun provideFinishDeliveryCheck(
        journal: ReminderJournal,
        alarms: AlarmGateway,
        time: TimeSource,
    ): FinishDeliveryCheckOnEntryUseCase = FinishDeliveryCheckOnEntryUseCase(
        journal = journal,
        alarms = alarms,
        time = time,
    )

    @Provides
    @Singleton
    fun provideCancelDeliveryCheck(
        alarms: AlarmGateway,
        journal: ReminderJournal,
        time: TimeSource,
    ): CancelDeliveryCheckUseCase = CancelDeliveryCheckUseCase(alarms = alarms, journal = journal, time = time)

    @Provides
    @Singleton
    fun provideAcknowledgeDeliveryCheck(
        acknowledgements: DeliveryCheckAcknowledgements,
    ): AcknowledgeDeliveryCheckUseCase = AcknowledgeDeliveryCheckUseCase(acknowledgements)

    /**
     * Переход из плашки «Всё настроено» на самопроверку доставки (ОВ-108).
     *
     * Решает `app`: у экрана проверки напоминаний один дом, и второй его копии
     * в онбординге не заводится. Вход открывает «Проверку напоминаний» с уже поднятой
     * шторкой — запуск остаётся одним нажатием.
     */
    @Provides
    @Singleton
    fun provideDeliveryCheckEntry(): DeliveryCheckEntry = DeliveryCheckEntry { context ->
        DiagnosticsActivity.intentFor(context, openDeliveryCheck = true)
    }
}
