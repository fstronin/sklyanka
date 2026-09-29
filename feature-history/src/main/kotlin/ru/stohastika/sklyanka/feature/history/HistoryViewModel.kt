package ru.stohastika.sklyanka.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.history.AdherencePeriod
import ru.stohastika.sklyanka.domain.history.BuildDoctorReportUseCase
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.ObserveAdherenceUseCase
import ru.stohastika.sklyanka.domain.history.ReportTarget
import ru.stohastika.sklyanka.domain.history.ReportTransferOutcome
import ru.stohastika.sklyanka.domain.history.TransferDoctorReportUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.YearMonth
import javax.inject.Inject

/**
 * Состояние экрана истории.
 *
 * Профиль берётся из контекста активного профиля, а не из аргумента экрана: у экрана нет
 * способа попросить историю чужого профиля (ПР-10). Признаки чужого профиля состояние
 * не несёт вовсе — их даёт оформление профиля в корне приложения (`ProfileChrome`) и
 * общая шапка. Флаг «показывать индикацию» в состоянии экрана означал бы место, где
 * защиту можно выключить одним значением (ПРФ-12, ПРФ-16).
 *
 * Месяц по умолчанию — текущий, и «текущий» берётся из источника времени, а не из
 * системных часов: иначе первый кадр экрана непроверяем (НФТ-48).
 *
 * Переключение месяца пересчитывает всё целиком (`TDD.md §9.2`). Частичное обновление
 * здесь дало бы сводку одного месяца рядом с календарём другого — то есть число,
 * которого не было ни в одном месяце.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel @Inject constructor(
    private val observeAdherence: ObserveAdherenceUseCase,
    private val buildReport: BuildDoctorReportUseCase,
    private val transferReport: TransferDoctorReportUseCase,
    private val renderer: DoctorReportRenderer,
    private val missedPattern: MissedPatternRule,
    private val time: TimeSource,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(time.today()))
    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<HistoryUiState> = combine(
        month.flatMapLatest { selected -> observeAdherence(selected) },
        local,
    ) { period, overlay -> screen(period, overlay) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, opening())

    fun onIntent(intent: HistoryIntent) {
        when (intent) {
            HistoryIntent.PreviousMonth -> month.value = month.value.minusMonths(1)
            HistoryIntent.NextMonth -> month.value = month.value.plusMonths(1)
            HistoryIntent.ReportRequested -> prepareReport()
            HistoryIntent.ReportDismissed -> local.value = local.value.copy(report = null)
            HistoryIntent.ShareReport -> share()
            is HistoryIntent.SaveTo -> save(ReportTarget(intent.target))
            HistoryIntent.SaveCancelled -> cancelSave()
            HistoryIntent.NoticeDismissed -> local.value = local.value.copy(notice = null)
            // Переход наружу и системный выбор места экран не исполняет: первое решает
            // маршрут, второе — системный диалог, чей результат приходит обратно как SaveTo.
            HistoryIntent.Back, HistoryIntent.SaveRequested -> Unit
        }
    }

    /**
     * Сводка месяца плюс то, чего в ней нет: строка-вывод и признаки профиля.
     *
     * Месяц берётся из посчитанного периода, а не из [month]: иначе между нажатием
     * стрелки и приходом новых данных на экране оказался бы заголовок одного месяца
     * над числами другого.
     */
    private fun screen(period: AdherencePeriod, overlay: LocalState) = HistoryUiState(
        month = YearMonth.from(period.from),
        period = period,
        insightTime = missedPattern.dominantMissedTime(period),
        report = overlay.report,
        notice = overlay.notice,
    )

    /** Первый кадр до первого значения потока: месяц уже виден, чисел ещё нет. */
    private fun opening(): HistoryUiState {
        val window = HistoryWindow.of(month.value)
        return HistoryUiState(
            month = month.value,
            period = AdherencePeriod.empty(profileId = "", from = window.from, to = window.to),
        )
    }

    private fun prepareReport() {
        viewModelScope.launch {
            val report = buildReport(month.value) ?: return@launch
            local.value = local.value.copy(report = ReportUi(renderer.render(report, time.zone())))
        }
    }

    private fun share() {
        val document = local.value.report?.document ?: return
        viewModelScope.launch { notice(transferReport.share(document)) }
    }

    private fun save(target: ReportTarget) {
        val document = local.value.report?.document ?: return
        viewModelScope.launch { notice(transferReport.save(document, target)) }
    }

    private fun cancelSave() {
        val document = local.value.report?.document ?: return
        viewModelScope.launch { notice(transferReport.cancelled(document)) }
    }

    /**
     * Исход передачи — человеку, а не только в журнал.
     *
     * Отмена системного диалога выглядит для человека точно так же, как ошибка записи,
     * и промолчать здесь значит оставить жалобу «нажал, и ничего не произошло». Отчёт
     * при успехе закрывается, при отмене остаётся открытым: человек хотел передать его
     * и, скорее всего, попробует снова.
     */
    private fun notice(outcome: ReportTransferOutcome) {
        local.value = when (outcome) {
            ReportTransferOutcome.Shared -> local.value.copy(report = null, notice = HistoryNotice.REPORT_SHARED)
            ReportTransferOutcome.Saved -> local.value.copy(report = null, notice = HistoryNotice.REPORT_SAVED)
            ReportTransferOutcome.Cancelled -> local.value.copy(notice = HistoryNotice.REPORT_CANCELLED)
            is ReportTransferOutcome.Failed -> local.value.copy(notice = HistoryNotice.REPORT_FAILED)
        }
    }

    /**
     * Состояние, которого нет в базе: открытый отчёт и исход его передачи.
     *
     * Отдельно от потока сводки намеренно: иначе любое обновление базы — например,
     * отметка приёма с часов — закрывало бы открытый отчёт под рукой человека.
     */
    private data class LocalState(val report: ReportUi? = null, val notice: HistoryNotice? = null)
}
