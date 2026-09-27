package ru.heuri.sklyanka.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ru.heuri.sklyanka.debug.DebugProfileSeed
import ru.heuri.sklyanka.domain.profile.CreateProfileUseCase
import ru.heuri.sklyanka.domain.repository.ProfileRepository
import ru.heuri.sklyanka.startup.StartupSeed
import javax.inject.Singleton

/**
 * Заготовка данных отладочной сборки (`ОВ-3`).
 *
 * Модуль живёт в `src/debug`, поэтому в релизной сборке его не существует — не «выключен
 * флагом», а отсутствует в артефакте. Флаг можно поставить не туда; исходника, которого
 * нет, в релизе не окажется никак.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugSeedModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideProfileSeed(
        @ApplicationContext context: Context,
        profiles: ProfileRepository,
        createProfile: CreateProfileUseCase,
    ): StartupSeed = DebugProfileSeed(
        context = context,
        profiles = profiles,
        createProfile = createProfile,
    )
}
