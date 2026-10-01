package ru.stohastika.sklyanka.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.data.onboarding.OnboardingStateDataStore
import ru.stohastika.sklyanka.domain.onboarding.ConfirmReliabilityStepUseCase
import ru.stohastika.sklyanka.domain.onboarding.LeaveOnboardingUseCase
import ru.stohastika.sklyanka.domain.onboarding.ReadPermissionChecklistUseCase
import ru.stohastika.sklyanka.domain.onboarding.ShouldShowOnboardingUseCase
import ru.stohastika.sklyanka.domain.onboarding.VendorProfileReader
import ru.stohastika.sklyanka.domain.onboarding.port.AutostartHintReader
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingLog
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingStateStore
import ru.stohastika.sklyanka.domain.onboarding.port.PermissionRequestSupport
import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.feature.onboarding.SystemSettingsNavigator
import ru.stohastika.sklyanka.platform.onboarding.AndroidSystemSettingsNavigator
import ru.stohastika.sklyanka.platform.onboarding.AndroidVendorProfileReader
import ru.stohastika.sklyanka.platform.onboarding.BatteryOptimizationHintReader
import ru.stohastika.sklyanka.platform.onboarding.OnboardingLogcat
import ru.stohastika.sklyanka.platform.onboarding.SdkPermissionRequestSupport
import javax.inject.Singleton

/**
 * Сборка онбординга разрешений (SKL-14).
 *
 * Отдельный модуль, а не строки в `ReminderModule`: подсистема напоминаний работает
 * без онбординга, а онбординг без неё — нет, и одностороннюю зависимость видно по составу
 * графа, а не по комментарию. Сценарии живут в домене и об Hilt не знают (НФТ-46).
 */
@Module
@InstallIn(SingletonComponent::class)
object PermissionOnboardingModule {

    @Provides
    @Singleton
    fun provideReadChecklist(
        permissions: PermissionStateReader,
        store: OnboardingStateStore,
        hints: AutostartHintReader,
        requests: PermissionRequestSupport,
        time: TimeSource,
        vendor: VendorProfileReader,
    ): ReadPermissionChecklistUseCase = ReadPermissionChecklistUseCase(
        permissions = permissions,
        store = store,
        hints = hints,
        requests = requests,
        time = time,
        vendor = vendor,
    )

    @Provides
    @Singleton
    fun provideConfirmReliabilityStep(store: OnboardingStateStore, log: OnboardingLog): ConfirmReliabilityStepUseCase =
        ConfirmReliabilityStepUseCase(store = store, log = log)

    @Provides
    @Singleton
    fun provideLeaveOnboarding(store: OnboardingStateStore, log: OnboardingLog): LeaveOnboardingUseCase =
        LeaveOnboardingUseCase(store = store, log = log)

    @Provides
    @Singleton
    fun provideShouldShowOnboarding(store: OnboardingStateStore): ShouldShowOnboardingUseCase =
        ShouldShowOnboardingUseCase(store)
}

/** Порты онбординга соединяются с платформенными реализациями и с хранилищем. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionOnboardingBindings {

    @Binds
    @Singleton
    abstract fun bindOnboardingStateStore(impl: OnboardingStateDataStore): OnboardingStateStore

    @Binds
    @Singleton
    abstract fun bindAutostartHintReader(impl: BatteryOptimizationHintReader): AutostartHintReader

    @Binds
    @Singleton
    abstract fun bindVendorProfileReader(impl: AndroidVendorProfileReader): VendorProfileReader

    @Binds
    @Singleton
    abstract fun bindPermissionRequestSupport(impl: SdkPermissionRequestSupport): PermissionRequestSupport

    @Binds
    @Singleton
    abstract fun bindOnboardingLog(impl: OnboardingLogcat): OnboardingLog

    @Binds
    @Singleton
    abstract fun bindSystemSettingsNavigator(impl: AndroidSystemSettingsNavigator): SystemSettingsNavigator
}
