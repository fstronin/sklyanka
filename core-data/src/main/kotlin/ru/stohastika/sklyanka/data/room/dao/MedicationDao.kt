package ru.stohastika.sklyanka.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.stohastika.sklyanka.data.room.entity.MedicationEntity

/**
 * Лекарства.
 *
 * Каждая выборка списком параметризована профилем (ПР-10, ПРФ-4); запроса «отдай всё»
 * не существует — не «не используется», а отсутствует в интерфейсе. Адресация по
 * первичному ключу профиля не знает: принадлежность проверяет слой выше, где известен
 * активный профиль.
 */
@Dao
interface MedicationDao {

    @Query(
        """
        SELECT * FROM medication
        WHERE profile_id = :profileId AND archived_at IS NULL
        ORDER BY name
        """,
    )
    fun observeActive(profileId: String): Flow<List<MedicationEntity>>

    @Query(
        """
        SELECT * FROM medication
        WHERE profile_id = :profileId AND archived_at IS NOT NULL
        ORDER BY archived_at DESC
        """,
    )
    fun observeArchived(profileId: String): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medication WHERE id = :medicationId")
    suspend fun getById(medicationId: String): MedicationEntity?

    /**
     * Вставка или обновление.
     *
     * Именно `@Upsert`, а не `@Insert(onConflict = REPLACE)`: REPLACE в SQLite — это
     * удаление строки и вставка новой, а у лекарства есть дети-приёмы с `ON DELETE RESTRICT`.
     * Обновление курса с непустой историей превратилось бы в отказ, а в худшем случае —
     * в удаление истории. `@Upsert` обновляет строку на месте.
     */
    @Upsert
    suspend fun upsert(medication: MedicationEntity)

    /**
     * Архивация условна по `archived_at IS NULL` и возвращает число изменённых строк:
     * повторная архивация — не ошибка, но и не событие, и в журнал уходит только первая.
     * Момент и причина проставляются вместе (INV-9).
     */
    @Query(
        """
        UPDATE medication SET archived_at = :at, archive_reason = :reason
        WHERE id = :medicationId AND archived_at IS NULL
        """,
    )
    suspend fun archive(medicationId: String, at: Long, reason: String): Int

    /**
     * Принимает уже посчитанное значение остатка.
     *
     * Арифметики над дозами в SQL нет вовсе: остаток хранится текстом, а текст
     * складывать нельзя — «10» лексикографически меньше «9».
     */
    @Query("UPDATE medication SET stock_left = :stock WHERE id = :medicationId")
    suspend fun setStock(medicationId: String, stock: String?): Int

    @Query("SELECT COUNT(*) FROM medication WHERE profile_id = :profileId")
    suspend fun countInProfile(profileId: String): Int

    /**
     * Удаление всех лекарств профиля — активных и архивных (SKL-36, ОВ-7).
     *
     * Идёт после удаления приёмов: внешний ключ приёма стоит на RESTRICT, и обратный
     * порядок упёрся бы в ограничение целостности — ровно то, чего от него ждут.
     */
    @Query("DELETE FROM medication WHERE profile_id = :profileId")
    suspend fun deleteInProfile(profileId: String): Int

    /** Только для режима «заменить всё» при переносе данных (ПЕР-16). */
    @Query("DELETE FROM medication")
    suspend fun deleteAll(): Int
}
