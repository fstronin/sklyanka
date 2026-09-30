package ru.stohastika.sklyanka.data.reminder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.entity.ReminderLogEntity
import ru.stohastika.sklyanka.domain.reminder.DeferredReason
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
    }

    /**
     * Запись вида «пробное напоминание», оставленная прежней сборкой, переживает обновление:
     * неизвестный вид выпадает из чтения (МГ-6), но в хранилище остаётся — миграции и
     * удаления исторических строк задача не делает (`SKL-32/TDD.md §4`).
     */
    @Test
    fun aTestRecordFromAnOlderBuildStaysInStorageAndOutOfSight() = runTest {
        database.reminderLogDao().insert(
            ReminderLogEntity(
                occurredAt = NOW.toEpochMilli(),
                event = "TEST",
                intakeId = null,
                medicationId = null,
                profileId = null,
                scheduledAt = null,
                firedAt = NOW.toEpochMilli(),
                deviationSeconds = 0,
                channel = null,
                delivered = true,
                reason = null,
                alarmsCancelled = null,
                alarmsScheduled = null,
                attempt = null,
                snoozeMinutes = null,
                watchResult = null,
            ),
        )
        journal.record(
            ReminderLogEntry(occurredAt = NOW.plusSeconds(1), event = ReminderEvent.SHOWN, intakeId = "intake-1"),
        )

        assertEquals("the old record must survive in storage", 2, database.reminderLogDao().count())
        assertEquals(listOf("intake-1"), journal.recent(limit = 10).map { it.intakeId })
        assertEquals(listOf("intake-1"), journal.since(NOW.minusSeconds(1)).map { it.intakeId })
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

    /**
     * Восстановление пишется один раз на факт (SKL-47).
     *
     * Две пересборки могут позвать запись с одним и тем же фактом — состояние и будильники
     * у них идемпотентны, и журнал обязан быть идемпотентным. Другой момент срабатывания —
     * другой факт, он не подавляется; запись об исходе `dropped` того же срабатывания
     * восстановлению не помеха.
     */
    @Test
    fun aRestorationIsRecordedOncePerFact() = runTest {
        val firedAt = NOW.plusSeconds(900)
        journal.recordRestored(restoredEntry(firedAt))
        journal.recordRestored(restoredEntry(firedAt))
        assertEquals("the same fact is one entry", 1, journal.recent(limit = 10).size)

        val anotherMoment = firedAt.plusSeconds(60)
        journal.recordRestored(restoredEntry(anotherMoment))
        assertEquals("another moment is another fact", 2, journal.recent(limit = 10).size)

        journal.record(
            ReminderLogEntry(
                occurredAt = NOW.plusSeconds(5),
                event = ReminderEvent.DEFERRED,
                reason = DeferredReason.DROPPED,
                intakeId = "intake-1",
                firedAt = firedAt,
                attempt = 0,
            ),
        )
        journal.recordRestored(restoredEntry(firedAt.plusSeconds(120)))
        assertEquals(
            "a dropped entry does not suppress a restoration",
            1,
            journal.recent(limit = 10).count { it.reason == DeferredReason.RESTORED && it.firedAt == firedAt },
        )
        assertEquals(4, journal.recent(limit = 10).size)
    }

    private fun restoredEntry(firedAt: Instant): ReminderLogEntry = ReminderLogEntry(
        occurredAt = NOW,
        event = ReminderEvent.DEFERRED,
        reason = DeferredReason.RESTORED,
        intakeId = "intake-1",
        medicationId = "med-1",
        profileId = "profile-1",
        firedAt = firedAt,
        attempt = 0,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-25T11:00:00Z")
    }
}
