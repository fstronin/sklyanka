package ru.stohastika.sklyanka.feature.widgets.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.widget.ReadWidgetSnapshotsUseCase
import ru.stohastika.sklyanka.feature.widgets.bindings.WidgetBindingStore
import ru.stohastika.sklyanka.feature.widgets.glance.GlanceWidgetRefresher
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetRefresher
import javax.inject.Singleton

/**
 * Связки виджетов (SKL-118, `TDD.md §2`).
 *
 * Только то, что заводят сами виджеты: чтение снимка и постоянные настройки привязки.
 * Сценарий отметки, проход по просроченным приёмам и часы приходят из графа приложения —
 * виджет живёт теми же правилами, что и приложение, а не своим набором (ПР-22, НФТ-48).
 */
@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {

    @Provides
    @Singleton
    fun snapshots(
        intakes: IntakeRepository,
        medications: MedicationRepository,
        history: HistoryRepository,
        profiles: ProfileContextProvider,
        time: TimeSource,
    ): ReadWidgetSnapshotsUseCase = ReadWidgetSnapshotsUseCase(
        intakes = intakes,
        medications = medications,
        history = history,
        profiles = profiles,
        time = time,
    )

    @Provides
    @Singleton
    fun bindings(@ApplicationContext context: Context): WidgetBindingStore = WidgetBindingStore(context)

    @Provides
    @Singleton
    fun refresher(glance: GlanceWidgetRefresher): WidgetRefresher = glance
}
