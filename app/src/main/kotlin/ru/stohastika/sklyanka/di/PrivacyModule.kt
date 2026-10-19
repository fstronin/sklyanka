package ru.stohastika.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.privacy.AcknowledgePrivacyConsentUseCase
import ru.stohastika.sklyanka.domain.privacy.PrivacyConsentStore
import ru.stohastika.sklyanka.domain.privacy.ShouldShowPrivacyConsentUseCase
import javax.inject.Singleton

/**
 * Сборка показа политики при первом запуске (Р-89).
 *
 * Отдельный модуль, а не строки в онбординге: политика — не шаг настройки надёжности,
 * и её диалог показывается до него, даже если онбординг человек ещё не видел.
 */
@Module
@InstallIn(SingletonComponent::class)
object PrivacyModule {

    @Provides
    @Singleton
    fun provideShouldShowPrivacyConsent(store: PrivacyConsentStore): ShouldShowPrivacyConsentUseCase =
        ShouldShowPrivacyConsentUseCase(store)

    @Provides
    @Singleton
    fun provideAcknowledgePrivacyConsent(store: PrivacyConsentStore): AcknowledgePrivacyConsentUseCase =
        AcknowledgePrivacyConsentUseCase(store)
}
