package ru.heuri.sklyanka.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import ru.heuri.sklyanka.startup.StartupSeed

/**
 * Объявление пустого набора отладочных заготовок данных.
 *
 * Без него граф Hilt не соберётся в релизной сборке: связок [StartupSeed] там нет вовсе,
 * а «нет связок» и «нет объявления» для Dagger — разные вещи. Объявление здесь, связка —
 * только в `src/debug`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StartupSeedModule {

    @Multibinds
    abstract fun seeds(): Set<StartupSeed>
}
