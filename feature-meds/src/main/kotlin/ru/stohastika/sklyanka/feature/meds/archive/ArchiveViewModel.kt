package ru.stohastika.sklyanka.feature.meds.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.medication.ArchiveGrouping
import ru.stohastika.sklyanka.domain.medication.MedicationListRules
import ru.stohastika.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.RestoreOutcome
import ru.stohastika.sklyanka.domain.medication.UndoDeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.undo.UndoableAction
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import java.time.Duration
import javax.inject.Inject

/**
 * Состояние вкладки «Архив».
 *
 * Поток производный от активного профиля: архив показывает лекарства ровно одного
 * профиля, и запроса без фильтра по профилю здесь нет вовсе (ПР-10).
 *
 * Карточка исчезает из архива не потому, что её убрали из списка в разметке, а потому что
 * следующее значение потока её не содержит. Тем же значением пересчитываются оба
 * счётчика — иначе «Активные · 5» и «Архив · 12» разойдутся на одно нажатие.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModel @Inject constructor(
    activeProfile: ActiveProfileRepository,
    private val medications: MedicationRepository,
    private val intakes: IntakeRepository,
    private val restoreMedication: RestoreMedicationUseCase,
    private val time: TimeSource,
    /** Возврат удаления в коротком окне (SKL-99): очищает `deleted_at` и планирует заново. */
    private val undoDelete: UndoDeleteMedicationUseCase,
    /** Общая ячейка «последнее действие» (SKL-30/99). */
    private val undoActions: UndoableActionStore,
) : ViewModel() {

    private val notice = MutableStateFlow<ArchiveNotice?>(null)
    private val deletion = MutableStateFlow<UndoableDeletion?>(null)
    private var deletionJob: Job? = null

    val state: StateFlow<ArchiveUiState> = activeProfile.activeProfileId
        .flatMapLatest { profileId -> screen(profileId) }
        .combine(notice) { screen, pending -> screen.copy(notice = pending) }
        .combine(deletion) { screen, pending -> screen.copy(deletion = pending) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ArchiveUiState())

    init {
        viewModelScope.launch {
            undoActions.current.collect { action -> showDeletion(action) }
        }
    }

    fun onIntent(intent: ArchiveIntent) {
        when (intent) {
            is ArchiveIntent.Restore -> restore(intent.medicationId)
            is ArchiveIntent.UndoDeletion -> undoDeletion(intent.medicationId)
            ArchiveIntent.NoticeDismissed -> notice.value = null
            // Переходы наружу состояния архива не меняют: их разбирает маршрут экрана.
            ArchiveIntent.SelectActive, is ArchiveIntent.OpenMedication -> Unit
        }
    }

    /**
     * Уход с экрана закрывает окно удаления: возврата через интерфейс больше нет
     * (правило SKL-30, ОВ-124). Зовёт маршрут архива при снятии с композиции.
     */
    fun onScreenLeft() {
        deletionJob?.cancel()
        deletionJob = null
        deletion.value = null
        undoActions.clear()
    }

    private fun showDeletion(action: UndoableAction?) {
        deletionJob?.cancel()
        deletionJob = null
        val shown = action as? UndoableDeletion
        if (shown == null) {
            deletion.value = null
            return
        }
        val remaining = Duration.between(time.now(), shown.expiresAt)
        if (remaining.isNegative || remaining.isZero) {
            deletion.value = null
            undoActions.clearIf(shown)
            return
        }
        deletion.value = shown
        deletionJob = viewModelScope.launch {
            delay(remaining.toMillis())
            deletion.value = null
            undoActions.clearIf(shown)
        }
    }

    private fun undoDeletion(medicationId: String) {
        deletionJob?.cancel()
        deletionJob = null
        deletion.value = null
        undoActions.clear()
        viewModelScope.launch { undoDelete(medicationId) }
    }

    private fun screen(profileId: String): Flow<ArchiveUiState> = combine(
        medications.observeActive(profileId),
        archiveWithHistory(profileId),
    ) { active, (archived, history) ->
        val content = ArchiveGrouping.of(archived, history, time.zone())
        ArchiveUiState(
            // Счётчик активных считает только курсы по расписанию (ЭКР-3) — тем же
            // правилом, что и вкладка активных: два подсчёта того же числа разойдутся.
            activeCount = MedicationListRules.activeCount(active),
            archiveCount = content.total,
            content = content.takeUnless { it.isEmpty },
            loading = false,
        )
    }

    /**
     * Архивные лекарства вместе с их приёмами.
     *
     * История читается одним запросом на весь архив, а не по запросу на карточку: итог
     * каждого завершённого курса считается из тех же записей, и несколько независимых
     * чтений разошлись бы между собой.
     *
     * Пустой архив запроса истории не делает вовсе: считать нечего, а окно от «самой
     * ранней даты начала» без лекарств не определено.
     */
    private fun archiveWithHistory(profileId: String): Flow<Pair<List<Medication>, List<Intake>>> =
        medications.observeArchived(profileId).flatMapLatest { archived ->
            if (archived.isEmpty()) {
                flowOf(archived to emptyList())
            } else {
                intakes.observePeriod(profileId, archived.minOf { it.startDate }, time.today())
                    .map { history -> archived to history }
            }
        }

    private fun restore(medicationId: String) {
        viewModelScope.launch {
            notice.value = when (restoreMedication(medicationId)) {
                is RestoreOutcome.NeedsPeriodUpdate -> ArchiveNotice.PeriodNeedsUpdate
                else -> null
            }
        }
    }
}
