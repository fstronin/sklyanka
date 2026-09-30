package ru.stohastika.sklyanka.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import ru.stohastika.sklyanka.data.room.entity.ReminderLogEntity

/**
 * Журнал срабатываний напоминаний.
 *
 * Наружу не уходит: сети у приложения нет (НАП-39). Читается экраном диагностики
 * и обнаружением перерыва.
 *
 * Абстрактный класс, а не интерфейс: идемпотентная запись восстановления (SKL-47)
 * — это «проверить и вставить» под одной транзакцией, а тело метода Room умеет
 * оборачивать только у класса.
 */
@Dao
abstract class ReminderLogDao {

    @Insert
    abstract suspend fun insert(entry: ReminderLogEntity)

    @Query("SELECT * FROM reminder_log ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    abstract suspend fun recent(limit: Int): List<ReminderLogEntity>

    @Query("SELECT * FROM reminder_log WHERE event = :event ORDER BY occurred_at DESC, id DESC LIMIT 1")
    abstract suspend fun last(event: String): ReminderLogEntity?

    @Query("SELECT * FROM reminder_log WHERE intake_id = :intakeId ORDER BY occurred_at DESC, id DESC")
    abstract suspend fun ofIntake(intakeId: String): List<ReminderLogEntity>

    /**
     * Записи, начиная с момента.
     *
     * Условие по времени, а не потолок по числу: сводка «вовремя за 7 дней» и выгрузка
     * журнала файлом (НФТ-51) спрашивают про промежуток, и «последние N» молча меняли бы
     * ответ при всплеске пересборок плана. Индекс по `occurred_at` для этого и есть.
     */
    @Query("SELECT * FROM reminder_log WHERE occurred_at >= :since ORDER BY occurred_at DESC, id DESC")
    abstract suspend fun since(since: Long): List<ReminderLogEntity>

    /**
     * Приёмы, о которых напоминание действительно сработало.
     *
     * Опора обнаружения перерыва (НАП-38): будильник стоял, момент прошёл, а записи нет —
     * значит приложение было остановлено, а не человек проигнорировал напоминание.
     */
    @Query(
        """
        SELECT DISTINCT intake_id FROM reminder_log
        WHERE event = :firedEvent AND occurred_at >= :since AND intake_id IS NOT NULL
        """,
    )
    abstract suspend fun firedIntakeIds(firedEvent: String, since: Long): List<String>

    @Query("DELETE FROM reminder_log WHERE occurred_at < :before")
    abstract suspend fun pruneOlderThan(before: Long): Int

    /**
     * Вытеснение по числу записей (НФТ-51): всплеск срабатываний иначе съест место
     * раньше, чем наступит срок хранения.
     */
    @Query(
        """
        DELETE FROM reminder_log WHERE id NOT IN
        (SELECT id FROM reminder_log ORDER BY id DESC LIMIT :keep)
        """,
    )
    abstract suspend fun pruneToLast(keep: Int): Int

    @Query("SELECT COUNT(*) FROM reminder_log")
    abstract suspend fun count(): Int

    /**
     * Сколько записей о восстановлении того же факта уже лежит в журнале (SKL-47).
     *
     * Факт — вид, исход, приём, момент срабатывания и номер показа. `reason` в условии
     * обязателен: запись об исходе `dropped` пишется по тому же приёму и тому же
     * срабатыванию и не имеет права подавить восстановление.
     */
    @Query(
        """
        SELECT COUNT(*) FROM reminder_log
        WHERE event = :event AND reason = :reason AND intake_id = :intakeId
          AND fired_at = :firedAt AND attempt = :attempt
        """,
    )
    abstract suspend fun restoredFacts(event: String, reason: String, intakeId: String, firedAt: Long, attempt: Int): Int

    /**
     * Записать восстановление, если такого факта ещё нет.
     *
     * Транзакция обязательна: без неё проверка и вставка могут лечь на разные соединения
     * пула, и две одновременные пересборки обе увидят «ещё нет» — ровно тот дубль,
     * который чинит SKL-47. Транзакция берёт одно соединение и сериализует пару.
     */
    @Transaction
    open suspend fun insertRestoredOnce(entry: ReminderLogEntity) {
        val event = entry.event
        val reason = entry.reason
        val intakeId = entry.intakeId
        val firedAt = entry.firedAt
        val attempt = entry.attempt
        val known = reason != null && intakeId != null && firedAt != null && attempt != null &&
            restoredFacts(event, reason, intakeId, firedAt, attempt) > 0
        if (!known) insert(entry)
    }
}
