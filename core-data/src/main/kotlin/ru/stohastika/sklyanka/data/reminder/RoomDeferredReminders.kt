package ru.stohastika.sklyanka.data.reminder

import ru.stohastika.sklyanka.data.room.SklyankaConverters
import ru.stohastika.sklyanka.data.room.dao.DeferredReminderDao
import ru.stohastika.sklyanka.data.room.entity.DeferredReminderEntity
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminder
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminders
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отложенные срабатывания поверх той же базы, что и данные (SKL-35, ОВ-75).
 *
 * Момент хранится числом epoch millis UTC: это точка на абсолютной шкале, и смена пояса
 * её не пересчитывает — в отличие от локального `scheduled_at` (НФТ-34). Преобразование
 * идёт через [SklyankaConverters], как у остальных моментов факта.
 *
 * Запись уходит вместе с приёмом каскадом (`ON DELETE CASCADE`): отдельного удаления
 * при замене данных при переносе или пересоздании будущих приёмов не требуется.
 */
@Singleton
class RoomDeferredReminders @Inject constructor(private val dao: DeferredReminderDao) : DeferredReminders {

    override suspend fun all(): List<DeferredReminder> = dao.all().map(::toDomain)

    override suspend fun put(reminder: DeferredReminder) {
        dao.put(
            DeferredReminderEntity(
                intakeId = reminder.intakeId,
                fireAt = reminder.fireAt.toEpochMilli(),
                attempt = reminder.attempt,
            ),
        )
    }

    override suspend fun remove(intakeId: String): Boolean = dao.delete(intakeId) > 0

    private fun toDomain(entity: DeferredReminderEntity) = DeferredReminder(
        intakeId = entity.intakeId,
        fireAt = requireNotNull(SklyankaConverters.millisToInstant(entity.fireAt)),
        attempt = entity.attempt,
    )
}
