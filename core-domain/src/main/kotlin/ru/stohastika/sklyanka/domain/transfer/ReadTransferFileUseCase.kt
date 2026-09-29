package ru.stohastika.sklyanka.domain.transfer

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog

/**
 * Чтение выбранного файла до всякого применения (ПЕР-15 п. 2, ПЕР-16 п. 1).
 *
 * Обе проверки — опознание кодеком и проверка снимка — выполняются здесь, и ни одна
 * запись в базу до их завершения не идёт. Именно поэтому битый файл и файл из будущего
 * гарантированно не оставляют следа: применять нечего (ПЕР-27, ПЕР-28, ПЕР-29).
 */
class ReadTransferFileUseCase(
    private val files: TransferFiles,
    private val codec: TransferCodec,
    private val validate: ValidateSnapshot,
    private val log: OperationLog,
) {

    suspend operator fun invoke(source: ImportSource, mode: TransferMode): TransferDecoded {
        val text = runCatching { files.read(source) }.getOrElse {
            return reject(TransferReject(TransferRejectReason.NOT_JSON, UNREADABLE))
        }

        return when (val decoded = codec.decode(text)) {
            is TransferDecoded.Rejected -> reject(decoded.reject)

            is TransferDecoded.Loaded -> {
                val violation = validate(decoded.snapshot)
                if (violation != null) {
                    reject(violation)
                } else {
                    log.record(
                        event = OpEvent.TRANSFER_FILE_ACCEPTED,
                        after = decoded.snapshot.counts.logDetail(),
                        detail = "${mode.code}:v${decoded.snapshot.formatVersion}",
                    )
                    decoded
                }
            }
        }
    }

    /** Отказ уходит в журнал кодом причины и местом, без содержимого файла (НФТ-19). */
    private suspend fun reject(reject: TransferReject): TransferDecoded.Rejected {
        log.record(event = OpEvent.TRANSFER_FILE_REJECTED, detail = reject.logDetail)
        return TransferDecoded.Rejected(reject)
    }

    private companion object {
        const val UNREADABLE = "unreadable"
    }
}
