package ru.heuri.sklyanka.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.heuri.sklyanka.data.room.entity.ReminderLogEntity

/**
 * Журнал срабатываний напоминаний.
 *
 * Наружу не уходит: сети у приложения нет (НАП-39). Читается экраном диагностики
 * и обнаружением перерыва.
 */
@Dao
interface ReminderLogDao {

    @Insert
    suspend fun insert(entry: ReminderLogEntity)

    @Query("SELECT * FROM reminder_log ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ReminderLogEntity>

    @Query("SELECT * FROM reminder_log WHERE event = :event ORDER BY occurred_at DESC, id DESC LIMIT 1")
    suspend fun last(event: String): ReminderLogEntity?

    @Query("SELECT * FROM reminder_log WHERE intake_id = :intakeId ORDER BY occurred_at DESC, id DESC")
    suspend fun ofIntake(intakeId: String): List<ReminderLogEntity>

    /**
     * Приёмы, о которых напоминание действительно сработало.
     *
     * Опора обнаружения перерыва (НАП-38): будильник стоял, момент прошёл, а записи нет —
     * значит приложение было остановлено, а не человек проигнорировал напоминание.
     */
    @Query(
        """
        SELECT DISTINCT intake_id FROM reminder_log
        WHERE event = :firedEvent AND occurred_at >= :since AND intake_id IS NOT NULL
        """,
    )
    suspend fun firedIntakeIds(firedEvent: String, since: Long): List<String>

    @Query("DELETE FROM reminder_log WHERE occurred_at < :before")
    suspend fun pruneOlderThan(before: Long): Int

    /**
     * Вытеснение по числу записей (НФТ-51): всплеск срабатываний иначе съест место
     * раньше, чем наступит срок хранения.
     */
    @Query(
        """
        DELETE FROM reminder_log WHERE id NOT IN
        (SELECT id FROM reminder_log ORDER BY id DESC LIMIT :keep)
        """,
    )
    suspend fun pruneToLast(keep: Int): Int

    @Query("SELECT COUNT(*) FROM reminder_log")
    suspend fun count(): Int
}
