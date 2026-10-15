package ru.stohastika.sklyanka.feature.meds.list

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
import ru.stohastika.sklyanka.domain.medication.MedicationListOrder
import ru.stohastika.sklyanka.domain.medication.MedicationListRules
import ru.stohastika.sklyanka.domain.medication.UndoDeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.undo.UndoableAction
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import ru.stohastika.sklyanka.feature.meds.form.ScheduleKind
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

/**
 * Состояние экрана «Лекарства».
 *
 * Поток производный от активного профиля: список пересобирается сам, когда профиль
 * переключили, и запроса без фильтра по профилю здесь нет вовсе (ПР-10).
 *
 * Архивированные лекарства в списке активных не показываются (ПР-11) — не фильтрацией
 * на экране, а выборкой слоя данных: фильтр на экране однажды забудут применить.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationListViewModel @Inject constructor(
    activeProfile: ActiveProfileRepository,
    private val medications: MedicationRepository,
    private val intakes: IntakeRepository,
    private val time: TimeSource,
    private val takePrn: TakePrnAction,
    /** Возврат удаления в коротком окне (SKL-99): очищает `deleted_at` и планирует заново. */
    private val undoDelete: UndoDeleteMedicationUseCase,
    /** Общая ячейка «последнее действие» (SKL-30/99). */
    private val undoActions: UndoableActionStore,
) : ViewModel() {

    private val tab = MutableStateFlow(MedsTab.ACTIVE)
    private val deletion = MutableStateFlow<UndoableDeletion?>(null)
    private var deletionJob: Job? = null

    val state: StateFlow<MedsUiState> = activeProfile.activeProfileId
        .flatMapLatest { profileId -> screen(profileId) }
        .combine(tab) { state, selected -> state.copy(tab = selected) }
        .combine(deletion) { state, pending -> state.copy(deletion = pending) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MedsUiState())

    init {
        viewModelScope.launch {
            undoActions.current.collect { action -> showDeletion(action) }
        }
    }

    fun onIntent(intent: MedsIntent) {
        when (intent) {
            is MedsIntent.SelectTab -> tab.value = intent.tab
            is MedsIntent.TakeNow -> viewModelScope.launch { takePrn(intent.medicationId) }
            is MedsIntent.UndoDeletion -> undoDeletion(intent.medicationId)
            // Переходы наружу состояния списка не меняют: их разбирает маршрут экрана.
            is MedsIntent.OpenMedication, MedsIntent.AddMedication -> Unit
        }
    }

    /**
     * Уход с экрана закрывает окно удаления: возврата через интерфейс больше нет
     * (правило SKL-30, ОВ-124). Зовёт маршрут списка при снятии с композиции.
     */
    fun onScreenLeft() {
        deletionJob?.cancel()
        deletionJob = null
        deletion.value = null
        undoActions.clear()
    }

    /** Показ окна по дедлайну магазина: истёкшее окно не показывается вовсе. */
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

    /**
     * «Отменить»: окно закрывается синхронно, до запуска возврата, — новая команда
     * в тот же миг открывает новое окно, а не ждёт завершения старой.
     */
    private fun undoDeletion(medicationId: String) {
        deletionJob?.cancel()
        deletionJob = null
        deletion.value = null
        undoActions.clear()
        viewModelScope.launch { undoDelete(medicationId) }
    }

    private fun screen(profileId: String): Flow<MedsUiState> = combine(
        cards(profileId),
        medications.observeArchived(profileId),
    ) { (active, history), archived ->
        MedsUiState(
            activeCount = MedicationListRules.activeCount(active),
            archiveCount = archived.size,
            // «Что ближе — то выше» (ПМ-15 п. 1): порядок считает домен, а не разметка.
            items = MedicationListOrder.sorted(active, time.nowLocal()).map { medication -> card(medication, history) },
            loading = false,
        )
    }

    /**
     * Активные лекарства вместе с историей приёмов «по потребности».
     *
     * История читается одним запросом на весь список, а не по запросу на карточку:
     * строка «последний приём» и доступность кнопки «Принять» — два взгляда на одни
     * и те же записи, и два независимых чтения разошлись бы между собой.
     */
    private fun cards(profileId: String): Flow<Pair<List<Medication>, List<Intake>>> =
        medications.observeActive(profileId).flatMapLatest { active ->
            val asNeeded = active.filter { it.scheduleMode == ScheduleMode.PRN }
            if (asNeeded.isEmpty()) {
                flowOf(active to emptyList())
            } else {
                intakes.observePeriod(profileId, asNeeded.minOf { it.startDate }, time.today())
                    .map { history -> active to history }
            }
        }

    private fun card(medication: Medication, history: List<Intake>): MedicationCardUi = MedicationCardUi(
        medicationId = medication.id,
        title = medication.name,
        form = medication.form,
        shape = medication.shape,
        strength = medication.strength?.stripTrailingZeros()?.toPlainString(),
        strengthUnit = medication.strengthUnit,
        timesPerDay = medication.times.size,
        times = medication.times.sorted(),
        scheduleKind = ScheduleKind.of(medication.schedule),
        trailingLine = MedicationListRules.trailingLineOf(medication),
        asNeeded = asNeededOf(medication, history),
    )

    private fun asNeededOf(medication: Medication, history: List<Intake>): AsNeededCardUi? {
        val limit = medication.maxPerDay.takeIf { medication.scheduleMode == ScheduleMode.PRN }
        return limit?.let {
            val taken = history.filter { it.medicationId == medication.id && it.status == IntakeStatus.TAKEN }
            val today: LocalDate = time.today()
            AsNeededCardUi(
                maxPerDay = limit,
                lastIntakeDate = taken.maxOfOrNull { intake -> intake.scheduledAt }?.toLocalDate(),
                // Ограничение считается по назначенному времени: у приёма по потребности оно
                // равно фактическому (ИНВ-14), поэтому сутки определяются однозначно.
                takeEnabled = taken.count { intake -> intake.scheduledAt.toLocalDate() == today } < limit,
            )
        }
    }
}
