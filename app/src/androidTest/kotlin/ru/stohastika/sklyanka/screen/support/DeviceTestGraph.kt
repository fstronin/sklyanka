package ru.stohastika.sklyanka.screen.support

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.data.repository.ProfileBootstrap
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentDecision
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentVersion
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingStateStore
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.stohastika.sklyanka.domain.schedule.PlanScope
import ru.stohastika.sklyanka.ui.token.ProfilePalette

/**
 * Точка входа тестового графа и сид состояния (SKL-97, TDD §6.2).
 *
 * Состояние создаётся доменными путями: владелец — `ProfileBootstrap`, лекарство —
 * репозиторий, приёмы — `PlanIntakesUseCase`, согласие и онбординг — их хранилища.
 * Запись прямо в базу или DataStore не используется: инварианты и мапперы те же,
 * что в продукте.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DeviceTestGraph {

    fun profileBootstrap(): ProfileBootstrap

    fun onboardingState(): OnboardingStateStore

    fun analyticsConsent(): AnalyticsConsentStore

    fun medications(): MedicationRepository

    fun planIntakes(): PlanIntakesUseCase
}

internal object DeviceTestSeed {

    fun ownerProfile(context: Context, name: String): Profile = runBlocking {
        graph(context).profileBootstrap().ensureOwnerProfile(name, ProfilePalette.ownerAccentArgb)
    }

    fun onboardingSeen(context: Context) = runBlocking {
        graph(context).onboardingState().markSeen()
    }

    fun consent(context: Context, decision: AnalyticsConsentDecision) = runBlocking {
        graph(context).analyticsConsent().record(decision, AnalyticsConsentVersion.MATERIAL)
    }

    fun medication(context: Context, medication: Medication) = runBlocking {
        graph(context).medications().save(medication)
    }

    fun planIntakes(context: Context, profileId: String) = runBlocking {
        graph(context).planIntakes()(PlanScope.Profile(profileId))
    }

    private fun graph(context: Context): DeviceTestGraph =
        EntryPointAccessors.fromApplication(context.applicationContext, DeviceTestGraph::class.java)
}
