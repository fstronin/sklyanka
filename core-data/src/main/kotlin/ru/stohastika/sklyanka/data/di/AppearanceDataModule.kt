package ru.stohastika.sklyanka.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.data.prefs.AppearancePreferencesDataStore
import ru.stohastika.sklyanka.domain.appearance.AppearancePreferences
import javax.inject.Singleton

/**
 * Связка постоянных настроек оформления (SKL-120, §5.17).
 *
 * Отдельный модуль рядом с [DataModule] и остальными: оформление заводится своей задачей,
 * и его хранилище должно быть видно одним файлом. Реализация живёт в `core-data`, а порт —
 * в домене: правило «платный выбор проходит только с доступом» принадлежит домену, и он же
 * решает, что запись означает.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppearanceDataModule {

    @Binds
    @Singleton
    abstract fun bindAppearancePreferences(impl: AppearancePreferencesDataStore): AppearancePreferences
}
