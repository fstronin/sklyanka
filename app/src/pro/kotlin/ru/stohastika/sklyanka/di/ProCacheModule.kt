package ru.stohastika.sklyanka.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.data.prefs.ProSubscriptionDataStore
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionCache
import javax.inject.Singleton

/**
 * Кэш доступа «Про» платной сборки (SKL-153, `ADR-0016` п. 2).
 *
 * В `pro` кэш — настоящий последний ответ магазина ([ProSubscriptionDataStore]): так же,
 * как было до разделения контуров. Биндинг живёт здесь, а не в `core-data`: в `free`
 * тот же порт отдаёт `FreeProSubscriptionCache`, и два биндинга в общем модуле дали бы
 * дубль в графе Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProCacheModule {

    @Binds
    @Singleton
    abstract fun proSubscriptionCache(implementation: ProSubscriptionDataStore): ProSubscriptionCache
}
