package ru.stohastika.sklyanka.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.data.prefs.ProSubscriptionDataStore
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionCache
import javax.inject.Singleton

/**
 * Связка кэша подписки «Про» (SKL-122, `ADR-0015` п. 1).
 *
 * Отдельный модуль рядом с [DataModule] и остальными: платный контур заводится своей
 * задачей, и его хранилище должно быть видно одним файлом. Реализации порта [ProStore]
 * здесь нет и быть не может — магазин живёт в модуле контура, вне `core-data` (`ADR-0010` п. 2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProDataModule {

    @Binds
    @Singleton
    abstract fun bindProSubscriptionCache(impl: ProSubscriptionDataStore): ProSubscriptionCache
}
