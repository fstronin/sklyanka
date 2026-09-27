package ru.heuri.sklyanka.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.heuri.sklyanka.domain.watch.WatchLinkStateReader
import ru.heuri.sklyanka.platform.watch.UnpairedWatchLinkReader
import javax.inject.Singleton

/**
 * Сборка экрана настроек (SKL-20).
 *
 * Своего домена у экрана почти нет: профили, напоминания, разрешения и журнал собраны
 * другими задачами и подставляются своими модулями. Здесь остаётся одно — чтение состояния
 * связи с часами, у которого до SKL-17 нет реализации, кроме честного «не настроены».
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsScreenModule {

    @Binds
    @Singleton
    abstract fun bindWatchLinkStateReader(impl: UnpairedWatchLinkReader): WatchLinkStateReader
}
