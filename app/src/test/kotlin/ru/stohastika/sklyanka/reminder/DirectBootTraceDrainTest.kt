package ru.stohastika.sklyanka.reminder

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.reminder.DirectBootTrace
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootTraceStore
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import java.time.Instant

/**
 * Перенос следов locked-фазы в журнал (SKL-87, НФТ-19).
 *
 * Одна трасса — одна запись о пересборке, без идентификаторов приёма и медицинских данных;
 * `drain` очищает очередь, поэтому повторный вызов ничего не добавляет (НАП-14).
 */
class DirectBootTraceDrainTest {

    private class FakeTraceStore(var traces: MutableList<DirectBootTrace>) : DirectBootTraceStore {
        override suspend fun append(trace: DirectBootTrace) {
            traces += trace
        }

        override suspend fun drain(): List<DirectBootTrace> = traces.toList().also { traces.clear() }
    }

    private class FakeJournal : ReminderJournal {
        val entries = mutableListOf<ReminderLogEntry>()
        override suspend fun record(entry: ReminderLogEntry) {
            entries += entry
        }

        override suspend fun recent(limit: Int): List<ReminderLogEntry> = entries.takeLast(limit)
        override suspend fun last(event: ReminderEvent): ReminderLogEntry? = entries.lastOrNull { it.event == event }

        override suspend fun firedIntakeIds(since: Instant): Set<String> = emptySet()
        override suspend fun prune(keepDays: Long, keepRecords: Int) = Unit
    }

    private val now: Instant = Instant.parse("2026-09-27T02:05:00Z")

    @Test
    fun `a trace becomes one reschedule entry with no medical identifiers`() = runTest {
        val traces = FakeTraceStore(
            mutableListOf(
                DirectBootTrace(now, replayed = 2, skipped = 1, failed = 0, reason = "replayed"),
            ),
        )
        val journal = FakeJournal()

        DirectBootTraceDrain(traces, journal).drainIntoJournal()

        val entry = journal.entries.single()
        assertEquals(ReminderEvent.RESCHEDULED, entry.event)
        assertEquals(RescheduleReason.LOCKED_BOOT.code, entry.reason)
        assertEquals(2, entry.alarmsScheduled)
        assertEquals(1, entry.alarmsCancelled)
        assertNull("the entry is about a rebuild, not an intake", entry.intakeId)
        assertNull(entry.medicationId)
        assertNull(entry.profileId)
    }

    @Test
    fun `a repeated drain records nothing twice`() = runTest {
        val traces = FakeTraceStore(
            mutableListOf(DirectBootTrace(now, replayed = 1, skipped = 0, failed = 0, reason = "replayed")),
        )
        val journal = FakeJournal()
        val drain = DirectBootTraceDrain(traces, journal)

        drain.drainIntoJournal()
        drain.drainIntoJournal()

        assertEquals(1, journal.entries.size)
        assertTrue(traces.traces.isEmpty())
    }
}
