package ru.stohastika.sklyanka.feature.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLimits
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
 * Подставные границы аналитики для проверок модуля (SKL-165).
 *
 * Копия фейков домена (`core-domain/src/test/.../AnalyticsFakes.kt`), а не зависимость
 * от них: доменный тестовый набор другому модулю не виден, а проверке истории нужен тот же
 * наблюдаемый мир — согласие в памяти, очередь с порядком и поставщик с журналом.
 */
internal class FakeAnalyticsConsentStore(initial: AnalyticsConsentState = AnalyticsConsentState.NOT_ASKED) :
    AnalyticsConsentStore {

    private val state = MutableStateFlow(initial)

    override val consent: Flow<AnalyticsConsentState> = state.asStateFlow()

    val current: AnalyticsConsentState get() = state.value

    override suspend fun decide(state: AnalyticsConsentState) {
        this.state.value = state
    }
}

/** Поставщик в памяти: помнит попытки отправки, поэтому видно и отказ, и повтор. */
internal class FakeAnalyticsTransport(override var available: Boolean = true, private val accepts: Boolean = true) :
    AnalyticsTransport {

    /** Записи принятых пачек — то, что действительно ушло. */
    val delivered: MutableList<AnalyticsRecord> = mutableListOf()

    var starts: Int = 0
        private set

    var stops: Int = 0
        private set

    override fun deliver(records: List<AnalyticsRecord>): Boolean {
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
internal class FakeAnalyticsQueue : AnalyticsQueue {

    private var nextId: Long = 1
    private val rows: MutableList<QueuedAnalyticsRecord> = mutableListOf()

    val size: Int get() = rows.size
    val names: List<AnalyticsEventName> get() = rows.map { it.record.name }

    override suspend fun append(record: AnalyticsRecord) {
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

/**
 * Мир аналитики модуля: четыре подмены и управляемые часы.
 *
 * Тесты пишут сценарий как человек: «включил», «разрешил», «прошло время» — и смотрят
 * на очередь и журнал поставщика, а не на внутренние поля рекордера.
 */
internal class AnalyticsTestKit(
    consent: AnalyticsConsentState = AnalyticsConsentState.NOT_ASKED,
    accepts: Boolean = true,
    available: Boolean = true,
    now: Instant = Instant.parse("2026-09-25T07:00:00Z"),
) {

    val store = FakeAnalyticsConsentStore(consent)
    val queue = FakeAnalyticsQueue()
    val transport = FakeAnalyticsTransport(available = available, accepts = accepts)
    val time = FixedTimeSource(now)

    /**
     * Рекордер стенда: события стенду не проверяются, поэтому область не привязана
     * к диспетчеру проверки и ничего не удерживает.
     */
    fun recorderForStand(): AnalyticsRecorder = recorder(CoroutineScope(Dispatchers.Unconfined))

    fun recorder(scope: CoroutineScope): AnalyticsRecorder = AnalyticsRecorder(
        consentStore = store,
        queue = queue,
        transport = transport,
        time = time,
        scope = scope,
        limits = AnalyticsLimits(),
    )
}
