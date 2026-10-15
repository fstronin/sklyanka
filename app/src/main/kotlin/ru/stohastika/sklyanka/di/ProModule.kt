package ru.stohastika.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.pro.ProSheetSession
import ru.stohastika.sklyanka.domain.pro.ProStore
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionCache
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionRefreshDueUseCase
import ru.stohastika.sklyanka.domain.pro.RefreshProSubscriptionUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import javax.inject.Singleton

/**
 * Сборка платного контура «Склянки Про» (SKL-122, `ADR-0010` п. 2, `ADR-0015`).
 *
 * Домен о Hilt не знает (НФТ-46), поэтому сессия шторки, проверка подписки и чтение
 * состояния собираются здесь. Реализация порта [ProStore] приезжает из модуля контура
 * (`billing-appgallery`) — единственного, кто знает вендора; если контур не подключён,
 * сборка падает на этапе графа Hilt, а не показывает пустую витрину.
 *
 * Сессия шторки — **синглтон** намеренно: поверхность покупки одна на все точки входа
 * (Р-67), и её состояние обязано пережить пересоздание активности при повороте, не
 * теряя ни выбранного периода, ни названной причины отказа.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProModule {

    @Provides
    @Singleton
    fun provideProSheetSession(
        store: ProStore,
        cache: ProSubscriptionCache,
        log: OperationLog,
        time: TimeSource,
    ): ProSheetSession = ProSheetSession(store = store, cache = cache, log = log, time = time)

    /**
     * Проверка подписки при запуске с сетью (`ADR-0015` п. 1).
     *
     * Отказ проверки ничего не меняет: доступ остаётся по кэшу, срока давности у него нет
     * (П-6, Р-68 п. 7).
     */
    @Provides
    @Singleton
    fun provideRefreshProSubscription(
        store: ProStore,
        cache: ProSubscriptionCache,
        log: OperationLog,
        time: TimeSource,
    ): RefreshProSubscriptionUseCase = RefreshProSubscriptionUseCase(
        store = store,
        cache = cache,
        log = log,
        time = time,
    )

    @Provides
    @Singleton
    fun provideObserveProStatus(cache: ProSubscriptionCache, time: TimeSource): ObserveProStatusUseCase =
        ObserveProStatusUseCase(cache = cache, time = time)

    /**
     * Условие «пора переспросить магазин»: в кэше лежит просроченная запись (SKL-122).
     *
     * Им возвращение в приложение отличается от запуска: на старте проверка идёт всегда,
     * а по возвращении — только когда иначе платящий человек увидел бы витрину вместо
     * управления подпиской (Р-68 п. 7).
     */
    @Provides
    @Singleton
    fun provideProRefreshDue(cache: ProSubscriptionCache, time: TimeSource): ProSubscriptionRefreshDueUseCase =
        ProSubscriptionRefreshDueUseCase(cache = cache, time = time)
}
