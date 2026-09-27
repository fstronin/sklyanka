package ru.heuri.sklyanka.acceptance.today

import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.today.BuildTodayAgenda
import ru.heuri.sklyanka.domain.today.DayProgress
import ru.heuri.sklyanka.domain.today.TodayAgenda
import ru.heuri.sklyanka.domain.today.TodayLayout
import ru.heuri.sklyanka.feature.today.TodayUiState

/**
 * Повестка суток и состояние экрана «Сегодня» из мира сценария приёмки.
 *
 * Собирается настоящим доменом ([BuildTodayAgenda]) и тем же правилом отображения,
 * которым `TodayViewModel` превращает повестку в состояние экрана. Выдуманное состояние
 * проверяло бы разметку, а приёмка SKL-08 говорит о продукте: какие приёмы попали
 * в сутки активного профиля и что о них сказано.
 */
internal object TodayStand {

    fun agenda(world: AcceptanceWorld): TodayAgenda {
        // Приёмы порождает планировщик SKL-06, а не шаг: Предыстория называет расписание.
        world.plan()
        val profileName = world.profiles.active
        val profile = world.profiles.get(profileName)
        val date = world.clock.today()
        return BuildTodayAgenda()(
            date = date,
            intakes = world.intakes.ofProfile(world.profiles, profileName)
                .filter { it.scheduledAt.toLocalDate() == date },
            activeMedications = world.medications.all().filter { it.profileId == profile.id && !it.isArchived },
            now = world.clock.nowLocal(),
            zone = world.clock.zone,
        )
    }

    fun progress(world: AcceptanceWorld): DayProgress = agenda(world).progress

    /** Лекарства, приёмы которых экран действительно показывает. */
    fun shownMedicationIds(world: AcceptanceWorld): Set<String> = when (val layout = agenda(world).layout) {
        is TodayLayout.Plain -> layout.groups
        is TodayLayout.Dense -> layout.sections.flatMap { it.groups }
    }.flatMap { group -> group.items.map { it.medication.id } }.toSet()

    fun state(world: AcceptanceWorld): TodayUiState {
        val agenda = agenda(world)
        return if (agenda.isFirstRun) {
            TodayUiState.Empty(agenda.date)
        } else {
            TodayUiState.Content(
                date = agenda.date,
                progress = agenda.progress,
                stockWarning = agenda.stockWarning,
                layout = agenda.layout,
                prn = agenda.prn,
                toggledSections = emptySet(),
                expandedGroups = emptySet(),
                prnCollapsed = false,
                nothingToday = agenda.hasNothingToday,
            )
        }
    }
}
