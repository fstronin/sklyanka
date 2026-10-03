package ru.stohastika.sklyanka.acceptance.profile

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.stock.StockWarning
import ru.stohastika.sklyanka.domain.stock.StockWarningForProfileUseCase
import ru.stohastika.sklyanka.domain.today.BuildTodayAgenda
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import ru.stohastika.sklyanka.domain.undo.UndoableMark
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.feature.today.UndoNotice

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
        val medications = world.medications.all()
            .filter { it.profileId == profile.id && !it.isArchived && !it.isDeleted }
        val intakes = world.intakes.ofProfile(world.profiles, profileName)
            .filter { it.scheduledAt.toLocalDate() == date }
            // Приёмы удалённого лекарства из повестки выпадают — тем же правилом, что
            // в `IntakeDao.observeDay` (ПР-14).
            .filter { world.medications.byId(it.medicationId)?.isDeleted != true }
        val agenda = BuildTodayAgenda()(
            date = date,
            intakes = intakes,
            activeMedications = medications,
            now = world.clock.nowLocal(),
            zone = world.clock.zone,
            stockWarning = stockWarning(world, profile.id),
        )
        return if (agenda.isFirstRun) {
            TodayUiState.Empty(
                date = agenda.date,
                deletion = world.undoActions.live() as? UndoableDeletion,
            )
        } else {
            TodayUiState.Content(
                date = agenda.date,
                progress = agenda.progress,
                stockWarning = agenda.stockWarning,
                // Причина ненадёжности читается у системы тем же доменным сценарием,
                // что в `TodayViewModel` (НФТ-52, SKL-29).
                reliability = ReadReminderReliabilityUseCase(world.system)().warning,
                // Окно отмены — из того же магазина, что в продукте (SKL-30): срок
                // проверяет магазин, а разметка показывает готовый факт.
                undo = (world.undoActions.live() as? UndoableMark)?.let { mark ->
                    UndoNotice(mark.intakeIds, mark.result, mark.medicationNames)
                },
                deletion = world.undoActions.live() as? UndoableDeletion,
                layout = agenda.layout,
                prn = agenda.prn,
                toggledSections = emptySet(),
                confirmingGroup = null,
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
