package ru.stohastika.sklyanka.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.stohastika.sklyanka.data.room.entity.AnalyticsEventEntity

/**
 * Очередь аналитики (SKL-165, АН-4).
 *
 * Наружу уходит доменным портом `AnalyticsQueue`: отправка забирает пачку в порядке
 * появления событий, а не в порядке случайного чтения, иначе согласие, показанное
 * раньше, ушло бы после выключения аналитики.
 *
 * Вытеснение разделено на два запроса намеренно: срок хранения и потолок объёма —
 * два разных решения, и порт зовёт их оба. Склеивание в один запрос сделало бы
 * невозможным «сначала выбросить просроченное, потом решать про объём».
 */
@Dao
interface AnalyticsDao {

    @Insert
    suspend fun insert(event: AnalyticsEventEntity)

    @Insert
    suspend fun insertAll(events: List<AnalyticsEventEntity>)

    /** Пачка на отправку: самые старые первыми, `id` разводит события одной миллисекунды. */
    @Query("SELECT * FROM analytics_event ORDER BY occurred_at ASC, id ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<AnalyticsEventEntity>

    /** @return число удалённых строк: ровно столько записей уйдёт из очереди. */
    @Query("DELETE FROM analytics_event WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>): Int

    /** Вытеснение по сроку хранения. */
    @Query("DELETE FROM analytics_event WHERE occurred_at < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long): Int

    /**
     * Вытеснение по числу записей: остаются последние `keep`, самые старые уходят первыми.
     *
     * Нужно вдобавок к сроку: всплеск событий иначе упрётся в потолок объёма раньше,
     * чем наступит срок, и очередь перестанет принимать новые.
     */
    @Query(
        """
        DELETE FROM analytics_event WHERE id NOT IN
        (SELECT id FROM analytics_event ORDER BY occurred_at DESC, id DESC LIMIT :keep)
        """,
    )
    suspend fun pruneToLast(keep: Int): Int

    /** Очистка целиком: отказ и выключение аналитики (АН-2). */
    @Query("DELETE FROM analytics_event")
    suspend fun clear(): Int

    /**
     * Есть ли в очереди событие с таким именем и такими свойствами.
     *
     * Ключ дедупликации — имя **вместе** со свойствами (Р-96): `consent_shown` первого запуска
     * и `consent_shown` из настроек — разные факты, и второй не должен считаться дублем.
     *
     * Строка свойств обязана быть собрана тем же способом, что при вставке: формат
     * `ключ=значение` через `&`. Второго формата здесь быть не может — иначе сравнение
     * разъедется молча, и дубль пройдёт незамеченным.
     */
    @Query("SELECT COUNT(*) FROM analytics_event WHERE name = :name AND properties = :properties")
    suspend fun countByNameAndProperties(name: String, properties: String): Int
}
