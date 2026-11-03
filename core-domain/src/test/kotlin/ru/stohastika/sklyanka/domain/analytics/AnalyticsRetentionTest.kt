package ru.stohastika.sklyanka.domain.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Удержание очереди (АН-6): срок и потолок объёма отбрасывают самые старые записи первыми.
 *
 * Правило проверяется без базы, Room и поставщика: «семь суток без сети» и «не больше 500»
 * — это одно решение, и его ошибка стоит либо потерянной метрики, либо растущей базы.
 */
class AnalyticsRetentionTest {

    private val start: Instant = Instant.parse("2026-05-01T08:00:00Z")

    @Test
    fun `expired records are dropped`() {
        val queue = listOf(
            queued(1, "2026-04-20T08:00:00Z"),
            queued(2, "2026-04-23T07:59:59Z"),
            queued(3, "2026-04-30T08:00:00Z"),
        )

        assertEquals(listOf(1L, 2L), AnalyticsRetention.drop(queue, cutoff = daysBefore(7), keep = 500))
    }

    @Test
    fun `a record exactly at the cutoff survives`() {
        val queue = listOf(queued(1, "2026-04-24T08:00:00Z"))

        assertEquals(emptyList<Long>(), AnalyticsRetention.drop(queue, cutoff = daysBefore(7), keep = 500))
    }

    @Test
    fun `the oldest records over the cap are dropped first`() {
        val queue = listOf(
            queued(1, "2026-04-30T08:00:00Z"),
            queued(2, "2026-04-30T09:00:00Z"),
            queued(3, "2026-04-30T10:00:00Z"),
            queued(4, "2026-04-30T11:00:00Z"),
            queued(5, "2026-04-30T12:00:00Z"),
        )

        assertEquals(listOf(1L, 2L, 3L), AnalyticsRetention.drop(queue, cutoff = daysBefore(7), keep = 2))
    }

    @Test
    fun `the cap counts only the records that survived the cutoff`() {
        val queue = listOf(
            queued(1, "2026-04-01T08:00:00Z"),
            queued(2, "2026-04-02T08:00:00Z"),
            queued(3, "2026-04-30T08:00:00Z"),
            queued(4, "2026-04-30T09:00:00Z"),
        )

        assertEquals(listOf(1L, 2L), AnalyticsRetention.drop(queue, cutoff = daysBefore(7), keep = 2))
    }

    @Test
    fun `a queue within the limits keeps everything`() {
        val queue = listOf(
            queued(1, "2026-04-30T08:00:00Z"),
            queued(2, "2026-04-30T09:00:00Z"),
        )

        assertEquals(emptyList<Long>(), AnalyticsRetention.drop(queue, cutoff = daysBefore(7), keep = 500))
    }

    @Test
    fun `an empty cap drops the queue`() {
        val queue = listOf(
            queued(1, "2026-04-30T08:00:00Z"),
            queued(2, "2026-04-30T09:00:00Z"),
        )

        assertEquals(listOf(1L, 2L), AnalyticsRetention.drop(queue, cutoff = daysBefore(7), keep = 0))
    }

    private fun queued(id: Long, occurredAt: String): QueuedAnalyticsRecord = QueuedAnalyticsRecord(
        id = id,
        record = AnalyticsRecord(
            name = AnalyticsEventName.SCREEN_VIEW,
            properties = mapOf(AnalyticsProperty.SCREEN to "today"),
            occurredAt = Instant.parse(occurredAt),
            scope = AnalyticsScope.PRODUCT,
        ),
    )

    private fun daysBefore(days: Long): Instant = start.minusSeconds(days * 24 * 60 * 60)
}
