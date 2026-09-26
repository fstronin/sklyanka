package ru.heuri.sklyanka.feature.meds.card

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.heuri.sklyanka.domain.medication.CourseCompletion
import ru.heuri.sklyanka.domain.medication.CourseProgress
import ru.heuri.sklyanka.domain.medication.DayCell
import ru.heuri.sklyanka.domain.medication.DoseUnit
import ru.heuri.sklyanka.domain.medication.MedicationSubtitle
import ru.heuri.sklyanka.domain.medication.RestoreOutcome
import ru.heuri.sklyanka.domain.medication.WeekAdherence
import ru.heuri.sklyanka.domain.medication.doseUnit
import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.repository.ActiveProfileRepository
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.stock.RefillOutcome
import ru.heuri.sklyanka.domain.stock.StockPanel
import ru.heuri.sklyanka.domain.time.TimeSource
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Состояние карточки одного лекарства.
 *
 * Поток производный от активного профиля и от самого лекарства: правка формой (SKL-09),
 * отметка приёма (SKL-07) и пополнение остатка (SKL-12) доходят до карточки сами, и
 * перечитывать её отдельно не нужно.
 *
 * Лекарство запрашивается **в паре с профилем** (ПР-10): идентификатор приходит из
 * навигации, то есть из внешнего мира, и карточка лекарства чужого профиля не
 * открывается.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationCardViewModel @Inject constructor(
    private val activeProfile: ActiveProfileRepository,
    private val medications: MedicationRepository,
    private val intakes: IntakeRepository,
    private val operations: MedCardOperations,
    private val time: TimeSource,
) : ViewModel() {

    private val medicationId = MutableStateFlow<String?>(null)
    private val local = MutableStateFlow(LocalState())

    /** `null` — лекарство ещё не прочитано либо принадлежит другому профилю (ПР-10). */
    val state: StateFlow<MedCardUiState?> = medicationId
        .flatMapLatest { id -> if (id == null) flowOf(null) else card(id) }
        .combine(local) { screen, overlay -> screen?.with(overlay) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Открыть карточку. Вызывается маршрутом при входе на экран. */
    fun open(id: String) {
        medicationId.value = id
        local.value = LocalState()
    }

    fun onIntent(intent: MedCardIntent) {
        when (intent) {
            is MedCardIntent.RefillRequested -> local.value = local.value.copy(refill = RefillUi(text = ""))
            is MedCardIntent.RefillValueChanged ->
                local.value = local.value.copy(refill = RefillUi(text = intent.text))

            MedCardIntent.RefillCancelled -> local.value = local.value.copy(refill = null)
            MedCardIntent.RefillConfirmed -> refill()
            is MedCardIntent.WatchMirrorToggled -> mirror(intent.enabled)
            MedCardIntent.Archive -> archive(ArchiveReason.MANUAL)
            MedCardIntent.ArchiveFinishedCourse -> archive(ArchiveReason.COURSE_FINISHED)
            MedCardIntent.Restore -> restore()
            MedCardIntent.NoticeDismissed -> local.value = local.value.copy(notice = null)
            // Переходы наружу состояния карточки не меняют: их разбирает маршрут экрана.
            MedCardIntent.Back, MedCardIntent.Edit -> Unit
        }
    }

    /**
     * Карточка одного лекарства вместе с его неделей приёмов.
     *
     * Окно недели — семь календарных дней, а не «минус 168 часов»: в неделе с переводом
     * времени часов не 168 (НФТ-37).
     */
    private fun card(id: String): Flow<MedCardUiState?> = activeProfile.activeProfileId.flatMapLatest { profileId ->
        val today = time.today()
        val from = today.minusDays((DayCell.WEEK_LENGTH - 1).toLong())
        combine(
            medications.observeActive(profileId),
            medications.observeArchived(profileId),
            intakes.observeByMedication(profileId, id, from, today),
        ) { active, archived, history ->
            (active + archived).firstOrNull { it.id == id }?.let { medication -> screen(medication, history) }
        }
    }

    private fun screen(medication: Medication, history: List<Intake>): MedCardUiState {
        val today = time.today()
        val scheduled = medication.scheduleMode == ScheduleMode.SCHEDULED
        return MedCardUiState(
            medicationId = medication.id,
            title = medication.name,
            form = medication.form,
            subtitle = MedicationSubtitle.of(medication),
            schedule = scheduleOf(medication),
            course = CourseProgress.of(medication, today),
            // Остаток приходит готовым из SKL-12: считать его заново значит завести
            // второй ответ на тот же вопрос.
            stock = StockPanel.of(medication),
            stockUnit = DoseUnit.ofCode(medication.doseUnit) ?: medication.form.doseUnit(),
            week = WeekAdherence.of(medication, history, today),
            watchMirror = if (scheduled) {
                WatchMirrorUi(checked = medication.mirrorToWatch, enabled = !medication.isArchived)
            } else {
                null
            },
            isArchived = medication.isArchived,
            courseFinishedOffer = CourseCompletion.isFinished(medication, today),
            refill = null,
            notice = null,
        )
    }

    private fun scheduleOf(medication: Medication): ScheduleBlockUi =
        if (medication.scheduleMode == ScheduleMode.SCHEDULED) {
            ScheduleBlockUi.Scheduled(
                timesPerDay = medication.times.size,
                doseAmount = medication.doseAmount,
                doseUnit = DoseUnit.ofCode(medication.doseUnit) ?: medication.form.doseUnit(),
                times = medication.times.sorted(),
            )
        } else {
            ScheduleBlockUi.AsNeeded(maxPerDay = medication.maxPerDay ?: 0)
        }

    private fun MedCardUiState.with(overlay: LocalState): MedCardUiState =
        copy(refill = overlay.refill, notice = overlay.notice, closed = overlay.closed)

    private fun archive(reason: ArchiveReason) {
        val id = medicationId.value ?: return
        viewModelScope.launch {
            operations.archive(id, reason)
            // Человек возвращается в список лекарств; карточку закрывает маршрут.
            local.value = local.value.copy(closed = true)
        }
    }

    private fun restore() {
        val id = medicationId.value ?: return
        viewModelScope.launch {
            val notice = when (operations.restore(id)) {
                is RestoreOutcome.NeedsPeriodUpdate -> MedCardNotice.PeriodNeedsUpdate
                else -> null
            }
            local.value = local.value.copy(notice = notice)
        }
    }

    private fun mirror(enabled: Boolean) {
        val id = medicationId.value ?: return
        viewModelScope.launch { operations.watchMirror(id, enabled) }
    }

    /**
     * Пополнение остатка.
     *
     * Значение разбирается так же, как в форме лекарства: запятая — десятичный разделитель
     * русской раскладки, и «0,5» обязано означать половину, а не отказ.
     */
    private fun refill() {
        val id = medicationId.value ?: return
        val text = local.value.refill?.text.orEmpty()
        val value = text.trim().replace(',', '.').toBigDecimalOrNull()
        if (value == null || value < BigDecimal.ZERO) {
            local.value = local.value.copy(refill = RefillUi(text = text, invalid = true))
            return
        }
        viewModelScope.launch {
            val outcome = operations.refillStock(id, value)
            local.value = if (outcome is RefillOutcome.Applied) {
                local.value.copy(refill = null)
            } else {
                local.value.copy(refill = RefillUi(text = text, invalid = true), notice = MedCardNotice.StockNegative)
            }
        }
    }

    /**
     * Состояние, которого нет в базе: открытый ввод остатка, объяснение и признак закрытия.
     *
     * Отдельно от потока лекарства намеренно: иначе каждое обновление базы затирало бы
     * набранное человеком число.
     */
    private data class LocalState(
        val refill: RefillUi? = null,
        val notice: MedCardNotice? = null,
        val closed: Boolean = false,
    )
}
