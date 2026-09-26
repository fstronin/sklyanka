package ru.heuri.sklyanka.feature.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.heuri.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.heuri.sklyanka.domain.reminder.ReminderDiagnostics
import ru.heuri.sklyanka.domain.reminder.RescheduleReason
import ru.heuri.sklyanka.domain.reminder.TestReminderUseCase
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/** Что показывает экран диагностики напоминаний (НАП-37). */
data class DiagnosticsUiState(
    val snapshot: ReminderDiagnostics? = null,
    val zone: ZoneId,
    /** Момент, на который поставлено пробное напоминание, — подтверждение нажатия (НАП-23). */
    val testPlannedAt: Instant? = null,
    val busy: Boolean = false,
)

/**
 * Диагностика напоминаний (НАП-37, НАП-39).
 *
 * Пересборку плана экран не делает сам: он зовёт ту же операцию, что зовут приёмники,
 * с причиной «ручное действие человека» (НАП-11). Наружу экран не отправляет ничего —
 * порта сети в проекте нет вовсе (ПЛ-3, НФТ-14).
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnostics: BuildReminderDiagnosticsUseCase,
    private val testReminder: TestReminderUseCase,
    private val check: ReminderCheckNow,
    time: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState(zone = time.zone()))
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(snapshot = diagnostics(), busy = false)
        }
    }

    /** «Проверить сейчас»: пересобрать окно и обновить показанное состояние (НАП-37). */
    fun onCheckNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            check(RescheduleReason.MANUAL)
            refresh()
        }
    }

    /** «Пробное напоминание через минуту» (НАП-22, НАП-23). */
    fun onTestReminder() {
        viewModelScope.launch {
            val at = testReminder()
            _state.value = _state.value.copy(testPlannedAt = at)
        }
    }

    /** Местное время момента: журнал хранит моменты в UTC, человек читает их в своём поясе. */
    fun local(instant: Instant): LocalDateTime = instant.atZone(_state.value.zone).toLocalDateTime()
}

/**
 * «Проверить сейчас» как зависимость экрана.
 *
 * Функциональный интерфейс, а не прямая зависимость на планировщик: полная пересборка
 * состоит из генерации приёмов и постановки будильников в строгом порядке, и знать этот
 * порядок обязан один класс в `app`, а не каждый экран (SKL-06, НАП-11).
 */
fun interface ReminderCheckNow {
    suspend operator fun invoke(reason: RescheduleReason)
}
