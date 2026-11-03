package ru.stohastika.sklyanka.data.analytics

import ru.stohastika.sklyanka.data.room.dao.AnalyticsDao
import ru.stohastika.sklyanka.data.room.entity.AnalyticsEventEntity
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecord
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScope
import ru.stohastika.sklyanka.domain.analytics.QueuedAnalyticsRecord
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsQueue
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Очередь событий аналитики поверх таблицы `analytics_event` (TDD §3.2).
 *
 * Хранилище умеет только то, что умеет SQL: порядок, пачку, удаление отправленного и два
 * вытеснения. Решение «сколько и чего хранить» принимает домен (`AnalyticsRetention`), а здесь
 * остаётся перевод записей домена в строки и обратно.
 *
 * **Свойства сериализуются здесь и только здесь** одним форматом: пары `ключ=значение`,
 * разделитель `&`. Второго формата в проекте быть не может: `AnalyticsDao.countByNameAndProperties`
 * сравнивает строки дословно, и разъехавшийся формат молча пропустил бы дубль. Пары
 * упорядочены по проводному имени ключа, поэтому одинаковый набор свойств даёт одинаковую
 * строку независимо от порядка, в котором его собрал вызывающий. Значения — проводные имена
 * закрытых перечислений домена, ни `&`, ни `=` в них не встречается, поэтому экранирование
 * не заводится.
 *
 * Нечитаемая строка (имя, контур или ключ свойства из будущей сборки) пропускается, а не
 * отправляется наугад: `pending` — путь наружу, и нести туда событие, которого нет в каталоге,
 * нельзя. Такая строка остаётся в базе до вытеснения по сроку или объёму — ровно как чужая
 * запись журнала в `RoomOperationLog`.
 */
@Singleton
class RoomAnalyticsQueue @Inject constructor(private val dao: AnalyticsDao) : AnalyticsQueue {

    override suspend fun append(record: AnalyticsRecord) {
        dao.insert(record.toEntity())
    }

    override suspend fun pending(limit: Int): List<QueuedAnalyticsRecord> =
        dao.pending(limit.coerceAtLeast(0)).mapNotNull(::toQueued)

    /** Пустой список — не ошибка и не повод идти в базу: `DELETE … IN ()` синтаксически неверен. */
    override suspend fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.delete(ids)
    }

    /**
     * Сначала срок, потом объём — двумя запросами, как их и задумал DAO.
     *
     * `keep` не бывает отрицательным: в SQLite отрицательный `LIMIT` означает «без предела»,
     * и вытеснение по объёму молча не сработало бы.
     */
    override suspend fun prune(cutoff: Instant, keep: Int) {
        dao.pruneOlderThan(cutoff.toEpochMilli())
        dao.pruneToLast(keep.coerceAtLeast(0))
    }

    override suspend fun clear() {
        dao.clear()
    }

    override suspend fun contains(name: AnalyticsEventName, properties: Map<AnalyticsProperty, String>): Boolean =
        dao.countByNameAndProperties(name.wire, encode(properties)) > 0

    private fun AnalyticsRecord.toEntity(): AnalyticsEventEntity = AnalyticsEventEntity(
        name = name.wire,
        properties = encode(properties),
        scope = scope.wire,
        occurredAt = occurredAt.toEpochMilli(),
    )

    private fun toQueued(entity: AnalyticsEventEntity): QueuedAnalyticsRecord? {
        val name = AnalyticsEventName.entries.firstOrNull { it.wire == entity.name }
        val scope = AnalyticsScope.entries.firstOrNull { it.wire == entity.scope }
        val properties = decode(entity.properties)
        if (name == null || scope == null || properties == null) return null
        return QueuedAnalyticsRecord(
            id = entity.id,
            record = AnalyticsRecord(
                name = name,
                properties = properties,
                occurredAt = Instant.ofEpochMilli(entity.occurredAt),
                scope = scope,
            ),
        )
    }

    /**
     * Канонический вид свойств: пары по возрастанию проводного имени ключа.
     *
     * Порядок задаёт адаптер, а не вызывающий: сравнение в `contains` строковое, и «source,
     * затем choice» обязано давать ту же строку, что «choice, затем source».
     */
    private fun encode(properties: Map<AnalyticsProperty, String>): String = properties.entries
        .sortedBy { it.key.wire }
        .joinToString(SEPARATOR) { "${it.key.wire}${PAIR}${it.value}" }

    /** Разбор строки; `null` — запись нечитаема целиком и в отправку не пойдёт. */
    private fun decode(stored: String): Map<AnalyticsProperty, String>? {
        if (stored.isEmpty()) return emptyMap()
        val decoded = stored.split(SEPARATOR).map(::decodePair)
        return if (decoded.any { it == null }) null else decoded.filterNotNull().toMap()
    }

    private fun decodePair(part: String): Pair<AnalyticsProperty, String>? {
        val delimiter = part.indexOf(PAIR)
        val key = if (delimiter > 0 && delimiter < part.lastIndex) part.substring(0, delimiter) else return null
        return AnalyticsProperty.entries
            .firstOrNull { it.wire == key }
            ?.let { it to part.substring(delimiter + PAIR.length) }
    }

    private companion object {
        const val SEPARATOR = "&"

        const val PAIR = "="
    }
}
