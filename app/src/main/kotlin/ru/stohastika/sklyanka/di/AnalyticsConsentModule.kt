package ru.stohastika.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.analytics.AcceptAnalyticsFromSettingsUseCase
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.DeclineAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.GrantAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.ObserveAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.SetAnalyticsEnabledUseCase
import ru.stohastika.sklyanka.feature.settings.AnalyticsConsentControl
import javax.inject.Singleton

/**
 * Сценарии согласия на аналитику (SKL-82).
 *
 * Домен о Hilt не знает (НФТ-46): сценарии живут в `core-domain`, а связки объявляются
 * здесь. Хранилище решения и канал событий приходят из `core-data` и `core-analytics`.
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsConsentModule {

    @Provides
    @Singleton
    fun provideGrantConsent(consent: AnalyticsConsentStore, analytics: Analytics): GrantAnalyticsConsentUseCase =
        GrantAnalyticsConsentUseCase(consent, analytics)

    @Provides
    @Singleton
    fun provideDeclineConsent(consent: AnalyticsConsentStore, analytics: Analytics): DeclineAnalyticsConsentUseCase =
        DeclineAnalyticsConsentUseCase(consent, analytics)

    @Provides
    @Singleton
    fun provideAcceptFromSettings(
        consent: AnalyticsConsentStore,
        analytics: Analytics,
    ): AcceptAnalyticsFromSettingsUseCase = AcceptAnalyticsFromSettingsUseCase(consent, analytics)

    @Provides
    @Singleton
    fun provideSetEnabled(consent: AnalyticsConsentStore, analytics: Analytics): SetAnalyticsEnabledUseCase =
        SetAnalyticsEnabledUseCase(consent, analytics)

    @Provides
    @Singleton
    fun provideObserveConsent(consent: AnalyticsConsentStore): ObserveAnalyticsConsentUseCase =
        ObserveAnalyticsConsentUseCase(consent)

    /** Экран настроек видит согласие одним значением: поток переключателя и выключение. */
    @Provides
    @Singleton
    fun provideAnalyticsConsentControl(
        observe: ObserveAnalyticsConsentUseCase,
        setEnabled: SetAnalyticsEnabledUseCase,
    ): AnalyticsConsentControl = AnalyticsConsentControl(
        observeEnabled = observe::invoke,
        setEnabled = setEnabled::invoke,
    )
}
