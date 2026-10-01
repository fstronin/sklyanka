package ru.stohastika.sklyanka.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.stohastika.sklyanka.data.room.entity.DeferredReminderEntity

/**
 * Хранилище отложенных срабатываний (SKL-35, ОВ-75).
 *
 * Наружу уходит доменным портом `DeferredReminders`: пересборке нужны все записи
 * одним запросом (таблица микроскопическая), отметке — ответ «была ли запись»,
 * по которому она решает, снимать ли будильник.
 */
@Dao
interface DeferredReminderDao {

    @Query("SELECT * FROM deferred_reminder")
    suspend fun all(): List<DeferredReminderEntity>

    /** Запись приёма либо `null`: отложенное срабатывание у приёма ровно одно. */
    @Query("SELECT * FROM deferred_reminder WHERE intake_id = :intakeId")
    suspend fun find(intakeId: String): DeferredReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(reminder: DeferredReminderEntity)

    /** @return число удалённых строк: 0 — записи не было, 1 — была. */
    @Query("DELETE FROM deferred_reminder WHERE intake_id = :intakeId")
    suspend fun delete(intakeId: String): Int
}
