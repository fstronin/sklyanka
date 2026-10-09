package ru.stohastika.sklyanka.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.BuildDoctorReportUseCase
import ru.stohastika.sklyanka.domain.history.DoctorReportTransfer
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.ObserveAdherenceUseCase
import ru.stohastika.sklyanka.domain.history.OnTimeRule
import ru.stohastika.sklyanka.domain.history.TransferDoctorReportUseCase
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.feature.history.DoctorReportRenderer
import ru.stohastika.sklyanka.feature.history.PdfDoctorReportRenderer
import ru.stohastika.sklyanka.platform.report.SystemDoctorReportTransfer
import javax.inject.Singleton

/**
 * Сборка экрана истории и отчёта врачу (SKL-18).
 *
 * Сами правила живут в домене и об Hilt не знают: домен не видит ни `javax.inject`,
 * ни `dagger` (НФТ-46), поэтому объекты собираются здесь. Платформенная часть —
 * системный обмен и системный выбор места — подставляется реализацией из `app`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryModule {

    @Binds
    @Singleton
    abstract fun bindDoctorReportTransfer(impl: SystemDoctorReportTransfer): DoctorReportTransfer

    /**
     * Отрисовка отчёта.
     *
     * PDF — подтверждённый дизайном формат (ОВ-15). Замена отрисовки — ровно эта
     * строка: ни расчёт, ни экран формата не знают.
     */
    @Binds
    @Singleton
    abstract fun bindDoctorReportRenderer(impl: PdfDoctorReportRenderer): DoctorReportRenderer

    companion object {

        /**
         * Порог «вовремя» (Р-21): окно ±2 часа вокруг назначенного времени.
         *
         * Значение объявлено в домене ([OnTimeRule.TwoHours]) и здесь только подставляется:
         * окно — часть смысла метрики, а не настройка сборки, и стенды приёмки обязаны
         * брать то же правило, а не его пересказ. Расчёт приверженности правило принимает
         * параметром и часов не считает.
         */
        @Provides
        @Singleton
        fun provideOnTimeRule(): OnTimeRule.Agreed = OnTimeRule.TwoHours

        /**
         * Расчёт приверженности.
         *
         * Порог низкой приверженности здесь не задаётся: значение по умолчанию выведено
         * из макета и живёт рядом с расчётом, чтобы обоснование было видно вместе с ним.
         */
        @Provides
        @Singleton
        fun provideAdherenceCalculator(onTimeRule: OnTimeRule.Agreed): AdherenceCalculator =
            AdherenceCalculator(onTimeRule = onTimeRule)

        /**
         * Правило строки-вывода со значениями по умолчанию.
         *
         * Порог «явного лидера» согласовывается с заказчиком (открытый вопрос 7 задачи);
         * до ответа действуют значения, при которых строка не появляется на шуме.
         */
        @Provides
        @Singleton
        fun provideMissedPatternRule(): MissedPatternRule = MissedPatternRule()

        @Provides
        @Singleton
        fun provideObserveAdherence(
            activeProfile: ActiveProfileRepository,
            history: HistoryRepository,
            calculator: AdherenceCalculator,
            time: TimeSource,
            log: OperationLog,
        ): ObserveAdherenceUseCase = ObserveAdherenceUseCase(
            activeProfile = activeProfile,
            history = history,
            calculator = calculator,
            time = time,
            log = log,
        )

        @Provides
        @Singleton
        @Suppress("LongParameterList")
        fun provideBuildDoctorReport(
            activeProfile: ActiveProfileRepository,
            profiles: ProfileRepository,
            history: HistoryRepository,
            calculator: AdherenceCalculator,
            time: TimeSource,
            log: OperationLog,
        ): BuildDoctorReportUseCase = BuildDoctorReportUseCase(
            activeProfile = activeProfile,
            profiles = profiles,
            history = history,
            calculator = calculator,
            time = time,
            log = log,
        )

        @Provides
        @Singleton
        fun provideTransferDoctorReport(
            transfer: DoctorReportTransfer,
            activeProfile: ActiveProfileRepository,
            log: OperationLog,
        ): TransferDoctorReportUseCase = TransferDoctorReportUseCase(
            transfer = transfer,
            activeProfile = activeProfile,
            log = log,
        )
    }
}
