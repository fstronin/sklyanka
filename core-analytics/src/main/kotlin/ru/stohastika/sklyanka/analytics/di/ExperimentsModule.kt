package ru.stohastika.sklyanka.analytics.di

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.analytics.HuaweiExperimentVendor
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import ru.stohastika.sklyanka.domain.experiment.ConsentedExperiments
import ru.stohastika.sklyanka.domain.experiment.ExperimentCacheStore
import ru.stohastika.sklyanka.domain.experiment.ExperimentVendor
import ru.stohastika.sklyanka.domain.experiment.Experiments
import javax.inject.Singleton

/**
 * Сборка A/B-контура (SKL-83, TDD §10.2).
 *
 * `debuggable` берётся из флага собранного приложения, а не из `BuildConfig` библиотеки:
 * у `core-analytics` он всегда `false`, и debug-сборка не отличила бы себя от релизной.
 * В debug SDK присутствует — гейты сборки едины (ПМ-28), — но гейт не запрашивает
 * варианты и не применяет их.
 */
@Module
@InstallIn(SingletonComponent::class)
object ExperimentsModule {

    @Provides
    @Singleton
    fun provideExperimentVendor(): ExperimentVendor = HuaweiExperimentVendor()

    @Provides
    @Singleton
    fun provideExperiments(
        vendor: ExperimentVendor,
        cache: ExperimentCacheStore,
        analytics: Analytics,
        log: AnalyticsLog,
        @ApplicationContext context: Context,
    ): Experiments = ConsentedExperiments(
        vendor = vendor,
        cache = cache,
        analytics = analytics,
        debuggable = debuggable(context),
        log = log,
    )

    private fun debuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
