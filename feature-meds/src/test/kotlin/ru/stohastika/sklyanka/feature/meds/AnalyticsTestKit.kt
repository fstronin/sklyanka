package ru.stohastika.sklyanka.feature.meds

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
 * Аналитика в памяти для тестов лекарств (SKL-165).
 *
 * `AnalyticsRecorder` — конкретный класс домена, поэтому подмены портов повторяются
 * в каждом модуле: общего тестового артефакта у `feature-*` нет, а зависимость
 * на чужие тесты запрещена (verifyModuleDependencies). Набор здесь ровно тот,
 * что нужен проверкам событий: очередь помнит записи, поставщик — отправленное.
 */
internal class FakeAnalyticsConsentStore(initial: AnalyticsConsentState = AnalyticsConsentState.ALLOWED) :
    AnalyticsConsentStore {

    private val state = MutableStateFlow(initial)

    override val consent: Flow<AnalyticsConsentState> = state.asStateFlow()

    val current: AnalyticsConsentState get() = state.value

    override suspend fun decide(state: AnalyticsConsentState) {
        this.state.value = state
    }
}

/** Поставщик в памяти: принимает всё, поэтому запись видна отправленной. */
internal class FakeAnalyticsTransport : AnalyticsTransport {

    val delivered: MutableList<AnalyticsRecord> = mutableListOf()

    override var available: Boolean = true

    override fun deliver(records: List<AnalyticsRecord>): Boolean {
        delivered += records
        return true
    }

    override fun start() = Unit

    override fun stop() = Unit
}

/** Очередь в памяти: порядок появления, вытеснение — общим правилом удержания. */
internal class FakeAnalyticsQueue : AnalyticsQueue {

    private var nextId: Long = 1
    private val rows: MutableList<QueuedAnalyticsRecord> = mutableListOf()

    val names: List<AnalyticsEventName> get() = rows.map { it.record.name }
    val records: List<AnalyticsRecord> get() = rows.map { it.record }

    /**
     * Всё, что когда-либо ставили в очередь.
     *
     * Отправка удаляет записи из очереди, поэтому проверять события нужно здесь:
     * иначе при выданном согласии очередь к концу действия уже пуста.
     */
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

/**
 * Мир аналитики теста: согласие, очередь, поставщик и рекордер в переданной области.
 *
 * Область — планировщик самого теста: запись асинхронна, и `advanceUntilIdle()`
 * делает её видимой так же, как это происходит в приложении.
 */
internal class AnalyticsTestKit(
    scope: CoroutineScope,
    consent: AnalyticsConsentState = AnalyticsConsentState.ALLOWED,
) {

    val store = FakeAnalyticsConsentStore(consent)
    val queue = FakeAnalyticsQueue()
    val transport = FakeAnalyticsTransport()
    private val time = FixedTimeSource(Instant.parse("2026-05-01T08:00:00Z"))

    val recorder: AnalyticsRecorder = AnalyticsRecorder(store, queue, transport, time, scope)
}
