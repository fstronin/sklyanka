package ru.heuri.sklyanka.feature.meds.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import ru.heuri.sklyanka.domain.medication.MedicationListRules
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.repository.ActiveProfileRepository
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.time.TimeSource
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
) : ViewModel() {

    private val tab = MutableStateFlow(MedsTab.ACTIVE)

    val state: StateFlow<MedsUiState> = activeProfile.activeProfileId
        .flatMapLatest { profileId -> screen(profileId) }
        .combine(tab) { state, selected -> state.copy(tab = selected) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MedsUiState())

    fun onIntent(intent: MedsIntent) {
        when (intent) {
            is MedsIntent.SelectTab -> tab.value = intent.tab
            is MedsIntent.TakeNow -> viewModelScope.launch { takePrn(intent.medicationId) }
            // Переходы наружу состояния списка не меняют: их разбирает маршрут экрана.
            is MedsIntent.OpenMedication, MedsIntent.AddMedication -> Unit
        }
    }

    private fun screen(profileId: String): Flow<MedsUiState> = combine(
        cards(profileId),
        medications.observeArchived(profileId),
    ) { (active, history), archived ->
        MedsUiState(
            activeCount = MedicationListRules.activeCount(active),
            archiveCount = archived.size,
            items = active.map { medication -> card(medication, history) },
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
        strength = medication.strength?.stripTrailingZeros()?.toPlainString(),
        strengthUnit = medication.strengthUnit,
        timesPerDay = medication.times.size,
        times = medication.times.sorted(),
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
