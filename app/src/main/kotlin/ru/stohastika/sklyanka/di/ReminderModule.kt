package ru.stohastika.sklyanka.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.stohastika.sklyanka.domain.intake.IntakeWindow
import ru.stohastika.sklyanka.domain.intake.MarkSideEffects
import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.DismissReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.stohastika.sklyanka.domain.reminder.HandleFiredReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.reminder.RecordReminderOutageUseCase
import ru.stohastika.sklyanka.domain.reminder.RescheduleRemindersUseCase
import ru.stohastika.sklyanka.domain.reminder.SnoozeReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.SweepAtDayBoundaryUseCase
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminders
import ru.stohastika.sklyanka.domain.reminder.port.NoWatchGateway
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.ReminderCardSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.WatchGateway
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.transfer.TransferFiles
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActivity
import ru.stohastika.sklyanka.feature.reminder.ReminderCheckNow
import ru.stohastika.sklyanka.feature.reminder.ReminderSetupEntry
import ru.stohastika.sklyanka.platform.notification.ReminderNotifier
import ru.stohastika.sklyanka.platform.permission.SystemPermissionStateReader
import ru.stohastika.sklyanka.platform.reminder.AlarmManagerGateway
import ru.stohastika.sklyanka.reminder.ReminderOutageDetector
import ru.stohastika.sklyanka.reminder.ReminderPlanningTrigger
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Область корутин подсистемы напоминаний.
 *
 * Именованная зависимость, а не `CoroutineScope` по месту: приёмник обязан продолжать
 * работу после возврата из `onReceive`, и область для этого должна переживать сам приёмник.
 * Создавать её в приёмнике значит терять работу вместе с объектом приёмника.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReminderScope

/**
 * Сборка подсистемы напоминаний (SKL-13).
 *
 * Сценарии живут в домене и об Hilt не знают: домен не видит ни `javax.inject`, ни `dagger`
 * (НФТ-46), поэтому объекты собираются здесь, а не аннотацией на конструкторе.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReminderModule {

    @Provides
    @Singleton
    @ReminderScope
    fun provideReminderScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Часы — SKL-16.
     *
     * До неё порт отвечает «часы не настроены»: недоступность часов не является ошибкой
     * сценария, приём отмечается на телефоне (ЧАС-14).
     */
    @Provides
    @Singleton
    fun provideWatchGateway(): WatchGateway = NoWatchGateway

    @Provides
    @Singleton
    fun provideReschedule(
        plan: ReminderPlanSource,
        alarms: AlarmGateway,
        notifications: NotificationGateway,
        journal: ReminderJournal,
        deferred: DeferredReminders,
        sweepMissed: SweepMissedIntakesUseCase,
        time: TimeSource,
    ): RescheduleRemindersUseCase = RescheduleRemindersUseCase(
        plan = plan,
        alarms = alarms,
        notifications = notifications,
        journal = journal,
        deferred = deferred,
        sweepMissed = sweepMissed,
        time = time,
    )

    @Provides
    @Singleton
    fun provideSnooze(
        plan: ReminderPlanSource,
        alarms: AlarmGateway,
        notifications: NotificationGateway,
        journal: ReminderJournal,
        deferred: DeferredReminders,
        time: TimeSource,
    ): SnoozeReminderUseCase = SnoozeReminderUseCase(
        plan = plan,
        alarms = alarms,
        notifications = notifications,
        journal = journal,
        deferred = deferred,
        time = time,
    )

    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun provideHandleFired(
        plan: ReminderPlanSource,
        cards: ReminderCardSource,
        notifications: NotificationGateway,
        watch: WatchGateway,
        alarms: AlarmGateway,
        journal: ReminderJournal,
        deferred: DeferredReminders,
        settings: ReminderSettings,
        window: IntakeWindow,
        time: TimeSource,
    ): HandleFiredReminderUseCase = HandleFiredReminderUseCase(
        plan = plan,
        cards = cards,
        notifications = notifications,
        watch = watch,
        alarms = alarms,
        journal = journal,
        deferred = deferred,
        settings = settings,
        window = window,
        time = time,
    )

    @Provides
    @Singleton
    fun provideDismiss(
        plan: ReminderPlanSource,
        cards: ReminderCardSource,
        notifications: NotificationGateway,
        watch: WatchGateway,
        deferred: DeferredReminders,
        alarms: AlarmGateway,
    ): DismissReminderUseCase = DismissReminderUseCase(
        plan = plan,
        cards = cards,
        notifications = notifications,
        watch = watch,
        deferred = deferred,
        alarms = alarms,
    )

    /**
     * Последствия отметки приёма (НАП-30, ЧАС-16).
     *
     * Подставляются в единственную точку отметки, поэтому карточка закрывается одинаково
     * при отметке с экрана «Сегодня», с карточки напоминания и с часов.
     */
    @Provides
    @Singleton
    fun provideMarkSideEffects(dismiss: DismissReminderUseCase): MarkSideEffects =
        MarkSideEffects { intakeId -> dismiss(intakeId) }

    /**
     * «Проверить сейчас» для экрана диагностики (НАП-37).
     *
     * Экран получает операцию, а не планировщик: порядок «сначала приёмы, потом будильники»
     * знает один класс в app, и знать его экрану незачем.
     */
    @Provides
    @Singleton
    fun provideCheckNow(trigger: ReminderPlanningTrigger): ReminderCheckNow =
        ReminderCheckNow { reason -> trigger.planAll(reason) }

    @Provides
    @Singleton
    fun provideReliability(permissions: PermissionStateReader): ReadReminderReliabilityUseCase =
        ReadReminderReliabilityUseCase(permissions)

    /**
     * Проход по просроченным приёмам на границе суток (НАП-34).
     *
     * Единственная операция подсистемы, срабатывающая по времени: пока телефон включён
     * и приложение не открывают, неотмеченный приём обязан стать пропущенным в полночь,
     * а его карточка — исчезнуть из шторки.
     */
    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun provideDayBoundarySweep(
        sweepMissed: SweepMissedIntakesUseCase,
        plan: ReminderPlanSource,
        notifications: NotificationGateway,
        alarms: AlarmGateway,
        journal: ReminderJournal,
        time: TimeSource,
    ): SweepAtDayBoundaryUseCase = SweepAtDayBoundaryUseCase(
        sweepMissed = sweepMissed,
        plan = plan,
        notifications = notifications,
        alarms = alarms,
        journal = journal,
        time = time,
    )

    /**
     * Обнаружение перерыва в напоминаниях при запуске (НАП-38).
     *
     * Вызывается из [ru.stohastika.sklyanka.SklyankaApp] после пересборки окна: признак перерыва
     * вычисляется по журналу и плану, а не по сохранённому флагу, поэтому сначала должен
     * появиться актуальный план.
     */
    @Provides
    @Singleton
    fun provideRecordOutage(
        plan: ReminderPlanSource,
        journal: ReminderJournal,
        time: TimeSource,
    ): RecordReminderOutageUseCase = RecordReminderOutageUseCase(plan = plan, journal = journal, time = time)

    /**
     * Обнаружение перерыва для объявления при входе (НАП-38).
     *
     * Держатель объявления получает операцию, а не сценарий: ему нужен исход, а порядок
     * «сначала план, потом признак» знает тот, кто зовёт пересборку.
     */
    @Provides
    @Singleton
    fun provideOutageDetector(record: RecordReminderOutageUseCase): ReminderOutageDetector =
        ReminderOutageDetector { record() }

    /**
     * Выгрузка журнала срабатываний файлом (НФТ-51).
     *
     * Файловая часть — тот же [TransferFiles], что у переноса данных: системный выбор
     * места, запись целиком и снятие цели при срыве уже реализованы там, и второй путь
     * записи файла означал бы второй набор ошибок в том же месте.
     */
    @Provides
    @Singleton
    fun provideJournalExport(
        journal: ReminderJournal,
        files: TransferFiles,
        time: TimeSource,
    ): ExportReminderJournalUseCase = ExportReminderJournalUseCase(journal = journal, files = files, time = time)

    /**
     * Куда ведёт «Пройти настройку заново» с экрана диагностики (НАП-21).
     *
     * Решать это обязан `app`: `feature-reminder` не зависит от `feature-onboarding`
     * и о существовании экрана разрешений не знает (`ADR-0007`).
     */
    @Provides
    @Singleton
    fun provideReminderSetupEntry(): ReminderSetupEntry =
        ReminderSetupEntry { context -> PermissionsActivity.intentFor(context) }

    @Provides
    @Singleton
    fun provideDiagnostics(
        journal: ReminderJournal,
        alarms: AlarmGateway,
        permissions: PermissionStateReader,
        plan: ReminderPlanSource,
        reliability: ReadReminderReliabilityUseCase,
        time: TimeSource,
    ): BuildReminderDiagnosticsUseCase = BuildReminderDiagnosticsUseCase(
        journal = journal,
        alarms = alarms,
        permissions = permissions,
        plan = plan,
        reliability = reliability,
        time = time,
    )
}

/** Порты домена соединяются с платформенными реализациями. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderBindings {

    @Binds
    @Singleton
    abstract fun bindAlarmGateway(impl: AlarmManagerGateway): AlarmGateway

    @Binds
    @Singleton
    abstract fun bindNotificationGateway(impl: ReminderNotifier): NotificationGateway

    @Binds
    @Singleton
    abstract fun bindPermissionStateReader(impl: SystemPermissionStateReader): PermissionStateReader
}
