package ru.stohastika.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.schedule.IntakeGenerationPolicy
import ru.stohastika.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Глубина окна генерации приёмов в сутках (ОВ-8).
 *
 * Именованная зависимость, а не число по месту: значение не согласовано окончательно,
 * и его смена обязана быть правкой одной строки здесь.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IntakeGenerationDepth

/**
 * Сборка планировщика приёмов (SKL-06).
 *
 * Сам сценарий живёт в домене и об Hilt не знает: домен не видит ни `javax.inject`,
 * ни `dagger` (НФТ-46), поэтому объект собирается здесь, а не аннотацией на конструкторе.
 */
@Module
@InstallIn(SingletonComponent::class)
object SchedulingModule {

    /**
     * Семь суток, считая текущие.
     *
     * Обоснование — `docs/archive/tasks/SKL-06/task.md`, «Горизонт генерации»: окно строго длиннее
     * 48-часового окна будильников (НАП-6, НАП-7) и переживает неделю, в которую человек
     * не открывал приложение.
     */
    private const val WINDOW_DEPTH_DAYS = 7

    @Provides
    @Singleton
    @IntakeGenerationDepth
    fun provideWindowDepthDays(): Int = WINDOW_DEPTH_DAYS

    @Provides
    @Singleton
    fun provideGenerationPolicy(@IntakeGenerationDepth depthDays: Int): IntakeGenerationPolicy =
        IntakeGenerationPolicy(windowDepthDays = depthDays)

    @Provides
    @Singleton
    fun providePlanIntakes(
        profiles: ProfileRepository,
        medications: MedicationRepository,
        intakes: IntakeRepository,
        time: TimeSource,
        log: OperationLog,
        policy: IntakeGenerationPolicy,
    ): PlanIntakesUseCase = PlanIntakesUseCase(
        profiles = profiles,
        medications = medications,
        intakes = intakes,
        time = time,
        log = log,
        policy = policy,
    )
}
