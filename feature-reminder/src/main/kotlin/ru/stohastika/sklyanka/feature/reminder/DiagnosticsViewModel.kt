package ru.stohastika.sklyanka.feature.reminder

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderDiagnostics
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalExportOutcome
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.TestReminderUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
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
    /**
     * Имя предлагаемого файла выгрузки журнала (НФТ-51).
     *
     * Нужно **до** системного выбора места: имя предлагается системе, а решение о месте
     * остаётся за человеком, и приложение выбранный путь не запоминает (НФТ-17).
     */
    val exportFileName: String = "",
    /** Исход последней выгрузки журнала: человеку нельзя отвечать молчанием ни в одном исходе. */
    val exportOutcome: ReminderJournalExportOutcome? = null,
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
    private val exportJournal: ExportReminderJournalUseCase,
    time: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DiagnosticsUiState(zone = time.zone(), exportFileName = exportJournal.fileName()),
    )
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

    /**
     * «Выгрузить журнал»: запись в документ, выбранный человеком (НФТ-51).
     *
     * Ссылка на документ приходит строкой: о `Uri` домен не знает (НФТ-46), а само
     * место выгрузки нигде не сохраняется — приложение не ведёт учёта того, куда ушёл файл.
     */
    fun onExportTo(handle: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, exportOutcome = null)
            val outcome = exportJournal(ExportTarget(handle))
            _state.value = _state.value.copy(busy = false, exportOutcome = outcome)
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

/**
 * Вход в настройку разрешений заново — как зависимость экрана (НАП-21).
 *
 * Экран разрешений принадлежит SKL-14 и живёт в `feature-onboarding`, а экранные модули
 * друг от друга не зависят: решать, куда ведёт «Пройти настройку заново», обязан `app`
 * (`ADR-0007`). Точка расширения задана типом, поэтому подключение не потребует правок
 * экрана диагностики.
 */
fun interface ReminderSetupEntry {
    fun intentFor(context: Context): Intent
}
