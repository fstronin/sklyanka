package ru.stohastika.sklyanka.feature.settings.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.transfer.ApplyTransferUseCase
import ru.stohastika.sklyanka.domain.transfer.ExportDataUseCase
import ru.stohastika.sklyanka.domain.transfer.ExportOutcome
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import ru.stohastika.sklyanka.domain.transfer.FindMedicationMatches
import ru.stohastika.sklyanka.domain.transfer.ImportSource
import ru.stohastika.sklyanka.domain.transfer.MatchDecision
import ru.stohastika.sklyanka.domain.transfer.MatchProfiles
import ru.stohastika.sklyanka.domain.transfer.ReadTransferFileUseCase
import ru.stohastika.sklyanka.domain.transfer.TransferDecoded
import ru.stohastika.sklyanka.domain.transfer.TransferFiles
import ru.stohastika.sklyanka.domain.transfer.TransferIdSource
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.domain.transfer.TransferNotice
import ru.stohastika.sklyanka.domain.transfer.TransferOutcome
import ru.stohastika.sklyanka.domain.transfer.TransferRejectReason
import ru.stohastika.sklyanka.domain.transfer.TransferSettingsStore
import ru.stohastika.sklyanka.domain.transfer.TransferSnapshot
import ru.stohastika.sklyanka.domain.transfer.TransferStore
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Состояние экрана «Перенос данных» (SKL-19).
 *
 * Один источник состояния и одно направление данных. Порядок шагов удерживается здесь,
 * а не на экранах: между «человек выбрал файл» и «данные применены» обязан лежать показ
 * сводки и подтверждение, иначе «Заменить всё» становится необратимым действием в одно
 * нажатие (ПЕР-15, ПЕР-16).
 *
 * Разобранный снимок файла живёт в поле этого объекта и нигде больше. Это и есть ПЕР-17:
 * загрузка не уходит в фон, не переживает уход с экрана и при возвращении начинается
 * сначала. Ни одной записи в базу до [TransferIntent.Apply] не происходит.
 */
@HiltViewModel
@Suppress("LongParameterList")
class DataTransferViewModel @Inject constructor(
    private val exportData: ExportDataUseCase,
    private val readFile: ReadTransferFileUseCase,
    private val applyTransfer: ApplyTransferUseCase,
    private val store: TransferStore,
    private val settings: TransferSettingsStore,
    private val files: TransferFiles,
    private val ids: TransferIdSource,
    private val time: TimeSource,
) : ViewModel() {

    private val mutable = MutableStateFlow(TransferUiState())
    val state: StateFlow<TransferUiState> = mutable.asStateFlow()

    /** Снимок выбранного файла: только в памяти экрана (ПЕР-17). */
    private var pending: TransferSnapshot? = null
    private var pendingNotices: List<TransferNotice> = emptyList()

    init {
        refreshOverview()
    }

    fun onIntent(intent: TransferIntent) {
        when (intent) {
            TransferIntent.Refresh -> refreshOverview()
            is TransferIntent.SelectMode -> mutable.update { it.copy(mode = intent.mode, failure = null) }
            is TransferIntent.SaveTo -> save(ExportTarget(intent.target))
            is TransferIntent.FileChosen -> openFile(ImportSource(intent.source))
            TransferIntent.ContinueFromSummary -> continueFromSummary()
            is TransferIntent.Decide -> decide(intent)
            TransferIntent.DropAllMatches -> dropAllMatches()
            is TransferIntent.ConfirmationWordChanged ->
                mutable.update { it.copy(confirmationWord = intent.text) }
            TransferIntent.Apply -> apply()
            TransferIntent.AcknowledgeReport -> reset()
            TransferIntent.DismissFailure -> mutable.update { it.copy(failure = null) }
            TransferIntent.NoFilePicker ->
                mutable.update { it.copy(busy = false, failure = TransferFailure.NoFilePicker) }
            TransferIntent.Leave -> reset()
        }
    }

    /**
     * Состав текущих данных, имя файла и дата последней выгрузки.
     *
     * Здесь же снимается признак «загрузка началась и не завершилась»: если он остался
     * с прошлого запуска, значит процесс убили посреди загрузки, и человеку надо сказать,
     * что загрузка не состоялась (ПЕР-31).
     */
    private fun refreshOverview() {
        viewModelScope.launch {
            val interrupted = settings.importInProgress()
            if (interrupted) settings.setImportInProgress(false)
            val device = store.readAll()
            val counts = device.counts
            mutable.update {
                it.copy(
                    deviceCounts = counts,
                    deviceHistoryMonths = counts.historyFrom?.let { from ->
                        ChronoUnit.MONTHS.between(from, time.today()).toInt()
                    },
                    exportFileName = exportData.fileName(),
                    zone = time.zone(),
                    lastExportDate = settings.lastExportDate(),
                    safetyCopyName = files.safetyCopy()?.name,
                    importDidNotHappen = interrupted,
                )
            }
        }
    }

    private fun save(target: ExportTarget) {
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, failure = null) }
            val outcome = exportData(target)
            mutable.update { current ->
                when (outcome) {
                    is ExportOutcome.Saved -> current.copy(
                        busy = false,
                        lastExportDate = outcome.date,
                        exportFileName = outcome.fileName,
                    )

                    is ExportOutcome.NotEnoughSpace -> current.copy(
                        busy = false,
                        failure = TransferFailure.NoSpace,
                    )

                    ExportOutcome.Failed -> current.copy(busy = false, failure = TransferFailure.ExportFailed)
                }
            }
        }
    }

    /**
     * Чтение и проверка файла. Ничего ещё не применяется (ПЕР-15 п. 2).
     *
     * Совпадения ищутся уже здесь, до подтверждения: сводка и список совпадений — это то,
     * что человек обязан увидеть **прежде** чем согласиться.
     */
    private fun openFile(source: ImportSource) {
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, failure = null) }
            when (val decoded = readFile(source, mutable.value.mode)) {
                is TransferDecoded.Rejected -> {
                    pending = null
                    mutable.update {
                        it.copy(
                            busy = false,
                            step = TransferStep.OVERVIEW,
                            failure = failureOf(decoded.reject.reason, decoded),
                        )
                    }
                }

                is TransferDecoded.Loaded -> {
                    pending = decoded.snapshot
                    pendingNotices = decoded.notices
                    mutable.update {
                        it.copy(
                            busy = false,
                            step = TransferStep.SUMMARY,
                            fileCounts = decoded.snapshot.counts,
                            fileExportedAt = decoded.snapshot.exportedAt,
                            oldFormatVersion = decoded.notices
                                .filterIsInstance<TransferNotice.OldFormat>()
                                .firstOrNull()?.fileVersion,
                            matches = emptyList(),
                            decisions = emptyMap(),
                            medicationNames = decoded.snapshot.medications.associate { med -> med.id to med.name },
                        )
                    }
                }
            }
        }
    }

    private fun continueFromSummary() {
        val snapshot = pending ?: return
        if (mutable.value.mode == TransferMode.REPLACE_ALL) {
            mutable.update { it.copy(step = TransferStep.REPLACE_CONFIRM) }
            return
        }
        viewModelScope.launch {
            val device = store.readAll()
            val profiles = MatchProfiles(ids)(device.profiles, snapshot.profiles)
            val matching = FindMedicationMatches()(device.medications, snapshot.medications, profiles)
            mutable.update { it.copy(step = TransferStep.MATCHES, matches = matching.matches) }
        }
    }

    private fun decide(intent: TransferIntent.Decide) {
        mutable.update { current ->
            current.copy(decisions = current.decisions + (intent.fileMedicationId to intent.decision))
        }
    }

    /** Выход из длинного списка: массового «применить ко всем» нет, а отказ от всех — есть (ПЕР-22). */
    private fun dropAllMatches() {
        mutable.update { current ->
            current.copy(
                decisions = current.matches.associate { it.file.id to MatchDecision.DO_NOT_LOAD },
            )
        }
    }

    private fun apply() {
        val snapshot = pending ?: return
        // Занятость ставится до операции и синхронно: повтор намерения, пришедший раньше,
        // чем тело корутины дойдёт до первого приостановления, обязан увидеть «идёт запись»
        // и отступить. Второй пояс к гейту экрана: занятая кнопка (`ButtonState.Busy`)
        // уже не нажимается, но замена необратима, и одной перерисовки для защиты мало (ПЕР-16).
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            val outcome = if (mutable.value.mode == TransferMode.REPLACE_ALL) {
                applyTransfer.replaceAll(snapshot, pendingNotices)
            } else {
                applyTransfer.merge(snapshot, pendingNotices, mutable.value.decisions)
            }
            when (outcome) {
                is TransferOutcome.Applied -> {
                    pending = null
                    mutable.update {
                        it.copy(
                            busy = false,
                            step = TransferStep.REPORT,
                            report = outcome.report,
                            safetyCopyName = outcome.safetyCopy?.name ?: it.safetyCopyName,
                        )
                    }
                }

                // Отказ применения возвращает на исходный шаг: сообщение о нём живёт
                // на экране переноса, и без возврата человек не увидел бы ни слова
                // (ПЕР-27, ПЕР-31: «приложение сообщает, что загрузка не состоялась»).
                is TransferOutcome.NotEnoughSpace ->
                    mutable.update {
                        it.copy(busy = false, step = TransferStep.OVERVIEW, failure = TransferFailure.NoSpace)
                    }

                TransferOutcome.NotApplied ->
                    mutable.update {
                        it.copy(busy = false, step = TransferStep.OVERVIEW, failure = TransferFailure.ImportNotApplied)
                    }
            }
        }
    }

    /** Возврат в исходное состояние: перенос всегда начинается сначала (ПЕР-17). */
    private fun reset() {
        pending = null
        pendingNotices = emptyList()
        mutable.update {
            TransferUiState(
                deviceCounts = it.deviceCounts,
                exportFileName = it.exportFileName,
                lastExportDate = it.lastExportDate,
                safetyCopyName = it.safetyCopyName,
            )
        }
        refreshOverview()
    }

    /**
     * Файл из будущего отличается от битого файла (ПЕР-29 против ПЕР-28).
     *
     * Ради этого различия версия формата читается до разбора остального файла: иначе
     * повышение версии формата выглядело бы для человека порчей его резервной копии (ОВ-65).
     */
    private fun failureOf(reason: TransferRejectReason, decoded: TransferDecoded.Rejected): TransferFailure =
        if (reason == TransferRejectReason.FUTURE_FORMAT) {
            TransferFailure.FromFuture
        } else {
            TransferFailure.NotAnExport(decoded.reject)
        }
}
