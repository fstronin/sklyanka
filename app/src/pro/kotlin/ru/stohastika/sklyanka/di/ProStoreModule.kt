package ru.stohastika.sklyanka.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.pro.ProSheetSession
import ru.stohastika.sklyanka.domain.pro.ProStore
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionCache
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionRefreshDueUseCase
import ru.stohastika.sklyanka.domain.pro.RefreshProSubscriptionUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.feature.widgets.open.ProShowcaseTarget
import ru.stohastika.sklyanka.pro.ProContourImpl
import ru.stohastika.sklyanka.pro.ProHost
import ru.stohastika.sklyanka.pro.ProHostImpl
import ru.stohastika.sklyanka.ui.pro.ProContour
import ru.stohastika.sklyanka.widget.ProShowcaseTargetImpl
import javax.inject.Singleton

/**
 * Платёжный контур платной сборки (SKL-122, SKL-153, `ADR-0016` п. 2).
 *
 * Здесь собирается всё, чему в free-графе места нет: сессия шторки, проверка подписки,
 * флаг интерфейса, витрина и слот виджета. Реализация порта [ProStore] приезжает из
 * модуля контура (`billing-appgallery`) — единственного, кто знает вендора; если контур
 * не подключён, сборка падает на этапе графа Hilt, а не показывает пустую витрину.
 *
 * Сессия шторки — **синглтон** намеренно: поверхность покупки одна на все точки входа
 * (Р-67), и её состояние обязано пережить пересоздание активности при повороте, не
 * теряя ни выбранного периода, ни названной причины отказа.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProStoreModule {

    @Binds
    @Singleton
    abstract fun proHost(implementation: ProHostImpl): ProHost

    @Binds
    @Singleton
    abstract fun proShowcaseTarget(implementation: ProShowcaseTargetImpl): ProShowcaseTarget

    @Binds
    @Singleton
    abstract fun proContour(implementation: ProContourImpl): ProContour

    companion object {

        @Provides
        @Singleton
        fun provideProSheetSession(
            store: ProStore,
            cache: ProSubscriptionCache,
            log: OperationLog,
            time: TimeSource,
        ): ProSheetSession = ProSheetSession(store = store, cache = cache, log = log, time = time)

        /**
         * Проверка подписки при запуске и возвращении в приложение (`ADR-0015` п. 1).
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
}
