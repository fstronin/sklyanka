package ru.stohastika.sklyanka.feature.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.analytics.ActionSource
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.analytics.IntakeAction
import ru.stohastika.sklyanka.domain.analytics.IntakeActionKind
import ru.stohastika.sklyanka.domain.analytics.ReminderSnoozed
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import ru.stohastika.sklyanka.domain.reminder.SnoozeReminderUseCase
import ru.stohastika.sklyanka.domain.reminder.port.ReminderCardSource
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
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
 *
 * Снэкбара на карточке нет (ОВ-81): после действия, закрывшего карточку, открывается
 * «Сегодня», и окно отмены публикуется в общий магазин. Частичная отметка строки карточку
 * не закрывает и окна не даёт — ни на карточке, ни на «Сегодня».
 */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val cards: ReminderCardSource,
    private val mark: MarkIntakeUseCase,
    private val snooze: SnoozeReminderUseCase,
    private val settings: ReminderSettings,
    /** Носитель последнего действия: снэкбар покажет «Сегодня» (ОВ-81, SKL-30). */
    private val undoActions: UndoableActionStore,
    /**
     * Запись событий отметки и откладывания (SKL-165, АН-1).
     *
     * Одно нажатие карточки — одно событие: «Принято для всех» отмечает несколько
     * приёмов, но действие человека одно. Событие ставится только по состоявшейся
     * отметке: проигравшая (`Ignored`) нового действия не образует.
     */
    private val analytics: AnalyticsRecorder,
) : ViewModel() {

    private val _state = MutableStateFlow<ReminderUiState>(ReminderUiState.Loading)
    val state: StateFlow<ReminderUiState> = _state.asStateFlow()

    private var slot: ReminderSlot? = null

    /** Карточка приёма: профиль и назначенное время приходят из будильника. */
    fun open(profileId: String, scheduledAt: LocalDateTime) {
        slot = ReminderSlot(profileId, scheduledAt)
        viewModelScope.launch { reload() }
    }

    /** Намерение без слота: показывать нечего, карточка закрывается (SKL-32). */
    fun close() {
        slot = null
        _state.value = ReminderUiState.Closed
    }

    fun onTaken(intakeId: String) = markOne(intakeId, MarkResult.TAKEN)

    fun onSkipped(intakeId: String) = markOne(intakeId, MarkResult.SKIPPED)

    /** «Принято» на карточке из нескольких приёмов относится ко всем неотмеченным строкам. */
    fun onTakenAll() = markAll(MarkResult.TAKEN)

    fun onSkippedAll() = markAll(MarkResult.SKIPPED)

    /**
     * Откладывание: напоминание переносится на интервал настройки, приём остаётся
     * неотмеченным, плановое время не меняется (НАП-31). Отменять нечего — окно не открывается.
     */
    fun onSnooze() {
        val current = _state.value as? ReminderUiState.Card ?: return
        viewModelScope.launch {
            current.items.forEach { snooze(it.intakeId, current.snoozeMinutes) }
            // Откладывание — самостоятельное действие: у действия своё имя в каталоге.
            analytics.record(ReminderSnoozed(ActionSource.PHONE))
            _state.value = ReminderUiState.Closed
        }
    }

    /**
     * Отметка одной строки: окно открывается, только если карточку закрыло **это** действие.
     *
     * Частичная отметка строки в карточке из нескольких приёмов карточку не закрывает —
     * публикуется ничего, и человек остаётся в том же напоминании (ОВ-81/ОВ-6, SKL-30).
     */
    private fun markOne(intakeId: String, result: MarkResult) {
        val state = _state.value as? ReminderUiState.Card ?: return
        val name = state.items.firstOrNull { it.intakeId == intakeId }?.name
        viewModelScope.launch {
            val outcome = mark(MarkRequest(intakeId = intakeId, result = result, source = IntakeSource.PHONE))
            if (outcome.isCompleted) analytics.record(IntakeAction(result.kind(), ActionSource.PHONE))
            val closed = reload()
            if (!closed || !outcome.isCompleted) return@launch
            undoActions.publishMark(
                intakeIds = listOf(intakeId),
                result = outcome.intake?.status?.let(MarkResult::of) ?: result,
                medicationNames = listOfNotNull(name),
            )
        }
    }

    /**
     * Отметка всех строк карточки: публикуются только состоявшиеся отметки действия —
     * приём, отмеченный часами в момент нажатия, в действие не входит (ПР-3). Если карточка
     * не закрылась или не состоялось ни одной отметки — не публикуется ничего.
     */
    private fun markAll(result: MarkResult) {
        val current = _state.value as? ReminderUiState.Card ?: return
        val names = current.items.associate { it.intakeId to it.name }
        viewModelScope.launch {
            val outcomes = current.items.map { item ->
                item.intakeId to mark(
                    MarkRequest(intakeId = item.intakeId, result = result, source = IntakeSource.PHONE),
                )
            }
            val completed = outcomes.filter { (_, outcome) -> outcome.isCompleted }
            // Одно нажатие на несколько строк — одно событие, а не событие на приём.
            // Записывается оно по состоявшимся отметкам, а не по закрытию карточки.
            if (completed.isNotEmpty()) analytics.record(IntakeAction(result.kind(), ActionSource.PHONE))
            val closed = reload()
            if (!closed || completed.isEmpty()) return@launch
            undoActions.publishMark(
                intakeIds = completed.map { it.first },
                result = result,
                medicationNames = completed.mapNotNull { (intakeId, _) -> names[intakeId] },
            )
        }
    }

    /** Вид отметки для каталога событий: у нажатия ровно два значения (АН-3). */
    private fun MarkResult.kind(): IntakeActionKind = when (this) {
        MarkResult.TAKEN -> IntakeActionKind.TAKEN
        MarkResult.SKIPPED -> IntakeActionKind.SKIPPED
    }

    /**
     * Перечитывание карточки после каждой отметки.
     *
     * Так закрывается и карточка, отмеченная с часов: слот перечитывается, и если
     * ожидающихся приёмов в нём не осталось, экран закрывается сам (НАП-32).
     *
     * @return закрылась ли карточка — этим решается, открывать ли окно отмены (SKL-30)
     */
    private suspend fun reload(): Boolean {
        val current = slot ?: return false
        val values = settings.read()
        val card = cards.card(current)
        return if (card == null || card.isEmpty) {
            _state.value = ReminderUiState.Closed
            true
        } else {
            _state.value = card.toUiState(values.snoozeMinutes)
            false
        }
    }
}
