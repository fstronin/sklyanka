package ru.heuri.sklyanka.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.heuri.sklyanka.data.log.RoomOperationLog
import ru.heuri.sklyanka.data.repository.ActiveProfileContext
import ru.heuri.sklyanka.data.repository.ActiveProfileRepositoryImpl
import ru.heuri.sklyanka.data.repository.IntakeRepositoryImpl
import ru.heuri.sklyanka.data.repository.MedicationRepositoryImpl
import ru.heuri.sklyanka.data.repository.ProfileRepositoryImpl
import ru.heuri.sklyanka.data.room.SklyankaTransactor
import ru.heuri.sklyanka.domain.Transactor
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.profile.ProfileContextProvider
import ru.heuri.sklyanka.domain.repository.ActiveProfileRepository
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.repository.ProfileRepository
import javax.inject.Singleton

/**
 * Интерфейсы домена соединяются с реализациями слоя данных.
 *
 * Наружу из core-data торчат только интерфейсы из core-domain: сущности Room за
 * пределы модуля не выходят, и компромиссы хранения не расползаются по правилам
 * предметной области.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindMedicationRepository(impl: MedicationRepositoryImpl): MedicationRepository

    @Binds
    @Singleton
    abstract fun bindIntakeRepository(impl: IntakeRepositoryImpl): IntakeRepository

    @Binds
    @Singleton
    abstract fun bindActiveProfileRepository(impl: ActiveProfileRepositoryImpl): ActiveProfileRepository

    /** Единственный источник контекста профиля для экранов и оформления (ПРФ-11, ПРФ-12). */
    @Binds
    @Singleton
    abstract fun bindProfileContextProvider(impl: ActiveProfileContext): ProfileContextProvider

    @Binds
    @Singleton
    abstract fun bindTransactor(impl: SklyankaTransactor): Transactor

    @Binds
    @Singleton
    abstract fun bindOperationLog(impl: RoomOperationLog): OperationLog
}
