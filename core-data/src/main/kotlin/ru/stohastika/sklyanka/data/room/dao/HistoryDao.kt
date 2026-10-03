package ru.stohastika.sklyanka.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.stohastika.sklyanka.data.room.entity.MedicationEntity

/**
 * Выборки экрана истории (SKL-18).
 *
 * Отдельный DAO, а не новые методы у [MedicationDao], по одной причине, и она важнее
 * аккуратности состава: здесь живёт **единственный** законный запрос лекарств без фильтра
 * архива (ПР-11). В [MedicationDao] такой метод стоял бы рядом с `observeActive` и
 * `observeArchived`, и при следующем касании его «починили бы для единообразия» — то есть
 * завершённый курс исчез бы из истории, ради сохранения которой архив и придуман.
 *
 * Схему базы этот DAO не меняет: новых сущностей и колонок у него нет.
 */
@Dao
interface HistoryDao {

    /**
     * Лекарства профиля **вместе с архивированными** — только для истории (ПР-11).
     *
     * Фильтра `archived_at` здесь нет намеренно. Во всех прочих выборках он обязателен:
     * архивированный курс не порождает приёмов, не планируется и не показывается в списке.
     * В истории он равноправен — завершённый курс остаётся в картине месяца.
     * Мягко удалённые исключены: их нет ни в истории, ни в отчёте (ПР-14).
     *
     * Запрос идёт по индексу `Medication(profile_id, archived_at)`, тому же, что обслуживает
     * выборки активных и архивированных.
     */
    @Query(
        """
        SELECT * FROM medication
        WHERE profile_id = :profileId AND deleted_at IS NULL
        ORDER BY name
        """,
    )
    fun observeMedicationsForHistory(profileId: String): Flow<List<MedicationEntity>>

    /**
     * Назначенное время самой ранней записи истории профиля.
     *
     * Нужно календарю: дни до начала ведения истории рисуются нейтрально и пропусками
     * не считаются (`docs/spec/03-screens.md`, состояние «Истории меньше месяца»). Без
     * этого значения такие дни неотличимы от дней, в которые приём был и не состоялся.
     *
     * `MIN` считает база: вытаскивать всю историю профиля в память ради одной даты значило
     * бы читать таблицу целиком при каждом открытии экрана (НФТ-24, НФТ-25).
     */
    @Query(
        """
        SELECT MIN(i.scheduled_at) FROM intake i
        JOIN medication m ON m.id = i.medication_id
        WHERE m.profile_id = :profileId AND m.deleted_at IS NULL
        """,
    )
    fun observeFirstScheduledAt(profileId: String): Flow<String?>
}
