package ru.stohastika.sklyanka.domain.transfer

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.LocalDate

/** Исход выгрузки. Три исхода вместо булева «получилось»: человеку надо сказать, что именно случилось. */
sealed interface ExportOutcome {

    /** @property bytes размер записанного файла: он же уходит в журнал (`TDD.md §10.1`). */
    data class Saved(val fileName: String, val bytes: Long, val date: LocalDate) : ExportOutcome

    /** Места не хватило, и это выяснилось **до** начала записи (ПЕР-31). */
    data class NotEnoughSpace(val requiredBytes: Long, val freeBytes: Long) : ExportOutcome

    /** Запись сорвалась: цель снята, дата последней выгрузки не двинулась (ПЕР-32). */
    data object Failed : ExportOutcome
}

/**
 * Выгрузка всего состояния в один файл (ПЕР-2, ПЕР-3).
 *
 * Порядок шагов здесь и есть содержание требования ПЕР-32: содержимое собирается
 * **полностью** и только потом открывается цель. Сорванная запись снимает цель за собой,
 * поэтому готового файла в выбранном месте не остаётся, а дата последней выгрузки
 * не двигается — иначе человек будет уверен, что копия есть.
 *
 * Куда именно ушёл файл, приложение не запоминает и не журналирует (ПЕР-36, НФТ-19):
 * место выбирает система по просьбе человека.
 */
class ExportDataUseCase(
    private val store: TransferStore,
    private val settings: TransferSettingsStore,
    private val codec: TransferCodec,
    private val files: TransferFiles,
    private val time: TimeSource,
    private val log: OperationLog,
    private val app: AppStamp,
) {

    /** Снимок текущего состояния. Нужен и выгрузке, и экрану: чипы состава считаются по нему (критерий 3). */
    suspend fun snapshot(): TransferSnapshot {
        val state = store.readAll()
        return TransferSnapshot.of(
            exportedAt = time.now(),
            app = app,
            profiles = state.profiles,
            medications = state.medications,
            intakes = state.intakes,
            settings = settings.read(),
        )
    }

    /** Имя файла по локальной дате устройства (ПЕР-2). */
    fun fileName(): String = TransferFormat.fileName(time.today())

    suspend operator fun invoke(target: ExportTarget): ExportOutcome {
        val snapshot = snapshot()
        val content = codec.encode(snapshot)
        val bytes = content.toByteArray(Charsets.UTF_8).size.toLong()

        val free = files.freeSpaceBytes()
        if (free < bytes * SPACE_MARGIN) {
            log.record(event = OpEvent.TRANSFER_EXPORT_FAILED, detail = NO_SPACE)
            return ExportOutcome.NotEnoughSpace(requiredBytes = bytes * SPACE_MARGIN, freeBytes = free)
        }

        return runCatching { files.write(target, content) }.fold(
            onSuccess = {
                val date = time.today()
                settings.setLastExportDate(date)
                log.record(
                    event = OpEvent.TRANSFER_EXPORTED,
                    after = snapshot.counts.logDetail(),
                    detail = "bytes=$bytes",
                )
                ExportOutcome.Saved(fileName = TransferFormat.fileName(date), bytes = bytes, date = date)
            },
            onFailure = {
                // Снять цель обязательно: наполовину записанный файл не должен выглядеть
                // готовой резервной копией (ПЕР-32).
                files.discard(target)
                log.record(event = OpEvent.TRANSFER_EXPORT_FAILED, detail = WRITE_FAILED)
                ExportOutcome.Failed
            },
        )
    }

    private companion object {
        /**
         * Запас по месту: файл пишется поверх, а не рядом, но черновой буфер и файловая
         * система берут своё. Двукратный запас дешевле, чем отказ на середине записи.
         */
        const val SPACE_MARGIN = 2L
        const val NO_SPACE = "no_space"
        const val WRITE_FAILED = "write_failed"
    }
}

/** Сводка числами для журнала: профили/лекарства/архив/приёмы — и ни одного названия (НФТ-19). */
fun TransferCounts.logDetail(): String = "$profiles/$medications/$archived/$intakes"
