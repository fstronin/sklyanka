package ru.stohastika.sklyanka.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.stohastika.sklyanka.data.room.entity.ProfileEntity

/**
 * Профили.
 *
 * Удаление условно: `deleteById` снимает только чужой профиль — условие `is_self = 0`
 * выражает ПРФ-28 средствами базы, а не проверкой на месте вызова. Полное стирание
 * данных профиля — один сценарий домена (SKL-36, ОВ-7); порядок «приёмы → лекарства →
 * профиль» задаёт он, а внешние ключи с RESTRICT его держат.
 */
@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile ORDER BY is_self DESC, name")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile WHERE id = :profileId")
    suspend fun getById(profileId: String): ProfileEntity?

    @Query("SELECT * FROM profile WHERE self_slot = 'self'")
    suspend fun getSelf(): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun count(): Int

    /** Только для режима «заменить всё» при переносе данных (ПЕР-16). Порядок удаления задаёт транзактор. */
    @Query("DELETE FROM profile")
    suspend fun deleteAll(): Int

    /**
     * Удаление чужого профиля (SKL-36).
     *
     * @return число изменённых строк: ноль означает, что профиль владельца неприкосновенен
     *   или строки уже нет — и то и другое не ошибка этого запроса.
     */
    @Query("DELETE FROM profile WHERE id = :profileId AND is_self = 0")
    suspend fun deleteById(profileId: String): Int
}
