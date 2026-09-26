package ru.heuri.sklyanka.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.heuri.sklyanka.data.room.entity.OpLogEntity

/** Журнал критичных операций. Наружу не уходит, выгружается только руками человека. */
@Dao
interface OpLogDao {

    @Insert
    suspend fun insert(record: OpLogEntity)

    @Query("SELECT * FROM op_log ORDER BY at DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<OpLogEntity>

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
