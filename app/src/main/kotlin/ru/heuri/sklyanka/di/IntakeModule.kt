package ru.heuri.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.heuri.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.heuri.sklyanka.domain.intake.EndOfDayIntakeWindow
import ru.heuri.sklyanka.domain.intake.IntakeWindow
import ru.heuri.sklyanka.domain.intake.MarkIntakeUseCase
import ru.heuri.sklyanka.domain.intake.MarkSideEffects
import ru.heuri.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.repository.ProfileRepository
import ru.heuri.sklyanka.domain.time.TimeSource
import javax.inject.Singleton

/**
 * Сборка жизненного цикла приёма (SKL-07).
 *
 * Сами сценарии живут в домене и об Hilt не знают: домен не видит ни `javax.inject`,
 * ни `dagger` (НФТ-46), поэтому объекты собираются здесь, а не аннотацией на конструкторе.
 */
@Module
@InstallIn(SingletonComponent::class)
object IntakeModule {

    /**
     * Граница окна `pending → missed` — **единственная точка** решения ОВ-4 на всё
     * приложение (НАП-35).
     *
     * Смена решения заказчика на фиксированный интервал — правка этой строки и новая
     * реализация [IntakeWindow]; ни один переход при этом не трогается.
     */
    @Provides
    @Singleton
    fun provideIntakeWindow(): IntakeWindow = EndOfDayIntakeWindow()

    /**
     * Единственная точка отметки приёма.
     *
     * [MarkSideEffects] подставляется задачей SKL-13: состоявшаяся отметка обязана снять
     * полноэкранное напоминание и отозвать парную карточку на часах (НАП-30, ЧАС-16).
     * Место для этого одно — здесь, иначе четвёртый путь отметки об этом забудет.
     */
    @Provides
    @Singleton
    fun provideMarkIntake(
        intakes: IntakeRepository,
        medications: MedicationRepository,
        profiles: ProfileRepository,
        time: TimeSource,
        sideEffects: MarkSideEffects,
    ): MarkIntakeUseCase = MarkIntakeUseCase(
        intakes = intakes,
        medications = medications,
        profiles = profiles,
        time = time,
        sideEffects = sideEffects,
    )

    @Provides
    @Singleton
    fun provideCreatePrnIntake(
        intakes: IntakeRepository,
        medications: MedicationRepository,
        profiles: ProfileRepository,
        time: TimeSource,
    ): CreatePrnIntakeUseCase = CreatePrnIntakeUseCase(
        intakes = intakes,
        medications = medications,
        profiles = profiles,
        time = time,
    )

    @Provides
    @Singleton
    fun provideSweepMissedIntakes(
        intakes: IntakeRepository,
        window: IntakeWindow,
        time: TimeSource,
        log: OperationLog,
    ): SweepMissedIntakesUseCase = SweepMissedIntakesUseCase(
        intakes = intakes,
        window = window,
        time = time,
        log = log,
    )
}
