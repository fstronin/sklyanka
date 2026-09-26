package ru.heuri.sklyanka.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.heuri.sklyanka.domain.time.TimeSource
import ru.heuri.sklyanka.platform.time.SystemTimeSource
import javax.inject.Singleton

/**
 * Привязка источника времени.
 *
 * Домен объявляет интерфейс, платформа даёт реализацию, Hilt их соединяет —
 * так платформенные механизмы (будильники, уведомления, часы, Wear Engine)
 * входят в проект, не проникая в домен.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {

    @Binds
    @Singleton
    abstract fun bindTimeSource(impl: SystemTimeSource): TimeSource
}
