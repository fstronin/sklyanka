package ru.stohastika.sklyanka.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.data.log.RoomOperationLog
import ru.stohastika.sklyanka.data.prefs.DatabaseLifeDataStore
import ru.stohastika.sklyanka.data.prefs.DeviceProtectedDirectBootStore
import ru.stohastika.sklyanka.data.privacy.PrivacyConsentDataStore
import ru.stohastika.sklyanka.data.repository.ActiveProfileContext
import ru.stohastika.sklyanka.data.repository.ActiveProfileRepositoryImpl
import ru.stohastika.sklyanka.data.repository.IntakeRepositoryImpl
import ru.stohastika.sklyanka.data.repository.MedicationRepositoryImpl
import ru.stohastika.sklyanka.data.repository.ProfileRepositoryImpl
import ru.stohastika.sklyanka.data.repository.RoomProfileEraser
import ru.stohastika.sklyanka.data.repository.RoomProfileLossesSource
import ru.stohastika.sklyanka.data.room.SklyankaTransactor
import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.privacy.PrivacyConsentStore
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.profile.ProfileEraser
import ru.stohastika.sklyanka.domain.profile.ProfileLossesSource
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootAlarmStore
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootTraceStore
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.startup.DatabaseLifeStore
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

    /** Стирание профиля одной транзакцией — сценарий SKL-36 (ОВ-7). */
    @Binds
    @Singleton
    abstract fun bindProfileEraser(impl: RoomProfileEraser): ProfileEraser

    /** Числа потерь профиля для экрана удаления — теми же запросами, что список и история (SKL-36). */
    @Binds
    @Singleton
    abstract fun bindProfileLossesSource(impl: RoomProfileLossesSource): ProfileLossesSource

    @Binds
    @Singleton
    abstract fun bindTransactor(impl: SklyankaTransactor): Transactor

    @Binds
    @Singleton
    abstract fun bindOperationLog(impl: RoomOperationLog): OperationLog

    /** Зеркало поставленных будильников в device-protected storage (SKL-87). */
    @Binds
    @Singleton
    abstract fun bindDirectBootAlarmStore(impl: DeviceProtectedDirectBootStore): DirectBootAlarmStore

    /** Очередь следов locked-фазы; забирается после разблокировки одной операцией (SKL-87). */
    @Binds
    @Singleton
    abstract fun bindDirectBootTraceStore(impl: DeviceProtectedDirectBootStore): DirectBootTraceStore

    /** Отметки жизни базы вне её файла — объяснение сброса после повреждения (МГ-8). */
    @Binds
    @Singleton
    abstract fun bindDatabaseLifeStore(impl: DatabaseLifeDataStore): DatabaseLifeStore

    /** Диалог политики при первом запуске (Р-89). */
    @Binds
    @Singleton
    abstract fun bindPrivacyConsentStore(impl: PrivacyConsentDataStore): PrivacyConsentStore
}
