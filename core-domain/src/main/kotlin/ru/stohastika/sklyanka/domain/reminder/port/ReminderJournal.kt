package ru.stohastika.sklyanka.domain.reminder.port

import ru.stohastika.sklyanka.domain.reminder.DeferredReason
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import java.time.Instant

/**
 * Факт восстановления отложенного срабатывания (SKL-47).
 *
 * Восстановление описывается приёмом, моментом из постоянной записи и номером показа:
 * две пересборки, поднявшие один и тот же отложенный повтор, говорят об одном факте,
 * даже если прошли по отдельности. Другое откладывание, другой момент или другой номер
 * показа — другой факт.
 */
data class RestoredDeferredFact(val intakeId: String, val firedAt: Instant, val attempt: Int)

/**
 * Факт восстановления, о котором запись журнала, или `null` — запись не о восстановлении.
 *
 * Исход `dropped` пишется по тому же приёму и тому же срабатыванию, что и `restored`,
 * и в ключ факта не входит: снятие записи не имеет права подавить её восстановление.
 * Момент записи (`occurredAt`) в ключ не входит тоже: у двух одновременных пересборок он
 * разный, а факт один.
 */
@Suppress("ReturnCount")
fun ReminderLogEntry.restoredDeferredFact(): RestoredDeferredFact? {
    if (event != ReminderEvent.DEFERRED || reason != DeferredReason.RESTORED) return null
    return RestoredDeferredFact(
        intakeId = intakeId ?: return null,
        firedAt = firedAt ?: return null,
        attempt = attempt ?: return null,
    )
}

/**
 * Журнал срабатываний (НАП-4, НФТ-50).
 *
 * Журнал — часть продукта, а не отладка: без него жалоба «напоминание не пришло»
 * неразбираема в принципе. Он локальный и наружу не уходит (НАП-39, ПЛ-3).
 */
interface ReminderJournal {

    suspend fun record(entry: ReminderLogEntry)

    /**
     * Записать след восстановления отложенного срабатывания — не более одного на факт.
     *
     * Пересборки могут идти одновременно (запуск и перезагрузка): обе видят запись
     * и обе ставят будильник, потому что состояние и будильники идемпотентны, — а журнал
     * обязан остаться журналом изменений, а не повторов (SKL-47). Ключ факта —
     * [restoredDeferredFact]; разные факты не подавляются.
     *
     * Реализация по умолчанию выражена через [recent] и годится хранилищам в памяти;
     * реализация на Room переопределяет её одной транзакцией «проверить и вставить».
     */
    suspend fun recordRestored(entry: ReminderLogEntry) {
        val fact = entry.restoredDeferredFact() ?: return record(entry)
        val known = recent(FALLBACK_LIMIT).any { it.restoredDeferredFact() == fact }
        if (!known) record(entry)
    }

    /**
     * Записать среду измерения, если она изменилась (SKL-85, НАП-14).
     *
     * Сравнение с последней записью среды, а не с любой: журнал — журнал изменений,
     * и пересборка на неизменной среде не имеет права добавлять строку (по образцу SKL-47).
     * Реализация по умолчанию годится хранилищам в памяти; Room переопределяет её одной
     * транзакцией «проверить и вставить».
     */
    suspend fun recordEnvironment(entry: ReminderLogEntry) {
        if (last(ReminderEvent.ENVIRONMENT)?.reason != entry.reason) record(entry)
    }

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
