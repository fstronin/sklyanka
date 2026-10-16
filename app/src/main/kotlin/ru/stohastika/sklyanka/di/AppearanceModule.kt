package ru.stohastika.sklyanka.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.appearance.AppearancePreferences
import ru.stohastika.sklyanka.domain.appearance.ObserveAccentThemeUseCase
import ru.stohastika.sklyanka.domain.appearance.ObserveIconVariantUseCase
import ru.stohastika.sklyanka.domain.appearance.SetAccentThemeUseCase
import ru.stohastika.sklyanka.domain.appearance.SetIconVariantUseCase
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import javax.inject.Singleton

/**
 * Сборка оформления: акцентная тема и вариант иконки (§2.5, §2.4, SKL-120).
 *
 * Домен о Hilt не знает (НФТ-46), поэтому чтение и запись выбора собираются здесь —
 * тем же приёмом, что платный контур в [ProModule]. Носитель выбора приезжает портом
 * [AppearancePreferences] из `core-data`; где именно он лежит, домен не знает.
 *
 * Синглтоны намеренны: и чтение, и запись ходят в один и тот же файл постоянных настроек,
 * и второй экземпляр шва разошёлся бы с первым на первой же правке выбора.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppearanceModule {

    @Provides
    @Singleton
    fun provideObserveAccentTheme(preferences: AppearancePreferences): ObserveAccentThemeUseCase =
        ObserveAccentThemeUseCase(preferences)

    @Provides
    @Singleton
    fun provideObserveIconVariant(preferences: AppearancePreferences): ObserveIconVariantUseCase =
        ObserveIconVariantUseCase(preferences)

    /**
     * Выбор темы — через тот же гейт, что и в настройках (§5.17, Р-22).
     *
     * Доступ берётся у [ObserveProStatusUseCase]: он читает кэш последнего ответа магазина
     * и в сеть не ходит (П-6).
     */
    @Provides
    @Singleton
    fun provideSetAccentTheme(
        preferences: AppearancePreferences,
        proStatus: ObserveProStatusUseCase,
    ): SetAccentThemeUseCase = SetAccentThemeUseCase(preferences = preferences, proStatus = proStatus)

    @Provides
    @Singleton
    fun provideSetIconVariant(
        preferences: AppearancePreferences,
        proStatus: ObserveProStatusUseCase,
    ): SetIconVariantUseCase = SetIconVariantUseCase(preferences = preferences, proStatus = proStatus)
}
