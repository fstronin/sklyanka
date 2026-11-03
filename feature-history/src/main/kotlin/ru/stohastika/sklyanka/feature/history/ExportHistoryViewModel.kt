package ru.stohastika.sklyanka.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.history.BuildHistoryCsvTableUseCase
import ru.stohastika.sklyanka.domain.history.ExportHistoryCsvUseCase
import ru.stohastika.sklyanka.domain.history.HistoryCsvSaveOutcome
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.ReportPeriodRules
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.domain.history.ReportShareLauncher
import ru.stohastika.sklyanka.domain.history.ReportTransferOutcome
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import java.time.LocalDate
import javax.inject.Inject

/**
 * Состояние шторки экспорта истории (SKL-119, Р-35, Р-85 п. 2).
 *
 * Шторка открывается **строкой настроек**, а не экраном: `app` собирает её поверх корня
 * и передаёт сюда намерения. Отсюда два правила, которых нет у шторки отчёта.
 *
 * Первое: **доступ проверяется в момент нажатия**. Снимок подписки не держится ни в одном
 * поле — гейт решает по подписке, а не по нажатию (Р-7), и второй заход после оформления
 * читает её заново без «перечитывания по возврату». Без активного доступа файл не формируется
 * вовсе: нажатие открывает витрину (Р-22, П-7). В поставляемой бесплатной сборке доступ есть
 * всегда (Р-86, ADR-0016), поэтому гейт остаётся спящим кодом для будущего `pro`-варианта.
 *
 * Второе: **период и состав — настройки одной выгрузки**, а не экрана. Границы «Другого периода»
 * те же, что у отчёта, и считаются тем же правилом ([ReportPeriodRules]): второго набора
 * пресетов в продукте нет.
 */
@HiltViewModel
class ExportHistoryViewModel @Inject constructor(
    private val buildTable: BuildHistoryCsvTableUseCase,
    private val composer: HistoryCsvComposer,
    private val export: ExportHistoryCsvUseCase,
    private val proStatus: ObserveProStatusUseCase,
    /**
     * Запись выгрузки истории (SKL-165, ПМ-26).
     *
     * Тем же швом домена, что у отчёта врачу: экран не знает поставщика, а без согласия
     * запись ничего не делает (АН-2).
     */
    private val analytics: AnalyticsRecorder,
    private val time: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportHistoryUiState())
    val state: StateFlow<ExportHistoryUiState> = _state.asStateFlow()

    /**
     * Показ системного выбора получателя.
     *
     * Приходит из разметки: результат системного диалога умеет получить только она.
     * До подстановки передавать нечем — попытка честно кончается отказом.
     */
    private var shareLauncher: ReportShareLauncher? = null

    private var selection: Selection? = null

    private val _showcase = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Сигнал «открыть витрину»: без активного доступа строка настроек ведёт туда (Р-22, §5.16).
     *
     * Событие, а не состояние: витрина — одна поверхность на все точки входа (Р-67), и показывает
     * её `app`; шторка только просит. Второго экземпляра поверхности покупки в модуле нет.
     */
    val showcase: SharedFlow<Unit> = _showcase.asSharedFlow()

    /** Связать передачу с системным выбором получателя, который живёт на экране. */
    fun attachShareLauncher(launcher: ReportShareLauncher) {
        shareLauncher = launcher
    }

    fun onIntent(intent: ExportHistoryIntent) {
        when (intent) {
            ExportHistoryIntent.Requested -> request()
            ExportHistoryIntent.Dismissed -> close()
            is ExportHistoryIntent.PeriodChosen -> choosePreset(intent.preset)
            is ExportHistoryIntent.StartPicked -> pickStart(intent.date)
            is ExportHistoryIntent.EndPicked -> pickEnd(intent.date)
            ExportHistoryIntent.ShareRequested -> share()
            is ExportHistoryIntent.SaveTo -> save(ExportTarget(intent.target))
            ExportHistoryIntent.SaveCancelled -> cancelled()
            ExportHistoryIntent.SaveUnavailable -> saveUnavailable()
            // Переход наружу и системный выбор места шторка не исполняет: первое решает
            // маршрут, второе — системный диалог, чей результат приходит обратно как SaveTo.
            ExportHistoryIntent.SaveRequested -> Unit
        }
    }

    /**
     * Нажатие строки «Экспорт истории»: с доступом — шторка, без доступа — витрина.
     *
     * Доступ читается здесь, а не хранится в состоянии: он мог измениться, пока человек ходил
     * в магазин, а `onResume` при закрытии шторки витрины не наступает (SKL-122). Без доступа
     * файла нет вовсе — витрина не «показывается рядом» с ним (П-7).
     */
    private fun request() {
        viewModelScope.launch {
            if (!proStatus().included) {
                _showcase.tryEmit(Unit)
                return@launch
            }
            val defaults = ReportPeriodRules.customDefaults(time.today())
            rebuild(Selection(preset = ReportPreset.THIS_MONTH, from = defaults.from, to = defaults.to))
        }
    }

    private fun close() {
        selection = null
        _state.value = ExportHistoryUiState()
    }

    /**
     * Смена пресета: «Другой период» возвращается к прежним датам в пределах открытой шторки,
     * а при первом выборе — к значениям по умолчанию (ОВ-125).
     */
    private fun choosePreset(preset: ReportPreset) {
        val current = selection ?: return
        val next = if (preset == ReportPreset.CUSTOM && current.preset != ReportPreset.CUSTOM) {
            val defaults = ReportPeriodRules.customDefaults(time.today())
            current.copy(preset = preset, from = defaults.from, to = defaults.to)
        } else {
            current.copy(preset = preset)
        }
        rebuild(next)
    }

    private fun pickStart(date: LocalDate) {
        val current = selection ?: return
        val window = ReportPeriodRules.withStart(date, HistoryWindow(current.from, current.to), time.today())
        rebuild(current.copy(preset = ReportPreset.CUSTOM, from = window.from, to = window.to))
    }

    private fun pickEnd(date: LocalDate) {
        val current = selection ?: return
        val window = ReportPeriodRules.withEnd(date, HistoryWindow(current.from, current.to), time.today())
        rebuild(current.copy(preset = ReportPreset.CUSTOM, from = window.from, to = window.to))
    }

    /**
     * Сборка файла за период — тем же расчётом, что экран истории и отчёт.
     *
     * Сообщение исхода сбрасывается: оно относилось к прошлому составу и оставить его рядом
     * с новым числом значит сказать человеку неправду. Прежний состав остаётся на месте,
     * пока не готов новый, и применяется результат только последнего выбора — как у отчёта.
     */
    private fun rebuild(next: Selection) {
        selection = next
        _state.value = _state.value.copy(outcome = null)
        viewModelScope.launch {
            val table = buildTable(windowOf(next)) ?: return@launch
            if (selection != next) return@launch
            val document = composer.compose(table)
            _state.value = ExportHistoryUiState(
                sheet = ExportUi(
                    document = document,
                    composition = composer.composition(table),
                    preset = next.preset,
                    customFrom = next.from,
                    customTo = next.to,
                    today = time.today(),
                ),
                outcome = null,
            )
        }
    }

    private fun windowOf(selection: Selection): HistoryWindow = if (selection.preset == ReportPreset.CUSTOM) {
        HistoryWindow(selection.from, selection.to)
    } else {
        ReportPeriodRules.window(selection.preset, time.today())
    }

    /**
     * Передать файл системным обменом.
     *
     * Исход ставится только после решения системного выбора: пока диалог открыт, результат
     * неизвестен (П-7). Без обработчика на экране передавать нечем — честный отказ.
     */
    private fun share() {
        val sheet = _state.value.sheet ?: return
        val launcher = shareLauncher
        viewModelScope.launch {
            val outcome = if (launcher == null) {
                export.shareUnavailable(sheet.document)
            } else {
                export.share(sheet.document, launcher)
            }
            apply(transferOutcome(outcome, onFailure = ExportOutcome.FAILED_SHARE))
        }
    }

    private fun save(target: ExportTarget) {
        val sheet = _state.value.sheet ?: return
        viewModelScope.launch {
            apply(
                when (export(sheet.document, target)) {
                    is HistoryCsvSaveOutcome.Saved -> ExportOutcome.SAVED
                    HistoryCsvSaveOutcome.NotEnoughSpace -> ExportOutcome.NO_SPACE
                    HistoryCsvSaveOutcome.Failed -> ExportOutcome.FAILED_SAVE
                },
            )
        }
    }

    private fun cancelled() {
        val sheet = _state.value.sheet ?: return
        viewModelScope.launch { apply(transferOutcome(export.cancelled(sheet.document), ExportOutcome.FAILED_SAVE)) }
    }

    /**
     * Сохранить некому: системного выбора места в устройстве нет.
     *
     * Исход тот же, что у сорванной записи, — отказ, — но причина другая: диалог не открывался.
     * Шторка остаётся открытой: человек хотел сохранить и вправе попробовать снова (SKL-46).
     */
    private fun saveUnavailable() {
        val sheet = _state.value.sheet ?: return
        viewModelScope.launch {
            apply(
                transferOutcome(
                    export.saveUnavailable(sheet.document),
                    ExportOutcome.FAILED_SAVE,
                ),
            )
        }
    }

    private fun transferOutcome(outcome: ReportTransferOutcome, onFailure: ExportOutcome): ExportOutcome =
        when (outcome) {
            ReportTransferOutcome.Shared -> ExportOutcome.SHARED
            ReportTransferOutcome.Saved -> ExportOutcome.SAVED
            ReportTransferOutcome.Cancelled -> ExportOutcome.CANCELLED
            is ReportTransferOutcome.Failed -> onFailure
        }

    private fun apply(outcome: ExportOutcome) {
        _state.value = _state.value.copy(outcome = outcome)
        // Одна точка на обмен, запись, отмену и отказ: второго события на нажатие нет
        // (SKL-165, TDD §4).
        analytics.recordReportExported(outcome)
    }

    /** Выбранный период шторки: пресет и границы «Другого периода». */
    private data class Selection(val preset: ReportPreset, val from: LocalDate, val to: LocalDate)
}
