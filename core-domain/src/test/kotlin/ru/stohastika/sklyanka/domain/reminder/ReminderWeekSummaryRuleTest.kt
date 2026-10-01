package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Недельная сводка «сработало и пропущено» (ОВ-49, SKL-79).
 *
 * Срабатывания — по `SHOWN`, пропущенные — по `MISSED_SWEEP`; остальные виды записей
 * в сводку не входят, иначе она отвечала бы не на тот вопрос.
 */
class ReminderWeekSummaryRuleTest {

    private val now: Instant = Instant.parse("2026-09-25T10:00:00Z")

    @Test
    fun `firings and missed intakes are counted by their events`() {
        val entries = listOf(
            entry(ReminderEvent.SHOWN),
            entry(ReminderEvent.SHOWN),
            entry(ReminderEvent.REPEATED),
            entry(ReminderEvent.MISSED_SWEEP),
            entry(ReminderEvent.RESCHEDULED),
            entry(ReminderEvent.MISSED_SWEEP),
        )

        val summary = ReminderWeekSummaryRule.summarize(entries)

        assertEquals(2, summary.fired)
        assertEquals(2, summary.missed)
        assertTrue(summary.hasData)
    }

    @Test
    fun `an empty window has nothing to say`() {
        val summary = ReminderWeekSummaryRule.summarize(emptyList())

        assertEquals(0, summary.fired)
        assertEquals(0, summary.missed)
        assertFalse(summary.hasData)
    }

    private fun entry(event: ReminderEvent) = ReminderLogEntry(occurredAt = now, event = event)
}
