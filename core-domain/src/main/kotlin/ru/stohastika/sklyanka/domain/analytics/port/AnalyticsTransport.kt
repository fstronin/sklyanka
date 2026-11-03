package ru.stohastika.sklyanka.domain.analytics.port

import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecord

/**
 * Поставщик аналитики — единственная граница с внешним миром (АН-1, TDD §2.3).
 *
 * Порт владеет домен: решение «можно ли отправлять» принимает [AnalyticsRecorder],
 * а адаптер умеет только отдавать пачку и открывать/закрывать передачу. Поставщик
 * не существует до согласия: [start] вызывается только при `ALLOWED` (АН-4).
 */
interface AnalyticsTransport {

    /** false — поставщик не сконфигурирован (нет ключа); ничего не отправит. */
    val available: Boolean

    /** Отдать пачку. false — поставщик не принял; записи остаются в очереди. */
    fun deliver(records: List<AnalyticsRecord>): Boolean

    /** Открыть передачу. Единственная точка инициализации SDK. */
    fun start()

    /** Закрыть передачу: после отзыва согласия не уходит ничего нового. */
    fun stop()
}
