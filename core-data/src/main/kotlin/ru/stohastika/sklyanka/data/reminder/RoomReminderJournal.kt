package ru.stohastika.sklyanka.data.reminder

import ru.stohastika.sklyanka.data.room.SklyankaConverters
import ru.stohastika.sklyanka.data.room.dao.ReminderLogDao
import ru.stohastika.sklyanka.data.room.entity.ReminderLogEntity
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Пределы хранения журнала срабатываний (НФТ-51). */
object ReminderLogRetention {
    /** Не меньше 30 суток: жалоба «напоминание не пришло на прошлой неделе» разбираема. */
    const val KEEP_DAYS: Long = 30L

    /**
     * Верхняя граница по числу записей.
     *
     * НФТ-51 задаёт предел в двух мегабайтах, а считать байты таблицы дорого и
     * неустойчиво. Число подобрано по составу записи: одна запись — порядка 200 байт,
     * значит 8 000 записей укладываются в полтора мегабайта с запасом, а срабатываний
     * при восьми приёмах в сутки столько набегает за два года.
     */
    const val KEEP_RECORDS: Int = 8_000
}

/**
 * Журнал срабатываний поверх той же базы, что и данные.
 *
 * Момент записи приходит из домена (он же считает отклонение), а подрезка берёт время
 * из подменяемого источника: иначе вытеснение по возрасту непроверяемо (НФТ-48).
 */
@Singleton
class RoomReminderJournal @Inject constructor(private val dao: ReminderLogDao, private val time: TimeSource) :
    ReminderJournal {

    override suspend fun record(entry: ReminderLogEntry) {
        dao.insert(
            ReminderLogEntity(
                occurredAt = entry.occurredAt.toEpochMilli(),
                event = entry.event.name,
                intakeId = entry.intakeId,
                medicationId = entry.medicationId,
                profileId = entry.profileId,
                scheduledAt = SklyankaConverters.instantToMillis(entry.scheduledAt),
                firedAt = SklyankaConverters.instantToMillis(entry.firedAt),
                deviationSeconds = entry.deviationSeconds,
                channel = entry.channel?.id,
                delivered = entry.delivered,
                reason = entry.reason,
                alarmsCancelled = entry.alarmsCancelled,
                alarmsScheduled = entry.alarmsScheduled,
                attempt = entry.attempt,
                snoozeMinutes = entry.minutes,
                watchResult = entry.watchResult,
            ),
        )
    }

    override suspend fun recent(limit: Int): List<ReminderLogEntry> = dao.recent(limit).mapNotNull(::toDomain)

    override suspend fun last(event: ReminderEvent): ReminderLogEntry? = dao.last(event.name)?.let(::toDomain)

    /** Выборка по времени запросом: реализация по умолчанию ограничена числом записей. */
    override suspend fun since(from: Instant): List<ReminderLogEntry> =
        dao.since(from.toEpochMilli()).mapNotNull(::toDomain)

    override suspend fun firedIntakeIds(since: Instant): Set<String> =
        dao.firedIntakeIds(ReminderEvent.FIRED.name, since.toEpochMilli()).toSet()

    override suspend fun prune(keepDays: Long, keepRecords: Int) {
        dao.pruneOlderThan(time.now().minus(Duration.ofDays(keepDays)).toEpochMilli())
        dao.pruneToLast(keepRecords)
    }

    /**
     * Незнакомый вид записи не роняет чтение, а выпадает из выборки (МГ-6): журнал,
     * записанный более новой сборкой, не имеет права сломать экран диагностики.
     */
    private fun toDomain(entity: ReminderLogEntity): ReminderLogEntry? {
        val event = SklyankaConverters.textToEnum<ReminderEvent>(entity.event) ?: return null
        return ReminderLogEntry(
            occurredAt = requireNotNull(SklyankaConverters.millisToInstant(entity.occurredAt)),
            event = event,
            intakeId = entity.intakeId,
            medicationId = entity.medicationId,
            profileId = entity.profileId,
            scheduledAt = SklyankaConverters.millisToInstant(entity.scheduledAt),
            firedAt = SklyankaConverters.millisToInstant(entity.firedAt),
            deviationSeconds = entity.deviationSeconds,
            channel = ReminderChannel.entries.firstOrNull { it.id == entity.channel },
            delivered = entity.delivered,
            reason = entity.reason,
            alarmsCancelled = entity.alarmsCancelled,
            alarmsScheduled = entity.alarmsScheduled,
            attempt = entity.attempt,
            minutes = entity.snoozeMinutes,
            watchResult = entity.watchResult,
        )
    }
}
