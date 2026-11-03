package ru.stohastika.sklyanka.domain.analytics.port

import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecord
import ru.stohastika.sklyanka.domain.analytics.QueuedAnalyticsRecord
import java.time.Instant

/**
 * Очередь событий аналитики (TDD §3.2).
 *
 * Очередь переживает смерть процесса: событие, случившееся без сети, ждёт следующей попытки,
 * а не теряется вместе с экраном. В записи нет ни профиля, ни лекарства, ни приёма —
 * связать событие с сущностью нельзя по схеме, а не по договорённости.
 */
interface AnalyticsQueue {

    /** Поставить событие в очередь. */
    suspend fun append(record: AnalyticsRecord)

    /** Пачка на отправку: самые старые первыми, не больше `limit`. */
    suspend fun pending(limit: Int): List<QueuedAnalyticsRecord>

    /** Удалить отправленное. */
    suspend fun delete(ids: List<Long>)

    /** Убрать записи старше cutoff и всё, что выше keep (самые старые — первыми). */
    suspend fun prune(cutoff: Instant, keep: Int)

    /** Очистить всё: отказ и выключение (АН-2). */
    suspend fun clear()

    /**
     * Есть ли в очереди неотправленное событие с таким именем и такими же свойствами.
     *
     * Ключ — имя и закрытые значения, а не одно имя: повторный показ согласия из другого
     * источника — другой факт и другой показ (Р-96), а повторный показ того же источника
     * дублей не плодит.
     */
    suspend fun contains(name: AnalyticsEventName, properties: Map<AnalyticsProperty, String>): Boolean
}
