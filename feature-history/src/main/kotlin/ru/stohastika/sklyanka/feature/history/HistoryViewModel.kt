package ru.stohastika.sklyanka.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.history.AdherencePeriod
import ru.stohastika.sklyanka.domain.history.BuildDoctorReportUseCase
import ru.stohastika.sklyanka.domain.history.BuildExtendedSectionsUseCase
import ru.stohastika.sklyanka.domain.history.ExtendedReportSections
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.ObserveAdherenceUseCase
import ru.stohastika.sklyanka.domain.history.ReportPeriodRules
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.domain.history.ReportScope
import ru.stohastika.sklyanka.domain.history.ReportShareLauncher
import ru.stohastika.sklyanka.domain.history.ReportTarget
import ru.stohastika.sklyanka.domain.history.ReportTransferOutcome
import ru.stohastika.sklyanka.domain.history.TransferDoctorReportUseCase
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.LocalDate
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
 *
 * **Период отчёта врачу отвязан от месяца «Истории»** (ОВ-125): чипы шторки задают
 * диапазон сами, и отчёт собирается тем же расчётом, что экран.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
class HistoryViewModel @Inject constructor(
    private val observeAdherence: ObserveAdherenceUseCase,
    private val buildReport: BuildDoctorReportUseCase,
    /** Разделы «Про» расширенного состава: считаются только по запросу выбранного состава (Р-22). */
    private val buildExtended: BuildExtendedSectionsUseCase,
    private val transferReport: TransferDoctorReportUseCase,
    private val renderer: DoctorReportRenderer,
    private val missedPattern: MissedPatternRule,
    /**
     * Доступ «Склянка Про»: им решается, доступен ли расширенный состав (Р-85 п. 1).
     *
     * Читается при открытии шторки и перечитывается по возвращении из витрины
     * ([refreshProAccess]): гейт решает по подписке, а не по нажатию, и экран в магазин
     * не ходит (Р-7, Р-22).
     */
    private val proStatus: ObserveProStatusUseCase,
    private val time: TimeSource,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(time.today()))
    private val local = MutableStateFlow(LocalState())

    /**
     * Показ системного выбора получателя.
     *
     * Приходит из разметки: результат системного диалога умеет получить только она.
     * До подстановки передавать нечем — попытка честно кончается отказом.
     */
    private var shareLauncher: ReportShareLauncher? = null

    private val _showcase = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Сигнал «открыть витрину»: без активного доступа расширенный состав её и открывает (Р-22).
     *
     * Событие, а не состояние: витрина — одна поверхность на все точки входа (Р-67), и показывает
     * её `app`; экран истории только просит. Выбор состава при этом не меняется: выбранный состав
     * и есть то, что напечатают (§5.15).
     */
    val showcase: SharedFlow<Unit> = _showcase.asSharedFlow()

    /** Связать передачу с системным выбором получателя, который живёт на экране. */
    fun attachShareLauncher(launcher: ReportShareLauncher) {
        shareLauncher = launcher
    }

    /**
     * Перечитать доступ «Про» после закрытия витрины (Р-85 п. 1).
     *
     * Шторка держит снимок доступа с момента открытия, а покупка в витрине пишет кэш подписки:
     * без перечитывания повторное нажатие расширенной строки снова открывало бы витрину вместо
     * выбора состава (тот же заход, что у формы лекарства, SKL-117). Состав при этом не
     * пересобирается сам: §5.15 требует повторить нажатие — иначе человек получил бы документ,
     * которого не просил.
     */
    fun refreshProAccess() {
        viewModelScope.launch { local.value = local.value.copy(proIncluded = proStatus().included) }
    }

    val state: StateFlow<HistoryUiState> = combine(
        month.flatMapLatest { selected -> observeAdherence(selected) },
        local,
    ) { period, overlay -> screen(period, overlay) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, opening())

    fun onIntent(intent: HistoryIntent) {
        when (intent) {
            HistoryIntent.PreviousMonth -> month.value = month.value.minusMonths(1)
            HistoryIntent.NextMonth -> month.value = month.value.plusMonths(1)
            HistoryIntent.ReportRequested -> openReport()
            HistoryIntent.ReportDismissed -> local.value = LocalState()
            is HistoryIntent.ReportPresetChosen -> choosePreset(intent.preset)
            is HistoryIntent.ReportScopeChosen -> chooseScope(intent.scope)
            is HistoryIntent.ReportStartPicked -> pickStart(intent.date)
            is HistoryIntent.ReportEndPicked -> pickEnd(intent.date)
            HistoryIntent.ShareReport -> share()
            is HistoryIntent.SaveTo -> save(ReportTarget(intent.target))
            HistoryIntent.SaveCancelled -> cancelSave()
            HistoryIntent.SaveUnavailable -> saveUnavailable()
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
        reportOutcome = overlay.outcome,
    )

    /** Первый кадр до первого значения потока: месяц уже виден, чисел ещё нет. */
    private fun opening(): HistoryUiState {
        val window = HistoryWindow.of(month.value)
        return HistoryUiState(
            month = month.value,
            period = AdherencePeriod.empty(profileId = "", from = window.from, to = window.to),
        )
    }

    /**
     * «Отчёт врачу»: чистое открытие — базовый состав, пресет по умолчанию, свежие даты.
     *
     * Доступ читается здесь же: от него зависит, что произойдёт при нажатии расширенной строки,
     * а не какой документ откроется — базовый формируется без единого нажатия в блоке выбора
     * и от подписки не зависит вовсе (Р-36).
     */
    private fun openReport() {
        val defaults = ReportPeriodRules.customDefaults(time.today())
        viewModelScope.launch {
            val included = proStatus().included
            local.value = local.value.copy(proIncluded = included)
            rebuild(Selection(preset = ReportPreset.THIS_MONTH, from = defaults.from, to = defaults.to))
        }
    }

    /**
     * Выбор состава в шторке: «Базовый» — всегда, «Расширенный» — только с активным доступом.
     *
     * Без доступа выбор **не меняется** и открывается витрина: недоступное действие говорит
     * «нельзя», а правда — «можно, по подписке» (П-7, §5.15). После отмены подписки базовый
     * состав работает, расширенный закрыт (Р-22). В поставляемой бесплатной сборке доступ есть
     * всегда (Р-86, ADR-0016): ветка с витриной остаётся спящей для будущего `pro`-варианта.
     */
    private fun chooseScope(scope: ReportScope) {
        val current = local.value.selection ?: return
        when {
            // Без доступа выбор не меняется: витрину просят, состав остаётся прежним (П-7).
            scope == ReportScope.EXTENDED && !local.value.proIncluded -> _showcase.tryEmit(Unit)
            current.scope == scope -> Unit
            else -> rebuild(current.copy(scope = scope))
        }
    }

    /**
     * Смена пресета: «Другой период» возвращается к прежним датам в пределах открытой
     * шторки, а при первом выборе — к значениям по умолчанию (ОВ-125).
     */
    private fun choosePreset(preset: ReportPreset) {
        val current = local.value.selection ?: return
        val next = if (preset == ReportPreset.CUSTOM && current.preset != ReportPreset.CUSTOM) {
            val defaults = ReportPeriodRules.customDefaults(time.today())
            current.copy(preset = preset, from = defaults.from, to = defaults.to)
        } else {
            current.copy(preset = preset)
        }
        rebuild(next)
    }

    private fun pickStart(date: LocalDate) {
        val current = local.value.selection ?: return
        val window = ReportPeriodRules.withStart(date, HistoryWindow(current.from, current.to), time.today())
        rebuild(current.copy(preset = ReportPreset.CUSTOM, from = window.from, to = window.to))
    }

    private fun pickEnd(date: LocalDate) {
        val current = local.value.selection ?: return
        val window = ReportPeriodRules.withEnd(date, HistoryWindow(current.from, current.to), time.today())
        rebuild(current.copy(preset = ReportPreset.CUSTOM, from = window.from, to = window.to))
    }

    /**
     * Сборка отчёта за период — тем же расчётом, что экран истории.
     *
     * Сообщение исхода сбрасывается: оно относилось к прошлому составу, и оставить его
     * рядом с новым числом значит сказать человеку неправду (ОВ-125).
     *
     * **Прежний состав остаётся на месте, пока не готов новый.** Обнуление здесь
     * заставляло шторку исчезнуть и вернуться: она дёргалась, теряла прокрутку и
     * выглядела «не нажавшейся» (SKL-101, дефект 1). Мелкая гонка двух быстрых
     * нажатий решается сверкой выбора: применяется результат только последнего.
     */
    private fun rebuild(selection: Selection) {
        local.value = local.value.copy(outcome = null, selection = selection)
        viewModelScope.launch {
            val window = windowOf(selection)
            val report = buildReport(window) ?: return@launch
            // Сборку обогнал более новый выбор — её результат уже не актуален,
            // и рисовать документ ради выброшенной версии не нужно.
            if (local.value.selection != selection) return@launch
            // Разделы «Про» считаются только для расширенного состава и только с активным
            // доступом: базовый путь кода от выбора не меняется вовсе (Р-36), а расширенный
            // без подписки не собирается (Р-22).
            val extended = if (selection.scope == ReportScope.EXTENDED && local.value.proIncluded) {
                buildExtended(window)
            } else {
                ExtendedReportSections.NONE
            }
            val zone = time.zone()
            local.value = local.value.copy(
                report = ReportUi(
                    document = renderer.render(report, zone, extended),
                    // Состав собирается тем же композером, что и PDF: двух версий одного
                    // содержания в продукте не остаётся.
                    composition = renderer.composition(report),
                    scope = selection.scope,
                    preset = selection.preset,
                    customFrom = selection.from,
                    customTo = selection.to,
                    today = time.today(),
                ),
            )
        }
    }

    private fun windowOf(selection: Selection): HistoryWindow = if (selection.preset == ReportPreset.CUSTOM) {
        HistoryWindow(selection.from, selection.to)
    } else {
        ReportPeriodRules.window(selection.preset, time.today())
    }

    /**
     * Передать отчёт системным обменом.
     *
     * Исход ставится только после решения системного выбора: пока диалог открыт,
     * результат неизвестен (SKL-101, дефект 4). Без обработчика на экране передавать
     * нечем — честный отказ, как у выбора места.
     */
    private fun share() {
        val report = local.value.report ?: return
        val launcher = shareLauncher
        viewModelScope.launch {
            val outcome = if (launcher == null) {
                transferReport.shareUnavailable(report.document)
            } else {
                transferReport.share(report.document, launcher)
            }
            outcome(outcome, onFailure = ReportOutcome.FAILED_SHARE)
        }
    }

    private fun save(target: ReportTarget) {
        val report = local.value.report ?: return
        viewModelScope.launch { outcome(transferReport.save(report.document, target), ReportOutcome.FAILED_SAVE) }
    }

    private fun cancelSave() {
        val report = local.value.report ?: return
        viewModelScope.launch { outcome(transferReport.cancelled(report.document), ReportOutcome.FAILED_SAVE) }
    }

    /**
     * Сохранить некому: системного выбора места в устройстве нет.
     *
     * Исход тот же, что у сорванной записи, — отказ, — но причина другая: диалог
     * не открывался. Шторка остаётся открытой: человек хотел сохранить и вправе
     * попробовать снова (SKL-46).
     */
    private fun saveUnavailable() {
        val report = local.value.report ?: return
        viewModelScope.launch { outcome(transferReport.saveUnavailable(report.document), ReportOutcome.FAILED_SAVE) }
    }

    /**
     * Исход передачи — человеку, а не только в журнал.
     *
     * Отмена системного диалога выглядит для человека точно так же, как ошибка записи,
     * и промолчать здесь значит оставить жалобу «нажал, и ничего не произошло». Успех
     * показывается отдельным состоянием шторки, отмена и ошибка — сообщением над составом:
     * шторка не закрывается ни в одном из исходов (ОВ-125).
     */
    private fun outcome(outcome: ReportTransferOutcome, onFailure: ReportOutcome) {
        val shown = outcomeOf(outcome, onFailure)
        local.value = local.value.copy(outcome = shown)
    }

    private fun outcomeOf(outcome: ReportTransferOutcome, onFailure: ReportOutcome): ReportOutcome = when (outcome) {
        ReportTransferOutcome.Shared -> ReportOutcome.SHARED
        ReportTransferOutcome.Saved -> ReportOutcome.SAVED
        ReportTransferOutcome.Cancelled -> ReportOutcome.CANCELLED
        is ReportTransferOutcome.Failed -> onFailure
    }

    /** Выбранный состав и период шторки: пресет, границы «Другого периода» и выбор состава. */
    private data class Selection(
        val preset: ReportPreset,
        val from: LocalDate,
        val to: LocalDate,
        val scope: ReportScope = ReportScope.BASE,
    )

    /**
     * Состояние, которого нет в базе: открытый отчёт, выбранный период и исход передачи.
     *
     * Отдельно от потока сводки намеренно: иначе любое обновление базы — например,
     * отметка приёма с часов — закрывало бы открытый отчёт под рукой человека.
     */
    private data class LocalState(
        val report: ReportUi? = null,
        val outcome: ReportOutcome? = null,
        val selection: Selection? = null,
        /**
         * Снимок доступа «Про» на момент открытия шторки.
         *
         * Хранится здесь, а не в разметке: решать, что произойдёт по нажатию расширенной строки,
         * обязан `ViewModel`, а не элемент управления (§5.15, П-7).
         */
        val proIncluded: Boolean = false,
    )
}
