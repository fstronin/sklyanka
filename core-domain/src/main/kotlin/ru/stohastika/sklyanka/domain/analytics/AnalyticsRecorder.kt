package ru.stohastika.sklyanka.domain.analytics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsQueue
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsTransport
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Пределы очереди событий (АН-6, TDD §7.5).
 *
 * Срок — обещание «семь суток без сети»: старое отбрасывается, а не копится. Потолок объёма
 * нужен вдобавок к сроку, иначе всплеск событий упрётся в предел раньше срока. Пачка
 * совпадает с буфером поставщика: при выключении в полёте остаётся не больше одной пачки.
 */
data class AnalyticsLimits(
    val ttl: Duration = DEFAULT_TTL,
    val maxEvents: Int = DEFAULT_MAX_EVENTS,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    private companion object {
        val DEFAULT_TTL: Duration = Duration.ofDays(7)
        const val DEFAULT_MAX_EVENTS: Int = 500
        const val DEFAULT_BATCH_SIZE: Int = 100
    }
}

/**
 * Правило удержания очереди — чистая функция, проверяемая без базы и поставщика.
 *
 * Сначала отбрасывается просроченное (срок хранения), затем — самое старое сверх потолка
 * объёма. Порядок важен: перевод часов вперёд отбрасывает очередь по сроку, а его отсутствие
 * не даёт очереди вырасти выше потолка (TDD §8, §7.5).
 */
object AnalyticsRetention {

    /**
     * @param records очередь в порядке появления: самые старые первыми.
     * @return идентификаторы записей, которые нужно удалить.
     */
    fun drop(records: List<QueuedAnalyticsRecord>, cutoff: Instant, keep: Int): List<Long> {
        val expired = records.filter { it.record.occurredAt < cutoff }
        val fresh = records.filter { it.record.occurredAt >= cutoff }
        val overflow = fresh.take((fresh.size - keep.coerceAtLeast(0)).coerceAtLeast(0))
        return (expired + overflow).map { it.id }
    }
}

/**
 * Шов записи и отправки событий (АН-1, АН-5, TDD §7.1–§7.3).
 *
 * Запись не блокирует вызывающего и не выбрасывает исключений: `record()` запускает корутину
 * в переданной области и глотает любые отказы. Отметка приёма не ждёт аналитики, а сбой
 * поставщика не виден человеку.
 *
 * Гейт согласия — здесь, а не в точках вызова: без `ALLOWED` не пишется и не отправляется
 * ничего, кроме событий о согласии. Те пишутся до согласия, но лежат в очереди, пока его нет;
 * при отказе очередь очищается целиком.
 */
class AnalyticsRecorder(
    private val consentStore: AnalyticsConsentStore,
    private val queue: AnalyticsQueue,
    private val transport: AnalyticsTransport,
    private val time: TimeSource,
    private val scope: CoroutineScope,
    private val limits: AnalyticsLimits = AnalyticsLimits(),
) {

    private val inFlight = AtomicBoolean(false)

    /**
     * Записать продуктовое событие. Без согласия ничего не происходит (АН-2).
     *
     * Событие о согласии сюда не проходит: у него свой путь записи и своя дедупликация.
     */
    fun record(event: AnalyticsEvent) {
        if (event.scope != AnalyticsScope.PRODUCT) return
        quietly { recordNow(event) }
    }

    /**
     * Записать событие о согласии: до согласия — в очередь, при `ALLOWED` — сразу на отправку.
     *
     * Продуктовое событие сюда не проходит: иначе оно попало бы в очередь без согласия.
     */
    fun recordConsent(event: AnalyticsEvent) {
        if (event.scope != AnalyticsScope.CONSENT) return
        quietly { recordConsentNow(event) }
    }

    /** Отправить накопленное: старт приложения и каждое записанное событие (TDD §7.2). */
    fun drain() {
        quietly { drainNow() }
    }

    /** Согласие получено: открыть передачу и отправить накопленное вместе с выбором. */
    fun onConsentGranted() {
        quietly {
            transport.start()
            drainNow()
        }
    }

    /** Согласие отозвано или выключено: прекратить передачу и очистить очередь (АН-2). */
    fun onConsentDeclined() {
        quietly {
            transport.stop()
            queue.clear()
        }
    }

    private fun quietly(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure { if (it is CancellationException) throw it }
        }
    }

    private suspend fun recordNow(event: AnalyticsEvent) {
        if (consentStore.consent.first() != AnalyticsConsentState.ALLOWED) return
        val now = time.now()
        queue.append(event.toRecord(now))
        queue.prune(now.minus(limits.ttl), limits.maxEvents)
        drainNow()
    }

    private suspend fun recordConsentNow(event: AnalyticsEvent) {
        val consent = consentStore.consent.first()
        if (consent == AnalyticsConsentState.DECLINED) return
        if (event is ConsentShown && queue.contains(event.name, event.properties)) return
        queue.append(event.toRecord(time.now()))
        if (consent == AnalyticsConsentState.ALLOWED) drainNow()
    }

    private suspend fun drainNow() {
        if (!inFlight.compareAndSet(false, true)) return
        try {
            if (maySend()) send()
        } finally {
            inFlight.set(false)
        }
    }

    private suspend fun maySend(): Boolean =
        consentStore.consent.first() == AnalyticsConsentState.ALLOWED && transport.available

    private suspend fun send() {
        queue.prune(time.now().minus(limits.ttl), limits.maxEvents)
        val batch = queue.pending(limits.batchSize)
        if (batch.isEmpty()) return
        transport.start()
        if (transport.deliver(batch.map { it.record })) queue.delete(batch.map { it.id })
    }
}
