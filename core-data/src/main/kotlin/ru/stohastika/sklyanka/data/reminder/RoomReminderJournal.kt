package ru.stohastika.sklyanka.data.reminder

import ru.stohastika.sklyanka.data.room.SklyankaConverters
import ru.stohastika.sklyanka.data.room.dao.ReminderLogDao
import ru.stohastika.sklyanka.data.room.entity.ReminderLogEntity
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Журнал срабатываний поверх той же базы, что и данные.
 *
 * Времени здесь нет: пороги хранения и момент ротации считает домен
 * (`RotateReminderJournalUseCase`), а хранилище умеет только то, что умеет SQL, —
 * удалить старше момента, вытеснить старейших снаружи окна и оценить объём.
 */
@Singleton
class RoomReminderJournal @Inject constructor(private val dao: ReminderLogDao) : ReminderJournal {

    override suspend fun record(entry: ReminderLogEntry) {
        dao.insert(entry.toEntity())
    }

    /**
     * Восстановление записывается один раз на факт (SKL-47): двумя пересборками
     * (запуск и перезагрузка) состояние и будильники не удваиваются, и журнал
     * не имеет права удвоиться. Решение «есть ли уже такой факт» принимается
     * в одной транзакции DAO, а не здесь: см. `ReminderLogDao.insertRestoredOnce`.
     */
    override suspend fun recordRestored(entry: ReminderLogEntry) {
        dao.insertRestoredOnce(entry.toEntity())
    }

    /**
     * Среда измерения записывается один раз на изменение (SKL-85): решение принимается
     * в одной транзакции DAO, а не здесь — см. `ReminderLogDao.insertEnvironmentOnce`.
     */
    override suspend fun recordEnvironment(entry: ReminderLogEntry) {
        dao.insertEnvironmentOnce(entry.toEntity())
    }

    private fun ReminderLogEntry.toEntity(): ReminderLogEntity = ReminderLogEntity(
        occurredAt = occurredAt.toEpochMilli(),
        event = event.name,
        intakeId = intakeId,
        medicationId = medicationId,
        profileId = profileId,
        scheduledAt = SklyankaConverters.instantToMillis(scheduledAt),
        firedAt = SklyankaConverters.instantToMillis(firedAt),
        deviationSeconds = deviationSeconds,
        channel = channel?.id,
        delivered = delivered,
        reason = reason,
        alarmsCancelled = alarmsCancelled,
        alarmsScheduled = alarmsScheduled,
        attempt = attempt,
        snoozeMinutes = minutes,
    )

    override suspend fun recent(limit: Int): List<ReminderLogEntry> = dao.recent(limit).mapNotNull(::toDomain)

    override suspend fun last(event: ReminderEvent): ReminderLogEntry? = dao.last(event.name)?.let(::toDomain)

    /** Выборка по времени запросом: реализация по умолчанию ограничена числом записей. */
    override suspend fun since(from: Instant): List<ReminderLogEntry> =
        dao.since(from.toEpochMilli()).mapNotNull(::toDomain)

    override suspend fun firedIntakeIds(since: Instant): Set<String> =
        dao.firedIntakeIds(ReminderEvent.FIRED.name, since.toEpochMilli()).toSet()

    /** Оценка объёма — деталь хранения: домен сравнивает её с потолком (НФТ-51). */
    override suspend fun storedBytes(): Long = dao.storedBytes()

    override suspend fun deleteOlderThan(before: Instant): Int = dao.deleteOlderThan(before.toEpochMilli())

    override suspend fun deleteOldest(protectedSince: Instant, limit: Int): Int =
        dao.deleteOldest(protectedSince.toEpochMilli(), limit)

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
        )
    }
}
