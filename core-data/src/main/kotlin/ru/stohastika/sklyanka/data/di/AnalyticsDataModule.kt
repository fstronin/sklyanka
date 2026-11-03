package ru.stohastika.sklyanka.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.data.analytics.AnalyticsConsentDataStore
import ru.stohastika.sklyanka.data.analytics.RoomAnalyticsQueue
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsQueue
import javax.inject.Singleton

/**
 * Связка хранения аналитики: согласие и очередь (SKL-165, TDD §5.2).
 *
 * Отдельный модуль рядом с [DataBindings] по той же причине, что и у оформления: аналитика
 * заводится своей задачей, и её хранилища должны быть видны одним файлом, а не строкой
 * в общем списке из тридцати привязок.
 *
 * Поставщика (`AnalyticsTransport`) здесь нет намеренно: он живёт в `core-analytics`
 * и привязывается своим модулем. Слой данных не знает, кто и куда отправляет.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsDataModule {

    /** Решение человека об аналитике — в общем файле настроек (ПМ-25). */
    @Binds
    @Singleton
    abstract fun bindAnalyticsConsentStore(impl: AnalyticsConsentDataStore): AnalyticsConsentStore

    /** Очередь событий на той же базе, что и данные, но без единой связи с ними (АН-4). */
    @Binds
    @Singleton
    abstract fun bindAnalyticsQueue(impl: RoomAnalyticsQueue): AnalyticsQueue
}
