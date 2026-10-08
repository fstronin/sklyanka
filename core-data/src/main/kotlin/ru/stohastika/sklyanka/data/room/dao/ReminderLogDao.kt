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
    abstract suspend fun deleteOlderThan(before: Long): Int

    /**
     * Вытеснение по объёму (НФТ-51, ПМ-30): старейшие снаружи неприкосновенного окна.
     *
     * `occurred_at < :protectedSince` стоит в самом запросе, а не только в домене:
     * окно неприкосновенно даже при ошибке вызывающего. Ноль удалённых строк означает
     * «дальше только окно» — и цикл вытеснения останавливается.
     */
    @Query(
        """
        DELETE FROM reminder_log WHERE id IN (
            SELECT id FROM reminder_log
            WHERE occurred_at < :protectedSince
            ORDER BY occurred_at ASC, id ASC
            LIMIT :limit
        )
        """,
    )
    abstract suspend fun deleteOldest(protectedSince: Long, limit: Int): Int

    /**
     * Оценка занятого журналом объёма в байтах (НФТ-51, ПМ-30).
     *
     * `LENGTH(CAST(x AS BLOB))` — UTF-8-длина текста, а для чисел — длина их десятичной
     * записи: намеренный перебор против varint, чтобы потолок не оказался превышен
     * из-за недооценки. Константа 12 — заголовок записи и rowid; перебор по числам
     * с запасом покрывает и индексные записи, которые построчно не считаются
     * (`docs/archive/tasks/SKL-65/TDD.md §8`).
     */
    @Query(
        """
        SELECT CAST(COALESCE(SUM(
            12
            + COALESCE(LENGTH(CAST(occurred_at AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(event AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(intake_id AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(medication_id AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(profile_id AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(scheduled_at AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(fired_at AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(deviation_seconds AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(channel AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(delivered AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(reason AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(alarms_cancelled AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(alarms_scheduled AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(attempt AS BLOB)), 0)
            + COALESCE(LENGTH(CAST(snooze_minutes AS BLOB)), 0)
        ), 0) AS INTEGER) FROM reminder_log
        """,
    )
    abstract suspend fun storedBytes(): Long

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
    abstract suspend fun restoredFacts(
        event: String,
        reason: String,
        intakeId: String,
        firedAt: Long,
        attempt: Int,
    ): Int

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
        val known = reason != null &&
            intakeId != null &&
            firedAt != null &&
            attempt != null &&
            restoredFacts(event, reason, intakeId, firedAt, attempt) > 0
        if (!known) insert(entry)
    }

    /**
     * Записать среду измерения, если она изменилась (SKL-85).
     *
     * Сравнивается нагрузка последней записи того же вида: журнал — журнал изменений,
     * и пересборка на неизменной среде не имеет права добавлять строку (по образцу SKL-47).
     * Транзакция обязательна по той же причине, что у восстановления: без неё две
     * одновременные пересборки (запуск и перезагрузка) обе увидят «ещё нет» и утроят
     * запись среды, а по её границам считается измерение.
     */
    @Transaction
    open suspend fun insertEnvironmentOnce(entry: ReminderLogEntity) {
        if (last(entry.event)?.reason != entry.reason) insert(entry)
    }
}
