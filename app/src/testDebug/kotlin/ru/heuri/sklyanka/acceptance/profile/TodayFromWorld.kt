package ru.heuri.sklyanka.acceptance.profile

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.stock.StockWarning
import ru.heuri.sklyanka.domain.stock.StockWarningForProfileUseCase
import ru.heuri.sklyanka.domain.today.BuildTodayAgenda
import ru.heuri.sklyanka.feature.today.TodayUiState

/**
 * Состояние экрана «Сегодня» из мира сценария приёмки.
 *
 * Собирается настоящим доменом ([BuildTodayAgenda]) и настоящим правилом отображения,
 * которое `TodayViewModel` применяет к повестке дня. Выдуманное состояние проверяло бы
 * разметку, а не продукт: приёмы дня обязан отобрать домен из того, что завела
 * Предыстория сценария.
 */
internal object TodayFromWorld {

    fun state(world: AcceptanceWorld): TodayUiState {
        // Приёмы порождает планировщик, а не шаг: Предыстория называет расписание,
        // а приёмы из него выводит SKL-06 — второй копии этого вывода в приёмке нет.
        world.plan()
        val profileName = world.profiles.active
        val profile = world.profiles.get(profileName)
        val date = world.clock.today()
        val medications = world.medications.all().filter { it.profileId == profile.id && !it.isArchived }
        val intakes = world.intakes.ofProfile(world.profiles, profileName)
            .filter { it.scheduledAt.toLocalDate() == date }
        val agenda = BuildTodayAgenda()(
            date = date,
            intakes = intakes,
            activeMedications = medications,
            now = world.clock.nowLocal(),
            zone = world.clock.zone,
            stockWarning = stockWarning(world, profile.id),
        )
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
}
