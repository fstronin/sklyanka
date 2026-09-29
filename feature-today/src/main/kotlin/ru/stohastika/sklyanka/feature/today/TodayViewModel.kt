package ru.stohastika.sklyanka.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.today.MarkIntakeGroupUseCase
import ru.stohastika.sklyanka.domain.today.ObserveTodayAgendaUseCase
import ru.stohastika.sklyanka.domain.today.PartOfDay
import ru.stohastika.sklyanka.domain.today.TodayAgenda
import java.time.LocalTime
import javax.inject.Inject

/**
 * Экран «Сегодня»: один источник состояния, одно направление данных.
 *
 * Оптимистичного состояния здесь нет (`TDD.md §9.1`): локальная запись в SQLite
 * укладывается в бюджет отклика 150 мс (НФТ-22) с запасом, а вторая версия правды
 * разошлась бы с идемпотентностью при отметке с часов (ПР-2, ПР-3).
 *
 * Отметка идёт только через [MarkIntakeUseCase] и построенный над ним
 * [MarkIntakeGroupUseCase] — второго пути записи отметки в приложении нет (ЧАС-24).
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    observeAgenda: ObserveTodayAgendaUseCase,
    private val markIntake: MarkIntakeUseCase,
    private val markGroup: MarkIntakeGroupUseCase,
    private val createPrn: CreatePrnIntakeUseCase,
) : ViewModel() {

    private val view = MutableStateFlow(ViewState())

    val state: StateFlow<TodayUiState> = combine(observeAgenda(), view) { agenda, current ->
        screen(agenda, current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), TodayUiState.Opening)

    fun onIntent(intent: TodayIntent) {
        when (intent) {
            is TodayIntent.MarkTaken -> mark(intent.intakeId, MarkResult.TAKEN)
            is TodayIntent.MarkSkipped -> mark(intent.intakeId, MarkResult.SKIPPED)
            is TodayIntent.MarkGroupTaken -> viewModelScope.launch {
                markGroup(intent.intakeIds, IntakeSource.PHONE)
            }

            is TodayIntent.TakePrn -> viewModelScope.launch {
                createPrn(intent.medicationId, IntakeSource.PHONE)
            }

            is TodayIntent.ExpandGroup -> view.update { it.withExpandedGroup(intent.time) }
            is TodayIntent.ToggleSection -> view.update { it.withToggledSection(intent.partOfDay) }
            TodayIntent.TogglePrnBlock -> view.update { it.copy(prnCollapsed = !it.prnCollapsed) }

            // Переходы наружу состояния экрана не меняют: их разбирает маршрут.
            is TodayIntent.OpenMedication,
            is TodayIntent.OpenStock,
            TodayIntent.AddMedication,
            TodayIntent.OpenMedicationList,
            TodayIntent.OpenSettings,
            -> Unit
        }
    }

    private fun mark(intakeId: String, result: MarkResult) {
        viewModelScope.launch {
            markIntake(MarkRequest(intakeId = intakeId, result = result, source = IntakeSource.PHONE))
        }
    }

    private fun screen(agenda: TodayAgenda, current: ViewState): TodayUiState = if (agenda.isFirstRun) {
        TodayUiState.Empty(agenda.date)
    } else {
        TodayUiState.Content(
            date = agenda.date,
            progress = agenda.progress,
            stockWarning = agenda.stockWarning,
            layout = agenda.layout,
            prn = agenda.prn,
            toggledSections = current.toggledSections,
            expandedGroups = current.expandedGroups,
            prnCollapsed = current.prnCollapsed,
            nothingToday = agenda.hasNothingToday,
        )
    }

    /**
     * Чисто зрительная часть состояния: что человек развернул руками.
     *
     * Отдельным потоком рядом с повесткой дня, а не полем внутри неё: повестку
     * пересобирает домен при каждом изменении данных, и развёрнутая секция обязана
     * это пережить.
     */
    private data class ViewState(
        val toggledSections: Set<PartOfDay> = emptySet(),
        val expandedGroups: Set<LocalTime> = emptySet(),
        val prnCollapsed: Boolean = false,
    ) {
        fun withToggledSection(partOfDay: PartOfDay): ViewState = copy(
            toggledSections = if (partOfDay in toggledSections) {
                toggledSections - partOfDay
            } else {
                toggledSections + partOfDay
            },
        )

        /** «По одному» только разворачивает: свернуть группу обратно макет не предлагает. */
        fun withExpandedGroup(time: LocalTime): ViewState = copy(expandedGroups = expandedGroups + time)
    }

    private companion object {

        /**
         * Сколько поток живёт без подписчиков.
         *
         * Пять секунд переживают поворот экрана и короткий уход в фон: иначе после
         * поворота экран пересобирается с нуля, а вместе с ним — проход по
         * просроченным приёмам.
         */
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
