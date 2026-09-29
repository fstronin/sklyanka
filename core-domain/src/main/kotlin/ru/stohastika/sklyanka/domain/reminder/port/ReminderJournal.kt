package ru.stohastika.sklyanka.domain.reminder.port

import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import java.time.Instant

/**
 * Журнал срабатываний (НАП-4, НФТ-50).
 *
 * Журнал — часть продукта, а не отладка: без него жалоба «напоминание не пришло»
 * неразбираема в принципе. Он локальный и наружу не уходит (НАП-39, ПЛ-3).
 */
interface ReminderJournal {

    suspend fun record(entry: ReminderLogEntry)

    /** Последние записи для диагностики — не менее 50 (НАП-37). */
    suspend fun recent(limit: Int): List<ReminderLogEntry>

    /** Последняя запись указанного вида: «последнее сработавшее», «последняя пересборка». */
    suspend fun last(event: ReminderEvent): ReminderLogEntry?

    /**
     * Записи, начиная с момента, — от новых к старым.
     *
     * Нужны сводке «сколько напоминаний сработало вовремя за последние 7 дней» и выгрузке
     * журнала файлом (НФТ-51): обе смотрят на промежуток времени, а не на «последние N».
     *
     * Реализация по умолчанию выражена через [recent] и потому ограничена сверху
     * [FALLBACK_LIMIT]: она годится фейкам и любому хранилищу без выборки по времени.
     * Хранилище на Room переопределяет её запросом с условием по `occurred_at` — там
     * потолка нет, иначе выгрузка теряла бы самые старые записи хранения молча.
     */
    suspend fun since(from: Instant): List<ReminderLogEntry> =
        recent(FALLBACK_LIMIT).filter { !it.occurredAt.isBefore(from) }

    /**
     * Приёмы, о которых напоминание действительно сработало, начиная с момента.
     *
     * Нужно обнаружению перерыва (НАП-38): «будильник стоял, момент прошёл, записи
     * о срабатывании нет» — единственный доступный признак принудительной остановки.
     */
    suspend fun firedIntakeIds(since: Instant): Set<String>

    /**
     * Кольцевое вытеснение: сначала по возрасту, затем по числу записей (НФТ-51).
     *
     * Вызывается вне транзакции основной операции: подрезка журнала не имеет права
     * откатить отметку приёма.
     */
    suspend fun prune(keepDays: Long, keepRecords: Int)

    companion object {
        /**
         * Потолок реализации [since] по умолчанию.
         *
         * Значение того же порядка, что предел хранения журнала: реализация без выборки
         * по времени не должна тихо превращать «записи за 30 суток» в «первую сотню».
         */
        const val FALLBACK_LIMIT: Int = 8_000
    }
}
