package ru.heuri.sklyanka.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.heuri.sklyanka.data.room.entity.IntakeEntity

/**
 * Приёмы.
 *
 * Сутки и периоды выбираются диапазоном по текстовому локальному времени —
 * без арифметики «минус 24 часа»: в сутках с переводом времени их 23 или 25 (НФТ-37).
 * Границы приходят из домена уже в формате хранения.
 */
@Dao
interface IntakeDao {

    @Query(
        """
        SELECT i.* FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE m.profile_id = :profileId
          AND i.scheduled_at >= :dayStart AND i.scheduled_at < :nextDayStart
        ORDER BY i.scheduled_at
        """,
    )
    fun observeDay(profileId: String, dayStart: String, nextDayStart: String): Flow<List<IntakeEntity>>

    @Query(
        """
        SELECT i.* FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE m.profile_id = :profileId
          AND i.scheduled_at >= :from AND i.scheduled_at < :to
        ORDER BY i.scheduled_at
        """,
    )
    fun observePeriod(profileId: String, from: String, to: String): Flow<List<IntakeEntity>>

    /**
     * История по лекарству за период.
     *
     * Фильтр по профилю здесь не избыточен, хотя лекарство однозначно задаёт профиль:
     * идентификатор лекарства приходит из навигации, то есть из внешнего мира, и после
     * переноса данных теоретически может указывать в чужой профиль.
     */
    @Query(
        """
        SELECT i.* FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE i.medication_id = :medicationId AND m.profile_id = :profileId
          AND i.scheduled_at >= :from AND i.scheduled_at < :to
        ORDER BY i.scheduled_at
        """,
    )
    fun observeByMedication(
        profileId: String,
        medicationId: String,
        from: String,
        to: String,
    ): Flow<List<IntakeEntity>>

    @Query("SELECT * FROM intake WHERE id = :intakeId")
    suspend fun getById(intakeId: String): IntakeEntity?

    /**
     * Вставка пачкой с пропуском конфликтов.
     *
     * Повторная генерация того же приёма — ожидаемый ход событий, а не ошибка:
     * уникальность держит индекс по слоту, а число реально вставленных строк
     * возвращается наружу и уходит в журнал.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(intakes: List<IntakeEntity>): List<Long>

    /**
     * Ядро идемпотентности (ПР-2, ПР-3, INV-19).
     *
     * Условие `status = 'PENDING'` стоит внутри самого UPDATE: сравнение и запись —
     * одна атомарная операция. Вторая отметка получает ноль изменённых строк и не делает
     * ничего — ни записи, ни списания остатка. Чтение «а отмечен ли уже?» перед записью
     * не годится: между чтением и записью помещается отметка с часов.
     */
    @Query(
        """
        UPDATE intake
        SET status = :status, acted_at = :actedAt, source = :source,
            actor_profile_id = :actorProfileId
        WHERE id = :intakeId AND status = 'PENDING'
        """,
    )
    suspend fun markIfPending(
        intakeId: String,
        status: String,
        actedAt: Long,
        source: String,
        actorProfileId: String?,
    ): Int

    /** Просроченные ожидающие приёмы для перевода в MISSED (ПР-1); граница окна — параметр домена (ОВ-4). */
    @Query("SELECT * FROM intake WHERE status = 'PENDING' AND scheduled_at < :before")
    suspend fun selectExpiredPending(before: String): List<IntakeEntity>

    @Query(
        """
        SELECT COUNT(*) FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE m.profile_id = :profileId
        """,
    )
    suspend fun countInProfile(profileId: String): Int

    /** Только для режима «заменить всё» при переносе данных (ПЕР-16). */
    @Query("DELETE FROM intake")
    suspend fun deleteAll(): Int
}
