package ru.heuri.sklyanka.domain.reminder.port

import ru.heuri.sklyanka.domain.reminder.ReminderEvent
import ru.heuri.sklyanka.domain.reminder.ReminderLogEntry
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
}
