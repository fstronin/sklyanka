package ru.stohastika.sklyanka.data.reminder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Журнал срабатываний на настоящей базе (НФТ-50, НФТ-51).
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется — поэтому кольцевое вытеснение и запись остаются непроверенными
 * до прогона на устройстве.
 */
@RunWith(AndroidJUnit4::class)
class ReminderLogTest {

    private lateinit var database: SklyankaDatabase
    private val clock = FixedTimeSource(NOW, ZoneId.of("Europe/Moscow"))
    private lateinit var journal: RoomReminderJournal

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SklyankaDatabase::class.java,
        ).build()
        journal = RoomReminderJournal(database.reminderLogDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aFiredReminderIsStoredWithItsDeviationAndChannel() = runTest {
        journal.record(
            ReminderLogEntry(
                occurredAt = NOW,
                event = ReminderEvent.SHOWN,
                intakeId = "intake-1",
                medicationId = "med-1",
                profileId = "profile-1",
                scheduledAt = NOW.minusSeconds(37),
                firedAt = NOW,
                deviationSeconds = 37,
                channel = ReminderChannel.REGULAR,
                delivered = true,
            ),
        )

        val stored = journal.recent(limit = 10).single()
        assertEquals(37L, stored.deviationSeconds)
        assertEquals(ReminderChannel.REGULAR, stored.channel)
        assertEquals(true, stored.delivered)
        assertEquals("intake-1", stored.intakeId)
    }

    @Test
    fun theLastRecordOfAKindIsFoundByItsKind() = runTest {
        journal.record(ReminderLogEntry(occurredAt = NOW, event = ReminderEvent.RESCHEDULED, reason = "boot"))
        journal.record(
            ReminderLogEntry(
                occurredAt = NOW.plusSeconds(60),
                event = ReminderEvent.RESCHEDULED,
                reason = "manual",
            ),
        )

        assertEquals("manual", journal.last(ReminderEvent.RESCHEDULED)?.reason)
        assertNull(journal.last(ReminderEvent.TEST))
    }

    @Test
    fun firedIntakesAreListedForTheOutageDetector() = runTest {
        journal.record(
            ReminderLogEntry(occurredAt = NOW, event = ReminderEvent.FIRED, intakeId = "intake-1"),
        )
        journal.record(
            ReminderLogEntry(
                occurredAt = NOW.minus(Duration.ofDays(2)),
                event = ReminderEvent.FIRED,
                intakeId = "intake-old",
            ),
        )

        val recent = journal.firedIntakeIds(NOW.minus(Duration.ofDays(1)))

        assertEquals(setOf("intake-1"), recent)
    }

    @Test
    fun recordsOlderThanTheRetentionPeriodAreEvicted() = runTest {
        journal.record(
            ReminderLogEntry(
                occurredAt = NOW.minus(Duration.ofDays(31)),
                event = ReminderEvent.SHOWN,
                intakeId = "old",
            ),
        )
        journal.record(ReminderLogEntry(occurredAt = NOW, event = ReminderEvent.SHOWN, intakeId = "fresh"))

        journal.prune(ReminderLogRetention.KEEP_DAYS, ReminderLogRetention.KEEP_RECORDS)

        assertEquals(listOf("fresh"), journal.recent(limit = 10).map { it.intakeId })
    }

    @Test
    fun theNumberOfRecordsIsBounded() = runTest {
        val keep = 5
        repeat(keep * 2) { index ->
            journal.record(
                ReminderLogEntry(
                    occurredAt = NOW.plusSeconds(index.toLong()),
                    event = ReminderEvent.SHOWN,
                    intakeId = "intake-$index",
                ),
            )
        }

        journal.prune(keepDays = ReminderLogRetention.KEEP_DAYS, keepRecords = keep)

        val stored = journal.recent(limit = 100)
        assertEquals(keep, stored.size)
        assertTrue("the newest records must survive", stored.first().intakeId == "intake-9")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-25T11:00:00Z")
    }
}
