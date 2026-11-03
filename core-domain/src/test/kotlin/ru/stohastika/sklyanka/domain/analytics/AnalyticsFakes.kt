package ru.stohastika.sklyanka.domain.analytics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsQueue
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsTransport
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant

/** Согласие в памяти: поток отдаёт изменение сразу, решение считается один раз. */
class FakeAnalyticsConsentStore(initial: AnalyticsConsentState = AnalyticsConsentState.NOT_ASKED) :
    AnalyticsConsentStore {

    private val state = MutableStateFlow(initial)

    override val consent: Flow<AnalyticsConsentState> = state.asStateFlow()

    val current: AnalyticsConsentState get() = state.value

    var writes: Int = 0
        private set

    override suspend fun decide(state: AnalyticsConsentState) {
        this.state.value = state
        writes++
    }
}

/** Поставщик в памяти: помнит попытки отправки, поэтому видно и отказ, и повтор. */
class FakeAnalyticsTransport(override var available: Boolean = true, private val accepts: Boolean = true) :
    AnalyticsTransport {

    /** Все пачки, которые поставщику пытались отдать. */
    val batches: MutableList<List<AnalyticsRecord>> = mutableListOf()

    /** Записи принятых пачек — то, что действительно ушло. */
    val delivered: MutableList<AnalyticsRecord> = mutableListOf()

    var starts: Int = 0
        private set

    var stops: Int = 0
        private set

    override fun deliver(records: List<AnalyticsRecord>): Boolean {
        batches += records
        if (accepts) delivered += records
        return accepts
    }

    override fun start() {
        starts++
    }

    override fun stop() {
        stops++
    }

    fun deliveredNames(): List<AnalyticsEventName> = delivered.map { it.name }
}

/** Очередь в памяти: порядок появления, как у таблицы, вытеснение — общим правилом удержания. */
class FakeAnalyticsQueue : AnalyticsQueue {

    private var nextId: Long = 1
    private val rows: MutableList<QueuedAnalyticsRecord> = mutableListOf()

    val size: Int get() = rows.size
    val names: List<AnalyticsEventName> get() = rows.map { it.record.name }
    val records: List<AnalyticsRecord> get() = rows.map { it.record }

    var appends: Int = 0
        private set

    var clears: Int = 0
        private set

    override suspend fun append(record: AnalyticsRecord) {
        rows += QueuedAnalyticsRecord(nextId++, record)
        rows.sortWith(compareBy({ it.record.occurredAt }, { it.id }))
        appends++
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
        clears++
    }

    override suspend fun contains(name: AnalyticsEventName, properties: Map<AnalyticsProperty, String>): Boolean =
        rows.any { it.record.name == name && it.record.properties == properties }
}

/** Мир домена аналитики: четыре подмены и управляемые часы, чтобы тест читался как сценарий. */
class AnalyticsWorld(
    consent: AnalyticsConsentState = AnalyticsConsentState.NOT_ASKED,
    accepts: Boolean = true,
    available: Boolean = true,
    private val limits: AnalyticsLimits = AnalyticsLimits(),
    now: Instant = Instant.parse("2026-05-01T08:00:00Z"),
) {

    val store = FakeAnalyticsConsentStore(consent)
    val queue = FakeAnalyticsQueue()
    val transport = FakeAnalyticsTransport(available = available, accepts = accepts)
    val time = FixedTimeSource(now)

    fun recorder(scope: CoroutineScope): AnalyticsRecorder =
        AnalyticsRecorder(store, queue, transport, time, scope, limits)
}
