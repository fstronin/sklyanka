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
 * Метода удаления нет — это решение, а не упущение: до закрытия ОВ-7 профиль
 * не удаляется вовсе, потому что судьба его истории не решена.
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
}
