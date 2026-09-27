package ru.heuri.sklyanka.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.heuri.sklyanka.domain.history.AdherenceCalculator
import ru.heuri.sklyanka.domain.history.BuildDoctorReportUseCase
import ru.heuri.sklyanka.domain.history.DoctorReportTransfer
import ru.heuri.sklyanka.domain.history.MissedPatternRule
import ru.heuri.sklyanka.domain.history.ObserveAdherenceUseCase
import ru.heuri.sklyanka.domain.history.OnTimeRule
import ru.heuri.sklyanka.domain.history.TransferDoctorReportUseCase
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.repository.ActiveProfileRepository
import ru.heuri.sklyanka.domain.repository.HistoryRepository
import ru.heuri.sklyanka.domain.repository.ProfileRepository
import ru.heuri.sklyanka.domain.time.TimeSource
import ru.heuri.sklyanka.feature.history.DoctorReportRenderer
import ru.heuri.sklyanka.feature.history.TextDoctorReportRenderer
import ru.heuri.sklyanka.platform.report.SystemDoctorReportTransfer
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
     * Текстовый документ — пока формат не подтверждён (ОВ-15). Подтверждённый формат
     * подставляется здесь: ни расчёт, ни экран этого не заметят.
     */
    @Binds
    @Singleton
    abstract fun bindDoctorReportRenderer(impl: TextDoctorReportRenderer): DoctorReportRenderer

    companion object {

        /**
         * Порог «вовремя» — единственная строка, которой отвечают на ОВ-4.
         *
         * Правило не зарегистрировано: граница окна перехода «ожидается → пропущено»
         * выбрана «до конца суток» (SKL-07, `EndOfDayIntakeWindow`), и при ней «вовремя»
         * совпадает с «принято» — метрика вырождается в долю отмеченных и перестаёт
         * отличаться от приверженности. Плитка «принято вовремя» показывает прочерк.
         *
         * Это проектное решение, а не заглушка: выдуманное число человек покажет врачу.
         * Когда заказчик согласует порог, здесь появляется `OnTimeRule.Agreed { … }` —
         * расчёт при этом не переделывается.
         */
        @Provides
        @Singleton
        fun provideOnTimeRule(): OnTimeRule = OnTimeRule.NotAgreed

        /**
         * Расчёт приверженности.
         *
         * Порог низкой приверженности здесь не задаётся: значение по умолчанию выведено
         * из макета и живёт рядом с расчётом, чтобы обоснование было видно вместе с ним.
         */
        @Provides
        @Singleton
        fun provideAdherenceCalculator(onTimeRule: OnTimeRule): AdherenceCalculator =
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
