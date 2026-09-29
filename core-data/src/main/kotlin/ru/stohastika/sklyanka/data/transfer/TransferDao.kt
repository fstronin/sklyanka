package ru.stohastika.sklyanka.data.transfer

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import ru.stohastika.sklyanka.data.room.entity.IntakeEntity
import ru.stohastika.sklyanka.data.room.entity.MedicationEntity
import ru.stohastika.sklyanka.data.room.entity.ProfileEntity

/**
 * Выборки, нужные только переносу данных (SKL-19).
 *
 * Отдельный DAO рядом с [ru.stohastika.sklyanka.data.room.dao.MedicationDao] и
 * [ru.stohastika.sklyanka.data.room.dao.IntakeDao] намеренно. У тех интерфейсов есть свойство,
 * которое стоит сохранить: запроса «отдай всё» в них не существует вовсе (ПР-10, ПРФ-4),
 * и каждая выборка списком параметризована профилем. Перенос — единственная операция
 * продукта, которой нужно всё содержимое сразу, и её запросы собраны здесь, чтобы это
 * исключение было видно списком, а не растворилось среди обычных выборок.
 *
 * Выборки записей всё равно идут по профилю: состояние читается профиль за профилем.
 * Без профиля здесь только счётчики — числа «до и после» для журнала (`TDD.md §10.1`),
 * которые ни одной записи наружу не отдают.
 *
 * Схему базы этот DAO не меняет: новых сущностей и колонок у него нет, поэтому версия
 * схемы остаётся прежней (МГ-4).
 */
@Dao
interface TransferDao {

    @Query("SELECT * FROM profile ORDER BY is_self DESC, name")
    suspend fun selectAllProfiles(): List<ProfileEntity>

    @Query("SELECT * FROM medication WHERE profile_id = :profileId ORDER BY name")
    suspend fun selectMedicationsInProfile(profileId: String): List<MedicationEntity>

    @Query(
        """
        SELECT i.* FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE m.profile_id = :profileId
        ORDER BY i.scheduled_at
        """,
    )
    suspend fun selectIntakesInProfile(profileId: String): List<IntakeEntity>

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun countProfiles(): Int

    @Query("SELECT COUNT(*) FROM medication")
    suspend fun countMedications(): Int

    @Query("SELECT COUNT(*) FROM intake")
    suspend fun countIntakes(): Int

    /**
     * Правка существующих приёмов при слиянии истории (ПЕР-23).
     *
     * Именно `@Update`, а не вставка с заменой: замена в SQLite — это удаление строки
     * и вставка новой, и отметка приёма при этом теряет свою строку вместе с историей
     * обращений к ней. Обновление идёт по первичному ключу — идентификатору устройства.
     */
    @Update
    suspend fun updateIntakes(rows: List<IntakeEntity>): Int
}
