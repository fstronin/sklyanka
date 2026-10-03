package ru.stohastika.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.DeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.SaveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ToggleWatchMirrorUseCase
import ru.stohastika.sklyanka.domain.medication.UndoDeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.reminder.RescheduleRemindersUseCase
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.stock.RefillStockUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.feature.meds.list.TakePrnAction
import ru.stohastika.sklyanka.reminder.ReminderPlanReasons
import ru.stohastika.sklyanka.scheduler.IntakePlanningTrigger
import javax.inject.Singleton

/**
 * Сборка карточки лекарства и архива курсов (SKL-10, SKL-11).
 *
 * Сами правила живут в домене и об Hilt не знают: домен не видит ни `javax.inject`,
 * ни `dagger` (НФТ-46), поэтому объекты собираются здесь, а не аннотацией на конструкторе.
 */
@Module
@InstallIn(SingletonComponent::class)
object MedicationCardModule {

    /**
     * Перепланирование одного лекарства — порт домена, реализованный точкой запуска
     * генерации.
     *
     * Именно `IntakePlanningTrigger.planMedication`, а не сам use-case генерации: точка
     * запуска удерживает обязательный порядок «сначала перевод просроченных приёмов, потом
     * генерация окна» (НАП-34). Вслед за приёмами пересобирается очередь будильников —
     * тем же портом, в том же порядке (НФТ-6, НАП-11): после каждого изменения лекарства
     * будильники обязаны совпасть с базой, иначе напоминание о новом курсе не прозвучит
     * до ближайшего запуска приложения. Причина перепланирования берётся из собственного
     * соответствия [ReminderPlanReasons.rescheduleReason]: «добавлено», «изменено»,
     * «в архив» и «из архива» в журнале напоминаний различимы (НАП-14).
     * Домен об этом классе не знает и знать не должен.
     */
    @Provides
    @Singleton
    fun provideMedicationPlanner(
        trigger: IntakePlanningTrigger,
        reschedule: RescheduleRemindersUseCase,
    ): MedicationPlanner = MedicationPlanner { medicationId, reason ->
        val plan = trigger.planMedication(medicationId, reason)
        reschedule(ReminderPlanReasons.rescheduleReason(reason))
        plan
    }

    @Provides
    @Singleton
    fun provideArchiveMedication(
        medications: MedicationRepository,
        planner: MedicationPlanner,
    ): ArchiveMedicationUseCase = ArchiveMedicationUseCase(medications = medications, planner = planner)

    @Provides
    @Singleton
    fun provideRestoreMedication(
        medications: MedicationRepository,
        planner: MedicationPlanner,
        transactor: Transactor,
        time: TimeSource,
        log: OperationLog,
    ): RestoreMedicationUseCase = RestoreMedicationUseCase(
        medications = medications,
        planner = planner,
        transactor = transactor,
        time = time,
        log = log,
    )

    /**
     * Мягкое удаление и возврат удаления (SKL-99, ПМ-24).
     *
     * События аналитики уходят отсюда, а не из экранов: отмена приходит с трёх хостов
     * («Лекарства», «Архив», «Сегодня»), и точка вызова обязана быть одна.
     */
    @Provides
    @Singleton
    fun provideDeleteMedication(
        medications: MedicationRepository,
        intakes: IntakeRepository,
        planner: MedicationPlanner,
        analytics: Analytics,
    ): DeleteMedicationUseCase = DeleteMedicationUseCase(
        medications = medications,
        intakes = intakes,
        planner = planner,
        analytics = analytics,
    )

    @Provides
    @Singleton
    fun provideUndoDeleteMedication(
        medications: MedicationRepository,
        planner: MedicationPlanner,
        analytics: Analytics,
    ): UndoDeleteMedicationUseCase = UndoDeleteMedicationUseCase(
        medications = medications,
        planner = planner,
        analytics = analytics,
    )

    @Provides
    @Singleton
    fun provideToggleWatchMirror(medications: MedicationRepository, log: OperationLog): ToggleWatchMirrorUseCase =
        ToggleWatchMirrorUseCase(medications = medications, log = log)

    /**
     * Пополнение остатка принадлежит SKL-12 и собирается здесь только потому, что
     * карточка лекарства — единственное место, откуда оно вызывается.
     */
    @Provides
    @Singleton
    fun provideRefillStock(medications: MedicationRepository, log: OperationLog): RefillStockUseCase =
        RefillStockUseCase(medications = medications, log = log)

    /**
     * Сохранение лекарства формой (SKL-09).
     *
     * Собирается здесь, потому что раздел «Лекарства» подключается к графу этой задачей:
     * до неё форма не создавалась ни из одного экрана, и связки у сценария не было.
     *
     * Генерация идёт тем же портом [MedicationPlanner], что у архивации и возврата:
     * одно событие — один путь, и будильники пересобираются вслед за приёмами (НАП-11).
     */
    @Provides
    @Singleton
    fun provideSaveMedication(
        medications: MedicationRepository,
        planner: MedicationPlanner,
        transactor: Transactor,
        log: OperationLog,
    ): SaveMedicationUseCase = SaveMedicationUseCase(
        validate = ValidateMedicationDraft(),
        medications = medications,
        planner = planner,
        transactor = transactor,
        log = log,
    )

    /**
     * Кнопка «Принять» в карточке списка «по потребности» (ПР-4).
     *
     * Порт списка лекарств связывается с доменным сценарием SKL-07: двух реализаций одного
     * действия быть не должно, и то же самое создание приёма вызывает экран «Сегодня».
     * Источник — телефон: на часы приёмы по потребности не уходят вовсе (ЧАС-17).
     */
    @Provides
    @Singleton
    fun provideTakePrnAction(createPrnIntake: CreatePrnIntakeUseCase): TakePrnAction =
        TakePrnAction { medicationId -> createPrnIntake(medicationId, IntakeSource.PHONE) }
}
