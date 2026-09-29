package ru.stohastika.sklyanka.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.data.prefs.TransferSettingsDataStore
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.transfer.JsonTransferCodec
import ru.stohastika.sklyanka.data.transfer.RoomTransferStore
import ru.stohastika.sklyanka.data.transfer.TransferDao
import ru.stohastika.sklyanka.domain.transfer.TransferCodec
import ru.stohastika.sklyanka.domain.transfer.TransferSettingsStore
import ru.stohastika.sklyanka.domain.transfer.TransferStore
import javax.inject.Singleton

/**
 * Сборка слоя данных переноса (SKL-19).
 *
 * Отдельный модуль рядом с [DataModule] и [DataBindings]: перенос — самостоятельная
 * область со своим форматом файла и своим хранилищем, и его связки не должны смешиваться
 * со связками, от которых зависит каждый экран.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransferDataModule {

    @Binds
    @Singleton
    abstract fun bindTransferCodec(impl: JsonTransferCodec): TransferCodec

    @Binds
    @Singleton
    abstract fun bindTransferStore(impl: RoomTransferStore): TransferStore

    @Binds
    @Singleton
    abstract fun bindTransferSettingsStore(impl: TransferSettingsDataStore): TransferSettingsStore

    companion object {

        @Provides
        fun provideTransferDao(database: SklyankaDatabase): TransferDao = database.transferDao()
    }
}
