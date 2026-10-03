package ru.stohastika.sklyanka.analytics.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.analytics.AndroidAnalyticsLog
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import ru.stohastika.sklyanka.domain.analytics.NoOpAnalytics
import javax.inject.Singleton

/**
 * Сборка аналитического контура: заглушка вместо вендора (ПМ-31, SKL-100 TDD §6.2).
 *
 * Механизм остаётся доменным: порт [Analytics], порт `AnalyticsSink`, каталог событий
 * и гейт `ConsentedAnalytics` живут в `core-domain` и проверяются тестами. В релизе
 * вендора нет, поэтому граф отдаёт [NoOpAnalytics]: события не уходят и не накапливаются.
 *
 * Подключение провайдера — новое решение: замена этой строки на адаптер и возврат
 * вендорских координат в правила состава артефакта.
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideAnalyticsLog(): AnalyticsLog = AndroidAnalyticsLog()

    // Заглушка не имеет ни sink, ни хранилища: отправлять и накапливать нечего.
    @Provides
    @Singleton
    fun provideAnalytics(): Analytics = NoOpAnalytics
}
