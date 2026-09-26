package ru.heuri.sklyanka.domain.transfer

import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.repository.ActiveProfileRepository

/** Исход применения загрузки. */
sealed interface TransferOutcome {

    /** @property safetyCopy где лежит страховочная копия; только у режима замены (ПЕР-16 п. 3). */
    data class Applied(val report: TransferReport, val safetyCopy: SafetyCopy? = null) : TransferOutcome

    /** Места не хватило, и это выяснилось до стирания и до записи (ПЕР-31). */
    data class NotEnoughSpace(val requiredBytes: Long, val freeBytes: Long) : TransferOutcome

    /**
     * Загрузка не состоялась: транзакция откатилась, состояние прежнее (ПЕР-27).
     *
     * Отдельный исход рядом с исключением: отказ записи не имеет права быть тихим (НФТ-11),
     * а экрану нужно сказать человеку ровно это и ничего больше.
     */
    data object NotApplied : TransferOutcome
}

/**
 * Применение загрузки (ПЕР-15 пп. 6–7, ПЕР-16 пп. 3–6).
 *
 * Последовательность здесь — не стиль, а требование. Сначала полностью готовый план,
 * затем страховочная копия (только для замены), затем **одна** транзакция, и лишь после
 * неё — активный профиль, перепланирование и отчёт. Ни одно из последействий не имеет
 * права выполниться раньше записи, и ни одна запись — раньше страховочной копии.
 *
 * Признак «загрузка в работе» ставится до транзакции и снимается после: транзакцию
 * откатит хранилище само, а сказать человеку при следующем запуске, что загрузка
 * не состоялась, больше нечем (ПЕР-31).
 */
@Suppress("LongParameterList")
class ApplyTransferUseCase(
    private val store: TransferStore,
    private val settings: TransferSettingsStore,
    private val codec: TransferCodec,
    private val files: TransferFiles,
    private val activeProfile: ActiveProfileRepository,
    private val replanner: TransferReplanner,
    private val log: OperationLog,
    private val buildPlan: BuildTransferPlan,
) {

    /** Режим «Добавить к текущим»: решения по совпадениям уже приняты человеком (ПЕР-22). */
    suspend fun merge(
        snapshot: TransferSnapshot,
        notices: List<TransferNotice>,
        decisions: Map<String, MatchDecision>,
    ): TransferOutcome {
        val plan = buildPlan.merge(store.readAll(), snapshot, decisions, notices)
        log.record(
            event = OpEvent.TRANSFER_MATCHES_RESOLVED,
            after = plan.report.matchesResolved.toString(),
            detail = plan.report.decisions.entries
                .sortedBy { it.key.ordinal }
                .joinToString(",") { "${it.key.code}=${it.value}" },
        )
        return apply(plan, snapshot, safetyCopy = null)
    }

    /**
     * Режим «Заменить всё» (ПЕР-16).
     *
     * Страховочная копия делается **до** стирания и тем же кодеком, что обычная выгрузка.
     * Если места на копию не хватает, ничего не стирается и копия не создаётся наполовину.
     */
    @Suppress("ReturnCount")
    suspend fun replaceAll(snapshot: TransferSnapshot, notices: List<TransferNotice>): TransferOutcome {
        // Состояние читается один раз: решения принимаются по снимку, а не по базе,
        // в которую по ходу операции кто-то может дописать строку (ПЕР-27).
        val state = store.readAll()
        val current = codec.encode(
            TransferSnapshot.of(
                exportedAt = snapshot.exportedAt,
                app = snapshot.app,
                profiles = state.profiles,
                medications = state.medications,
                intakes = state.intakes,
                settings = settings.read(),
            ),
        )
        val needed = current.toByteArray(Charsets.UTF_8).size.toLong() * SPACE_MARGIN
        val free = files.freeSpaceBytes()
        if (free < needed) {
            log.record(event = OpEvent.TRANSFER_ROLLED_BACK, detail = NO_SPACE)
            return TransferOutcome.NotEnoughSpace(requiredBytes = needed, freeBytes = free)
        }

        val copy = runCatching { files.writeSafetyCopy(current) }.getOrElse {
            log.record(event = OpEvent.TRANSFER_ROLLED_BACK, detail = SAFETY_COPY_FAILED)
            return TransferOutcome.NotApplied
        }
        log.record(event = OpEvent.TRANSFER_SAFETY_COPY, detail = "bytes=${copy.sizeBytes}")

        return apply(buildPlan.replaceAll(snapshot, notices), snapshot, copy)
    }

    private suspend fun apply(
        plan: TransferPlan,
        snapshot: TransferSnapshot,
        safetyCopy: SafetyCopy?,
    ): TransferOutcome {
        settings.setImportInProgress(true)
        val applied = runCatching { store.apply(plan) }.getOrElse { failure ->
            log.record(event = OpEvent.TRANSFER_ROLLED_BACK, detail = plan.mode.code)
            settings.setImportInProgress(false)
            return if (failure is TransferSpaceException) {
                TransferOutcome.NotEnoughSpace(requiredBytes = failure.requiredBytes, freeBytes = failure.freeBytes)
            } else {
                TransferOutcome.NotApplied
            }
        }
        settings.setImportInProgress(false)

        // Последействие: строго после успешной транзакции и строго в этом порядке.
        settings.write(snapshot.settings)
        plan.activeProfileId?.let { activeProfile.switchTo(it) }
        log.record(
            event = OpEvent.TRANSFER_APPLIED,
            before = applied.before,
            after = applied.after,
            detail = if (safetyCopy == null) plan.mode.code else "${plan.mode.code}:${safetyCopy.name}",
        )
        replanner.replanAfterTransfer(plan.archivedMedicationIds)
        return TransferOutcome.Applied(report = plan.report, safetyCopy = safetyCopy)
    }

    private companion object {
        const val SPACE_MARGIN = 2L
        const val NO_SPACE = "no_space"
        const val SAFETY_COPY_FAILED = "safety_copy_failed"
    }
}

/**
 * Нехватка места, обнаруженная хранилищем уже в ходе записи (ПЕР-31).
 *
 * Отдельный тип, потому что этот отказ человеку объясняется иначе, чем любой другой:
 * «места недостаточно» — поправимо, «загрузка не состоялась» — нет.
 */
class TransferSpaceException(val requiredBytes: Long, val freeBytes: Long) :
    IllegalStateException("Not enough space for the transfer: required $requiredBytes, free $freeBytes")
