package ru.stohastika.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.stock.StockWarningForProfileUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.today.BuildTodayAgenda
import ru.stohastika.sklyanka.domain.today.DenseLayoutRule
import ru.stohastika.sklyanka.domain.today.MarkIntakeGroupUseCase
import ru.stohastika.sklyanka.domain.today.ObserveTodayAgendaUseCase
import javax.inject.Singleton

/**
 * Сборка экрана «Сегодня» (SKL-08).
 *
 * Сами правила живут в домене и об Hilt не знают: домен не видит ни `javax.inject`,
 * ни `dagger` (НФТ-46), поэтому объекты собираются здесь, а не аннотацией на конструкторе.
 */
@Module
@InstallIn(SingletonComponent::class)
object TodayModule {

    /**
     * Порог включения плотного вида — **единственная точка** решения ОВ-33.
     *
     * Значение предложено `docs/norm/04-behavior.md` («больше десяти приёмов в сутки»)
     * и с заказчиком не согласовано. Смена решения — правка этой строки; ни правило,
     * ни разметка при этом не трогаются.
     */
    private const val DENSE_THRESHOLD = DenseLayoutRule.DEFAULT_THRESHOLD

    @Provides
    @Singleton
    fun provideObserveTodayAgenda(
        activeProfile: ActiveProfileRepository,
        intakes: IntakeRepository,
        medications: MedicationRepository,
        sweepMissed: SweepMissedIntakesUseCase,
        time: TimeSource,
        log: OperationLog,
    ): ObserveTodayAgendaUseCase = ObserveTodayAgendaUseCase(
        activeProfile = activeProfile,
        intakes = intakes,
        medications = medications,
        sweepMissed = sweepMissed,
        // Предупреждение о покупке считает SKL-12. Собирается здесь, а не внедряется
        // отдельной связкой, чтобы экран не занимал точку, которая принадлежит задаче
        // остатка: свой модуль она заведёт сама.
        stockWarnings = StockWarningForProfileUseCase(medications, time, log),
        time = time,
        build = BuildTodayAgenda(DenseLayoutRule(DENSE_THRESHOLD)),
    )

    @Provides
    @Singleton
    fun provideMarkIntakeGroup(
        markIntake: MarkIntakeUseCase,
        transactor: Transactor,
        time: TimeSource,
        log: OperationLog,
    ): MarkIntakeGroupUseCase = MarkIntakeGroupUseCase(
        markIntake = markIntake,
        transactor = transactor,
        time = time,
        log = log,
    )
}
