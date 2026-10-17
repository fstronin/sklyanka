package ru.stohastika.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionCache
import ru.stohastika.sklyanka.domain.time.TimeSource
import javax.inject.Singleton

/**
 * Чтение доступа «Про» для гейтов состава (SKL-122, SKL-153, `ADR-0016` п. 3).
 *
 * Здесь осталось только то, что нужно обоим контурам: гейты читают [ObserveProStatusUseCase],
 * а он по-прежнему считается по [ProSubscriptionCache]. В `free` кэш варианта всегда отвечает
 * «доступ есть» (`FreeProSubscriptionCache`), в `pro` — настоящий ответ магазина
 * (`ProSubscriptionDataStore`); сам домен об этом различии не знает.
 *
 * Сессия шторки, проверка подписки и порт `ProStore`
 * переехали в `app/src/pro/.../di/ProStoreModule.kt`: в free-графе магазина нет вовсе,
 * и держать его сборку в общем модуле значило бы требовать `ProStore` от бесплатной сборки.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProModule {

    @Provides
    @Singleton
    fun provideObserveProStatus(cache: ProSubscriptionCache, time: TimeSource): ObserveProStatusUseCase =
        ObserveProStatusUseCase(cache = cache, time = time)
}
