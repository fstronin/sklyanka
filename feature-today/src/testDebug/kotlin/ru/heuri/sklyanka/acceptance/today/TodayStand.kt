package ru.heuri.sklyanka.acceptance.today

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.stock.StockWarning
import ru.heuri.sklyanka.domain.stock.StockWarningForProfileUseCase
import ru.heuri.sklyanka.domain.today.BuildTodayAgenda
import ru.heuri.sklyanka.domain.today.DayProgress
import ru.heuri.sklyanka.domain.today.PartOfDay
import ru.heuri.sklyanka.domain.today.TodayAgenda
import ru.heuri.sklyanka.domain.today.TodayLayout
import ru.heuri.sklyanka.feature.today.TodayUiState
import java.time.LocalTime

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
        // Планировщик здесь не запускается намеренно: окно генерации начинается «сейчас»
        // (SKL-06), и запуск после перевода часов вперёд не создал бы приёма, время
        // которого уже прошло. Приёмы суток порождает Предыстория, в свой момент.
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
            stockWarning = stockWarning(world, profile.id),
        )
    }

    /**
     * Самое срочное предупреждение об остатке — тем же доменным сценарием, что в продукте.
     *
     * Плашка на экране показывает одно предупреждение из сработавших порогов, и решает,
     * какое (SKL-12): копии этого выбора в стенде нет.
     */
    private fun stockWarning(world: AcceptanceWorld, profileId: String): StockWarning? {
        val useCase = StockWarningForProfileUseCase(
            world.repositories.medicationStore,
            world.clock.time,
            world.log,
        )
        return runBlocking { useCase(profileId).first() }
    }

    fun progress(world: AcceptanceWorld): DayProgress = agenda(world).progress

    /** Лекарства, приёмы которых экран действительно показывает. */
    fun shownMedicationIds(world: AcceptanceWorld): Set<String> = when (val layout = agenda(world).layout) {
        is TodayLayout.Plain -> layout.groups
        is TodayLayout.Dense -> layout.sections.flatMap { it.groups }
    }.flatMap { group -> group.items.map { it.medication.id } }.toSet()

    fun state(world: AcceptanceWorld, toggles: TodayScreenToggles = TodayScreenToggles()): TodayUiState {
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
                toggledSections = toggles.toggledSections,
                expandedGroups = toggles.expandedGroups,
                prnCollapsed = toggles.prnCollapsed,
                nothingToday = agenda.hasNothingToday,
            )
        }
    }
}

/**
 * Ручные переключения экрана, накопленные сценарием.
 *
 * В продукте они живут в потоке `TodayViewModel` и переживают пересборку повестки дня
 * (`TDD.md §5.2`). Стенд собирает экран заново на каждый шаг, поэтому накопленное
 * хранит [TodayScreenProbe] между шагами сценария, а состояние принимает готовым.
 */
data class TodayScreenToggles(
    val toggledSections: Set<PartOfDay> = emptySet(),
    val expandedGroups: Set<LocalTime> = emptySet(),
    val prnCollapsed: Boolean = false,
)
