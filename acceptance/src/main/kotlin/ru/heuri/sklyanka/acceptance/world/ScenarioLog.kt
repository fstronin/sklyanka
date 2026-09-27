package ru.heuri.sklyanka.acceptance.world

import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OpLogRecord
import ru.heuri.sklyanka.domain.log.OperationLog
import java.time.Instant

/**
 * Журнал сценария — один на сценарий.
 *
 * Слияние двух прежних журналов: один хранил записи целиком (по ним проверяется, что
 * медицинских данных в журнале нет — НФТ-19), другой — только события (по ним проверяется,
 * что автоматический пропуск ничего человеку не показывает). Здесь хранятся записи,
 * а события отдаются производным списком, чтобы обе проверки работали без правок.
 */
class ScenarioLog : OperationLog {

    val records: MutableList<OpLogRecord> = mutableListOf()

    val events: List<OpEvent> get() = records.map { it.event }

    @Suppress("LongParameterList")
    override suspend fun record(
        event: OpEvent,
        profileId: String?,
        medicationId: String?,
        intakeId: String?,
        before: String?,
        after: String?,
        source: String?,
        detail: String?,
    ) {
        records += OpLogRecord(
            id = records.size.toLong(),
            at = Instant.EPOCH,
            event = event,
            profileId = profileId,
            medicationId = medicationId,
            intakeId = intakeId,
            before = before,
            after = after,
            source = source,
            detail = detail,
        )
    }

    override suspend fun recent(limit: Int): List<OpLogRecord> = records.takeLast(limit)

    override suspend fun prune(keepDays: Long, keepRecords: Int) = Unit
}
