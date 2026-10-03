package ru.stohastika.sklyanka.analytics.di

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.analytics.AndroidAnalyticsLog
import ru.stohastika.sklyanka.analytics.HuaweiAnalyticsSink
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import ru.stohastika.sklyanka.domain.analytics.AnalyticsSink
import ru.stohastika.sklyanka.domain.analytics.ConsentedAnalytics
import javax.inject.Singleton

/**
 * Сборка аналитического контура (TDD §5.3, §10).
 *
 * `debuggable` берётся из флага собранного приложения, а не из `BuildConfig` библиотеки:
 * у `core-analytics` он всегда `false`, и debug-сборка не отличила бы себя от релизной.
 * В debug SDK присутствует — гейты сборки едины (ПМ-28), — но гейт не активирует его.
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideAnalyticsLog(): AnalyticsLog = AndroidAnalyticsLog()

    @Provides
    @Singleton
    fun provideAnalyticsSink(@ApplicationContext context: Context, log: AnalyticsLog): AnalyticsSink =
        HuaweiAnalyticsSink(context, log)

    @Provides
    @Singleton
    fun provideAnalytics(sink: AnalyticsSink, log: AnalyticsLog, @ApplicationContext context: Context): Analytics =
        ConsentedAnalytics(sink, debuggable(context), log)

    private fun debuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
