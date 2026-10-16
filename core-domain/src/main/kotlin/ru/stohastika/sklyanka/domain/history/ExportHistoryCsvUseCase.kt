package ru.stohastika.sklyanka.domain.history

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import ru.stohastika.sklyanka.domain.transfer.TransferFiles

/**
 * Исход записи выгрузки истории.
 *
 * Отмена системного диалога сюда не попадает: до этой операции дело доходит только после
 * того, как человек выбрал место, — отмену видит поверхность (`ExportHistoryOutcome`).
 */
sealed interface HistoryCsvSaveOutcome {

    data class Saved(val records: Int, val bytes: Int) : HistoryCsvSaveOutcome

    /** Места не хватило, и это выяснилось **до** начала записи (тот же порядок, что ПЕР-31). */
    data object NotEnoughSpace : HistoryCsvSaveOutcome

    /** Запись сорвалась: цель снята, файла с целевым именем не остаётся. */
    data object Failed : HistoryCsvSaveOutcome
}

/**
 * Выгрузка истории файлом — «Сохранить» и «Передать» шторки экспорта (SKL-119, Р-35, Р-85 п. 2).
 *
 * Файловая часть — **тот же механизм, что у переноса данных и служебной выгрузки журнала**
 * ([TransferFiles]): содержимое собирается целиком до открытия цели, место проверяется
 * до записи, сорванная запись снимает за собой цель. Второй путь записи означал бы второй
 * набор ошибок в том же месте, а полуфайла с медицинскими данными быть не должно.
 *
 * Система передачи — **существующий** механизм отчёта врачу ([DoctorReportTransfer]):
 * обмен отдаёт файл через системный выбор получателя и `FileProvider`, а приложение
 * не запрашивает ни разрешений на хранилище, ни сети (НФТ-17, ADR-0010). «Передан» означает
 * «отдан системному обмену» и доставку не обещает: успех ставится по решению человека
 * в системном диалоге, а не в момент его открытия (П-7).
 *
 * Оба исхода оставляют след в журнале без медицинских данных: исход, число записей и размер
 * файла, но ни одного названия лекарства и ни одного имени профиля (НФТ-19).
 */
class ExportHistoryCsvUseCase(
    private val files: TransferFiles,
    private val transfer: DoctorReportTransfer,
    private val activeProfile: ActiveProfileRepository,
    private val log: OperationLog,
) {

    /** Запись в выбранное человеком место с проверкой свободного места до начала записи. */
    suspend operator fun invoke(document: HistoryCsvDocument, target: ExportTarget): HistoryCsvSaveOutcome {
        val bytes = document.sizeBytes
        if (files.freeSpaceBytes() < bytes.toLong() * SPACE_MARGIN) {
            record("no_space;records=${document.records};bytes=$bytes")
            return HistoryCsvSaveOutcome.NotEnoughSpace
        }
        return runCatching { files.write(target, document.content) }.fold(
            onSuccess = {
                record("saved;records=${document.records};bytes=$bytes")
                HistoryCsvSaveOutcome.Saved(records = document.records, bytes = bytes)
            },
            onFailure = {
                // Недописанная цель снимается: файл, который выглядит выгруженной историей,
                // но кончается посреди строки, хуже отсутствия файла.
                files.discard(target)
                record("failed;records=${document.records};bytes=$bytes")
                HistoryCsvSaveOutcome.Failed
            },
        )
    }

    /** Отдать файл системному обмену и дождаться выбора получателя. */
    suspend fun share(document: HistoryCsvDocument, launcher: ReportShareLauncher): ReportTransferOutcome =
        recordOutcome(transfer.share(document.asTransferable(), launcher), document)

    /**
     * Человек закрыл системный выбор места, не выбрав его.
     *
     * След остаётся и здесь, хотя приложение не сделало ничего: без записи «файл не сохранён»
     * неотличимо от «сохранение сорвалось», а человеку эти два случая выглядят одинаково.
     */
    suspend fun cancelled(document: HistoryCsvDocument): ReportTransferOutcome =
        recordOutcome(ReportTransferOutcome.Cancelled, document)

    /** Сохранить файл некому: в устройстве нет обработчика системного выбора места. */
    suspend fun saveUnavailable(document: HistoryCsvDocument): ReportTransferOutcome =
        recordOutcome(ReportTransferOutcome.Failed(NO_SAVE_HANDLER), document)

    /**
     * Передать файл некому: системного обмена в устройстве нет.
     *
     * Исход — отказ, а не отмена: системный диалог не открывался вовсе (SKL-46).
     */
    suspend fun shareUnavailable(document: HistoryCsvDocument): ReportTransferOutcome =
        recordOutcome(ReportTransferOutcome.Failed(NO_SHARE_HANDLER), document)

    private suspend fun recordOutcome(
        outcome: ReportTransferOutcome,
        document: HistoryCsvDocument,
    ): ReportTransferOutcome {
        val detail = when (outcome) {
            ReportTransferOutcome.Shared -> "shared"
            ReportTransferOutcome.Saved -> "saved"
            ReportTransferOutcome.Cancelled -> "cancelled"
            is ReportTransferOutcome.Failed -> "failed:${outcome.reason}"
        }
        record("$detail;records=${document.records};bytes=${document.sizeBytes}")
        return outcome
    }

    private suspend fun record(detail: String) {
        log.record(
            event = OpEvent.HISTORY_EXPORTED,
            profileId = activeProfile.current(),
            // Исход, число записей и размер вместо содержимого: файл целиком — медицинские данные.
            detail = detail,
        )
    }

    private companion object {

        /** Тот же двукратный запас по месту, что у выгрузки данных и журнала (ПЕР-31). */
        const val SPACE_MARGIN = 2L

        const val NO_SAVE_HANDLER = "no_activity_for_create_document"
        const val NO_SHARE_HANDLER = "no_activity_for_share"
    }
}
