package ru.heuri.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.heuri.sklyanka.domain.Transactor
import ru.heuri.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.heuri.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.heuri.sklyanka.domain.medication.SaveMedicationUseCase
import ru.heuri.sklyanka.domain.medication.ToggleWatchMirrorUseCase
import ru.heuri.sklyanka.domain.medication.ValidateMedicationDraft
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.schedule.MedicationPlanner
import ru.heuri.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.heuri.sklyanka.domain.stock.RefillStockUseCase
import ru.heuri.sklyanka.domain.time.TimeSource
import ru.heuri.sklyanka.feature.meds.list.TakePrnAction
import ru.heuri.sklyanka.scheduler.IntakePlanningTrigger
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
     * генерация окна» (НАП-34) и станет местом постановки будильников, когда та появится
     * (SKL-13). Домен об этом классе не знает и знать не должен.
     */
    @Provides
    @Singleton
    fun provideMedicationPlanner(trigger: IntakePlanningTrigger): MedicationPlanner =
        MedicationPlanner { medicationId, reason -> trigger.planMedication(medicationId, reason) }

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
     */
    @Provides
    @Singleton
    fun provideSaveMedication(
        medications: MedicationRepository,
        planIntakes: PlanIntakesUseCase,
        transactor: Transactor,
        log: OperationLog,
    ): SaveMedicationUseCase = SaveMedicationUseCase(
        validate = ValidateMedicationDraft(),
        medications = medications,
        planIntakes = planIntakes,
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
