package ru.stohastika.sklyanka.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.data.repository.HistoryRepositoryImpl
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.dao.HistoryDao
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import javax.inject.Singleton

/**
 * Слой данных истории приёмов (SKL-18).
 *
 * Отдельный модуль рядом с [DataModule] и [DataBindings] — по образцу переноса данных
 * и подсистемы напоминаний: у истории единственная особенность, и она обязана быть видна
 * одним файлом. Особенность в том, что выборка лекарств идёт **без фильтра архива**
 * (ПР-11), и это единственное место приложения, где так правильно.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryDataModule {

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    companion object {

        @Provides
        fun provideHistoryDao(database: SklyankaDatabase): HistoryDao = database.historyDao()
    }
}
