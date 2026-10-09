package ru.stohastika.sklyanka.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.stohastika.sklyanka.data.room.entity.OpLogEntity

/** Журнал критичных операций. Наружу не уходит, выгружается только руками человека. */
@Dao
interface OpLogDao {

    @Insert
    suspend fun insert(record: OpLogEntity)

    @Query("SELECT * FROM op_log ORDER BY at DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<OpLogEntity>

    /**
     * Момент последней записи этого события.
     *
     * Спрашивается прямо у базы, а не разбором [recent]: между двумя проверками в журнал
     * могло лечь сколько угодно других записей, и окно «не чаще раза в сутки» не имеет
     * права зависеть от того, сколько их легло.
     */
    @Query("SELECT MAX(at) FROM op_log WHERE event = :event")
    suspend fun lastAt(event: String): Long?

    @Query("DELETE FROM op_log WHERE at < :before")
    suspend fun pruneOlderThan(before: Long): Int

    /**
     * Вытеснение по числу записей.
     *
     * Нужно вдобавок к вытеснению по возрасту: всплеск событий (загрузка файла переноса
     * на тысячах записей) иначе съест место раньше, чем наступит срок хранения.
     */
    @Query(
        """
        DELETE FROM op_log WHERE id NOT IN
        (SELECT id FROM op_log ORDER BY id DESC LIMIT :keep)
        """,
    )
    suspend fun pruneToLast(keep: Int): Int
}
