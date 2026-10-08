package ru.stohastika.sklyanka.screen.support

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.data.repository.ProfileBootstrap
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingStateStore
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminder
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminders
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.stohastika.sklyanka.domain.schedule.PlanScope
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import java.time.LocalDate

/**
 * Точка входа тестового графа и сид состояния (SKL-97, TDD §6.2).
 *
 * Состояние создаётся доменными путями: владелец — `ProfileBootstrap`, лекарство —
 * репозиторий, приёмы — `PlanIntakesUseCase`, онбординг — его хранилище.
 * Запись прямо в базу или DataStore не используется: инварианты и мапперы те же,
 * что в продукте.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DeviceTestGraph {

    fun profileBootstrap(): ProfileBootstrap

    fun onboardingState(): OnboardingStateStore

    fun medications(): MedicationRepository

    fun planIntakes(): PlanIntakesUseCase

    /** Приёмы профиля: чтения следствий отметки, откладывания и пропуска (SKL-98). */
    fun intakes(): IntakeRepository

    /** Отложенные срабатывания: следствие «Отложить» на карточке напоминания. */
    fun deferredReminders(): DeferredReminders

    /** Журнал срабатываний: следствие «Проверить сейчас» на экране диагностики. */
    fun reminderJournal(): ReminderJournal
}

internal object DeviceTestSeed {

    fun ownerProfile(context: Context, name: String): Profile = runBlocking {
        graph(context).profileBootstrap().ensureOwnerProfile(name, ProfilePalette.ownerAccentArgb)
    }

    fun onboardingSeen(context: Context) = runBlocking {
        graph(context).onboardingState().markSeen()
    }

    fun medication(context: Context, medication: Medication) = runBlocking {
        graph(context).medications().save(medication)
    }

    fun planIntakes(context: Context, profileId: String) = runBlocking {
        graph(context).planIntakes()(PlanScope.Profile(profileId))
    }

    /** Приёмы профиля за календарный день: следствия действий карточек читаются отсюда. */
    fun dayIntakes(context: Context, profileId: String, date: LocalDate): List<Intake> = runBlocking {
        graph(context).intakes().observeDay(profileId, date).first()
    }

    /** Отметка «принято» сидом: проверка «после первой отметки удаления нет» (SKL-99). */
    fun markTaken(context: Context, intakeId: String) = runBlocking {
        graph(context).intakes().markIfPending(intakeId, IntakeStatus.TAKEN, IntakeSource.PHONE)
    }

    /** Отложенная запись приёма, если срабатывание перенесено, — иначе `null`. */
    fun deferredFor(context: Context, intakeId: String): DeferredReminder? = runBlocking {
        graph(context).deferredReminders().find(intakeId)
    }

    /** Последняя запись журнала указанного вида: по ней проверяется пересборка «Проверить сейчас». */
    fun lastJournalEvent(context: Context, event: ReminderEvent): ReminderLogEntry? = runBlocking {
        graph(context).reminderJournal().last(event)
    }

    private fun graph(context: Context): DeviceTestGraph =
        EntryPointAccessors.fromApplication(context.applicationContext, DeviceTestGraph::class.java)
}
