package ru.stohastika.sklyanka.analytics

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsTransport
import javax.inject.Qualifier

/**
 * Ключ SDK провайдера из [BuildConfig] (TDD §2.3).
 *
 * Отдельный квалификатор, а не строковый параметр «где-то рядом»: ключ приходит из сборки
 * один раз, и адаптер получает его подписью, а не читает глобальное состояние. Тест
 * адаптера подставляет пустой ключ тем же способом, каким его получит продукт.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MyTrackerKey

/**
 * Единственное место, где создаётся адаптер провайдера (TDD §2.3).
 *
 * `new MyTrackerTransport(...)` нет ни в `app`, ни в экранах, ни в сценариях домена:
 * подстановка реализации — это и есть граница «продукт не знает MyTracker». Объекты
 * домена (`AnalyticsRecorder`, сценарии согласия) собираются в composition root `app` —
 * здесь их нет, потому что этот модуль не знает продукта.
 *
 * Область — синглтон процесса: SDK инициализируется один раз, и флаг «передача открыта»
 * обязан быть один на процесс, иначе два адаптера разойдутся в решении о согласии.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsProviderModule {

    /** Порт поставщика — единственная точка, через которую домен видит внешний мир. */
    @Binds
    abstract fun bindAnalyticsTransport(impl: MyTrackerTransport): AnalyticsTransport

    /** Тонкий интерфейс SDK: единственная реализация — `AndroidMyTrackerSdk` (TDD §2.3). */
    @Binds
    abstract fun bindMyTrackerSdk(impl: AndroidMyTrackerSdk): MyTrackerSdk

    /** Тот же адаптер как якорь запуска: активность не знает имени SDK (TDD §14, риск 2). */
    @Binds
    abstract fun bindAnalyticsLaunchAnchor(impl: MyTrackerTransport): AnalyticsLaunchAnchor

    companion object {

        /** Ключ из `buildConfigField`; пустой ключ = поставщик недоступен. */
        @Provides
        @MyTrackerKey
        fun provideMyTrackerKey(): String = BuildConfig.MYTRACKER_KEY
    }
}
