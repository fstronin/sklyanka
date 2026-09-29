package ru.stohastika.sklyanka.feature.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import ru.stohastika.sklyanka.domain.reminder.SnoozeReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.port.ReminderCardSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Состояние полноэкранного напоминания и его действия (НАП-30).
 *
 * Отметка идёт **только** через [MarkIntakeUseCase] — единственную точку отметки в проекте.
 * Своих правил перехода статусов здесь нет ни одного: два пути записи означали бы два
 * способа обойти проверку статуса и двойное списание остатка в гонке «часы против телефона»
 * (ЧАС-24). Снятие уведомления и отзыв карточки часов выполняются последствиями отметки,
 * а не этим экраном.
 */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val cards: ReminderCardSource,
    private val mark: MarkIntakeUseCase,
    private val snooze: SnoozeReminderUseCase,
    private val settings: ReminderSettings,
) : ViewModel() {

    private val _state = MutableStateFlow<ReminderUiState>(ReminderUiState.Loading)
    val state: StateFlow<ReminderUiState> = _state.asStateFlow()

    private var slot: ReminderSlot? = null

    /** Карточка приёма: профиль и назначенное время приходят из будильника. */
    fun open(profileId: String, scheduledAt: LocalDateTime) {
        slot = ReminderSlot(profileId, scheduledAt)
        reload()
    }

    /** Пробное напоминание: приёма за ним нет, и отмечать в нём нечего (НАП-22). */
    fun openTest() {
        slot = null
        _state.value = ReminderUiState.Test
    }

    fun onTaken(intakeId: String) = markOne(intakeId, MarkResult.TAKEN)

    fun onSkipped(intakeId: String) = markOne(intakeId, MarkResult.SKIPPED)

    /** «Принято» на карточке из нескольких приёмов относится ко всем неотмеченным строкам. */
    fun onTakenAll() = markAll(MarkResult.TAKEN)

    fun onSkippedAll() = markAll(MarkResult.SKIPPED)

    /**
     * Откладывание: напоминание переносится на интервал настройки, приём остаётся
     * неотмеченным, плановое время не меняется (НАП-31).
     */
    fun onSnooze() {
        val current = _state.value as? ReminderUiState.Card ?: return
        viewModelScope.launch {
            current.items.forEach { snooze(it.intakeId, current.snoozeMinutes) }
            _state.value = ReminderUiState.Closed
        }
    }

    private fun markOne(intakeId: String, result: MarkResult) {
        viewModelScope.launch {
            mark(MarkRequest(intakeId = intakeId, result = result, source = IntakeSource.PHONE))
            reload()
        }
    }

    private fun markAll(result: MarkResult) {
        val current = _state.value as? ReminderUiState.Card ?: return
        viewModelScope.launch {
            current.items.forEach {
                mark(MarkRequest(intakeId = it.intakeId, result = result, source = IntakeSource.PHONE))
            }
            reload()
        }
    }

    /**
     * Перечитывание карточки после каждой отметки.
     *
     * Так закрывается и карточка, отмеченная с часов: слот перечитывается, и если
     * ожидающихся приёмов в нём не осталось, экран закрывается сам (НАП-32).
     */
    private fun reload() {
        val current = slot ?: return
        viewModelScope.launch {
            val values = settings.read()
            val card = cards.card(current, mirroredToWatch = false)
            _state.value = if (card == null || card.isEmpty) {
                ReminderUiState.Closed
            } else {
                card.toUiState(values.snoozeMinutes)
            }
        }
    }
}
