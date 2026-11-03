package ru.stohastika.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.analytics.DecideAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.ObserveAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.ShouldShowAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsQueue
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsTransport
import ru.stohastika.sklyanka.domain.time.TimeSource
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Область корутин аналитики (SKL-165, TDD §5.7).
 *
 * Именованная зависимость: запись события не блокирует вызывающего, а порядок записей
 * и отправок должен быть один. Параллельность ограничена единицей — два прохода
 * `drain` одновременно вытащили бы одну и ту же пачку и отправили её дважды.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnalyticsScope

/**
 * Сборка аналитики в composition root (SKL-165, TDD §2.3, §5.7).
 *
 * Сценарии живут в домене и о Hilt не знают (НФТ-46), адаптер поставщика собирается
 * в `core-analytics`; здесь — только то, что связывает их с приложением. Ни один экран
 * и ни одна ViewModel не видят `ru.stohastika.sklyanka.analytics.*`: они получают
 * доменный [AnalyticsRecorder] и решают продуктивное поведение, а не способ доставки.
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    /**
     * Последовательная область записи и отправки.
     *
     * Обработчика отказов здесь нет намеренно: сбой аналитики не должен завершать процесс
     * и не должен показываться человеку (АН-5). `quietly` в рекордере глотает отказы,
     * а `SupervisorJob` не даёт одной упавшей записи снять остальные.
     */
    @Provides
    @Singleton
    @AnalyticsScope
    fun provideAnalyticsScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Шов записи: гейт согласия, очередь и отправка в одном месте (TDD §7). */
    @Provides
    @Singleton
    fun provideAnalyticsRecorder(
        consentStore: AnalyticsConsentStore,
        queue: AnalyticsQueue,
        transport: AnalyticsTransport,
        time: TimeSource,
        @AnalyticsScope scope: CoroutineScope,
    ): AnalyticsRecorder = AnalyticsRecorder(
        consentStore = consentStore,
        queue = queue,
        transport = transport,
        time = time,
        scope = scope,
    )

    /** Показывать ли экран согласия: решение есть — экран не показывается (АН-2). */
    @Provides
    @Singleton
    fun provideShouldShowAnalyticsConsent(store: AnalyticsConsentStore): ShouldShowAnalyticsConsentUseCase =
        ShouldShowAnalyticsConsentUseCase(store)

    /** Текущее решение потоком — строка настроек меняется без перезахода на экран. */
    @Provides
    @Singleton
    fun provideObserveAnalyticsConsent(store: AnalyticsConsentStore): ObserveAnalyticsConsentUseCase =
        ObserveAnalyticsConsentUseCase(store)

    /** Единственное место, где решение превращается в записи и отправку (TDD §7.4). */
    @Provides
    @Singleton
    fun provideDecideAnalyticsConsent(
        store: AnalyticsConsentStore,
        recorder: AnalyticsRecorder,
    ): DecideAnalyticsConsentUseCase = DecideAnalyticsConsentUseCase(store, recorder)
}
