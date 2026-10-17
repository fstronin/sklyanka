package ru.stohastika.sklyanka.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionCache
import ru.stohastika.sklyanka.feature.widgets.open.ProShowcaseTarget
import ru.stohastika.sklyanka.pro.FreeProContour
import ru.stohastika.sklyanka.pro.FreeProHost
import ru.stohastika.sklyanka.pro.FreeProSubscriptionCache
import ru.stohastika.sklyanka.pro.ProHost
import ru.stohastika.sklyanka.ui.pro.ProContour
import ru.stohastika.sklyanka.widget.FreeProShowcaseTarget
import javax.inject.Singleton

/**
 * Вариантные связки бесплатной сборки (Р-86, `ADR-0016` п. 1–3).
 *
 * Здесь замыкаются все четыре места, где `free` отличается от `pro`: кэш доступа всегда
 * «есть», витрины нет, флаг интерфейса выключен, слот виджета пуст. Продуктовый модуль
 * `ProModule` и домен о различии не знают — они читают те же порты.
 *
 * Биндинга [ProSubscriptionCache] в `core-data` больше нет: он жил там один на оба
 * варианта, и второй биндинг дал бы дубль в графе Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FreeProModule {

    @Binds
    @Singleton
    abstract fun proSubscriptionCache(implementation: FreeProSubscriptionCache): ProSubscriptionCache

    @Binds
    @Singleton
    abstract fun proHost(implementation: FreeProHost): ProHost

    @Binds
    @Singleton
    abstract fun proShowcaseTarget(implementation: FreeProShowcaseTarget): ProShowcaseTarget

    @Binds
    @Singleton
    abstract fun proContour(implementation: FreeProContour): ProContour
}
