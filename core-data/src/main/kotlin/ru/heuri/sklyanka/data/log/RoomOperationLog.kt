package ru.heuri.sklyanka.data.log

import ru.heuri.sklyanka.data.room.SklyankaConverters
import ru.heuri.sklyanka.data.room.dao.OpLogDao
import ru.heuri.sklyanka.data.room.entity.OpLogEntity
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OpLogRecord
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Журнал операций поверх той же базы, что и данные.
 *
 * Момент записи берётся из подменяемого источника времени, а не из системных часов:
 * иначе журнал непроверяем ровно там, где он нужен, — в тестах на гонки и на границу
 * суток (НФТ-48).
 */
@Singleton
class RoomOperationLog @Inject constructor(private val dao: OpLogDao, private val time: TimeSource) : OperationLog {

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
        dao.insert(
            OpLogEntity(
                at = time.now().toEpochMilli(),
                event = event.name,
                profileId = profileId,
                medicationId = medicationId,
                intakeId = intakeId,
                valueBefore = before,
                valueAfter = after,
                source = source,
                detail = detail,
            ),
        )
    }

    override suspend fun recent(limit: Int): List<OpLogRecord> = dao.recent(limit).mapNotNull { entity ->
        val event = SklyankaConverters.textToEnum<OpEvent>(entity.event) ?: return@mapNotNull null
        OpLogRecord(
            id = entity.id,
            at = requireNotNull(SklyankaConverters.millisToInstant(entity.at)),
            event = event,
            profileId = entity.profileId,
            medicationId = entity.medicationId,
            intakeId = entity.intakeId,
            before = entity.valueBefore,
            after = entity.valueAfter,
            source = entity.source,
            detail = entity.detail,
        )
    }

    override suspend fun prune(keepDays: Long, keepRecords: Int) {
        dao.pruneOlderThan(time.now().minus(Duration.ofDays(keepDays)).toEpochMilli())
        dao.pruneToLast(keepRecords)
    }
}
