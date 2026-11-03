package ru.stohastika.sklyanka.feature.onboarding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecord
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRetention
import ru.stohastika.sklyanka.domain.analytics.QueuedAnalyticsRecord
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsQueue
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsTransport
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant

/**
 * Подмены аналитики для проверок экрана «Настроить надёжность».
 *
 * `AnalyticsRecorder` — конкретный класс домена, а не интерфейс, поэтому подменяются его
 * порты: согласие, очередь, поставщик и часы. Гейт согласия, удержание очереди и отправка
 * при этом остаются настоящими — проверяется то, что работает в продукте, а не его копия.
 *
 * Запись асинхронна: `record` запускает корутину в переданной области, поэтому в тестах
 * область — область прогона, а события появляются после `advanceUntilIdle()`.
 */
internal class AnalyticsWorld(consent: AnalyticsConsentState = AnalyticsConsentState.ALLOWED) {

    val store = FakeAnalyticsConsentStore(consent)
    val queue = FakeAnalyticsQueue()
    val transport = FakeAnalyticsTransport()

    private val time = FixedTimeSource(NOW)

    /** Настоящий рекордер поверх подмен: пишет в очередь и отдаёт пачки поставщику в `scope`. */
    fun recorder(scope: CoroutineScope): AnalyticsRecorder = AnalyticsRecorder(store, queue, transport, time, scope)

    /**
     * Записанные продуктовые события в порядке записи.
     *
     * Журнал ведёт очередь: отправка удаляет записи из неё, но не из журнала, поэтому
     * порядок здесь — порядок записи, а не порядок доставки.
     */
    fun records(): List<AnalyticsRecord> = queue.recorded

    fun names(): List<AnalyticsEventName> = records().map { it.name }

    /** Значения одного свойства в порядке записи: `entry`, `step`, `result`, `outcome`. */
    fun property(property: AnalyticsProperty): List<String> = records().mapNotNull { it.properties[property] }

    companion object {
        val NOW: Instant = Instant.parse("2026-10-01T08:00:00Z")
    }
}

/** Согласие в памяти: поток отдаёт изменение сразу, решение считается один раз. */
internal class FakeAnalyticsConsentStore(initial: AnalyticsConsentState) : AnalyticsConsentStore {

    private val state = MutableStateFlow(initial)

    override val consent: Flow<AnalyticsConsentState> = state.asStateFlow()

    override suspend fun decide(state: AnalyticsConsentState) {
        this.state.value = state
    }
}

/** Поставщик в памяти: принимает всё отправленное и помнит порядок. */
internal class FakeAnalyticsTransport : AnalyticsTransport {

    override val available: Boolean = true

    /** Записи принятых пачек — то, что действительно ушло. */
    val delivered: MutableList<AnalyticsRecord> = mutableListOf()

    override fun deliver(records: List<AnalyticsRecord>): Boolean {
        delivered += records
        return true
    }

    override fun start() = Unit

    override fun stop() = Unit
}

/** Очередь в памяти: порядок появления, как у таблицы, вытеснение — общим правилом удержания. */
internal class FakeAnalyticsQueue : AnalyticsQueue {

    private var nextId: Long = 1
    private val rows: MutableList<QueuedAnalyticsRecord> = mutableListOf()

    val size: Int get() = rows.size
    val names: List<AnalyticsEventName> get() = rows.map { it.record.name }
    val records: List<AnalyticsRecord> get() = rows.map { it.record }

    /** Всё, что когда-либо ставили в очередь: удаление отправленного этой истории не меняет. */
    val recorded: MutableList<AnalyticsRecord> = mutableListOf()

    override suspend fun append(record: AnalyticsRecord) {
        recorded += record
        rows += QueuedAnalyticsRecord(nextId++, record)
        rows.sortWith(compareBy({ it.record.occurredAt }, { it.id }))
    }

    override suspend fun pending(limit: Int): List<QueuedAnalyticsRecord> = rows.take(limit)

    override suspend fun delete(ids: List<Long>) {
        rows.removeAll { it.id in ids }
    }

    override suspend fun prune(cutoff: Instant, keep: Int) {
        delete(AnalyticsRetention.drop(rows, cutoff, keep))
    }

    override suspend fun clear() {
        rows.clear()
    }

    override suspend fun contains(name: AnalyticsEventName, properties: Map<AnalyticsProperty, String>): Boolean =
        rows.any { it.record.name == name && it.record.properties == properties }
}
