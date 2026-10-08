package ru.stohastika.sklyanka.data.reminder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.entity.ReminderLogEntity
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.DeferredReason
import ru.stohastika.sklyanka.domain.reminder.DeviceEnvironment
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEnvironment
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.ReminderLogRetention
import ru.stohastika.sklyanka.domain.reminder.ReminderLogRotation
import ru.stohastika.sklyanka.domain.reminder.RotateReminderJournalUseCase
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
        journal = RoomReminderJournal(database.reminderLogDao())
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
    fun recordsOlderThanTheAgeLimitAreEvicted() = runTest {
        journal.record(
            ReminderLogEntry(
                occurredAt = NOW.minus(Duration.ofDays(91)),
                event = ReminderEvent.SHOWN,
                intakeId = "old",
            ),
        )
        journal.record(
            ReminderLogEntry(
                occurredAt = NOW.minus(Duration.ofDays(89)),
                event = ReminderEvent.SHOWN,
                intakeId = "kept",
            ),
        )

        val rotation = requireNotNull(rotate())

        assertEquals(1, rotation.removedByAge)
        assertEquals(listOf("kept"), shownIntakeIds())
    }

    @Test
    fun aRecordExactlyAtTheAgeLimitSurvives() = runTest {
        journal.record(
            ReminderLogEntry(
                occurredAt = NOW.minus(ReminderLogRetention.MAX_AGE),
                event = ReminderEvent.SHOWN,
                intakeId = "at-limit",
            ),
        )

        assertNull("strictly older than 90 days is evicted, exactly 90 is not", rotate())
        assertEquals(listOf("at-limit"), shownIntakeIds())
    }

    /**
     * Потолок объёма — байты, а не число записей (ПМ-30, п. 1): политика с малым
     * потолком проверяет тот же самый запрос, которым живёт продукт.
     */
    @Test
    fun theVolumeCapEvictsTheOldestAndKeepsTheProtectedWindow() = runTest {
        val retention = ReminderLogRetention(maxBytes = 4_000L)
        val protectedIds = (1..5).map { "protected-$it" }
        protectedIds.forEach { id ->
            journal.record(paddedEntry(daysAgo = 1, intakeId = id))
        }
        repeat(40) { index ->
            journal.record(paddedEntry(daysAgo = 8L + index % 20, intakeId = "old-$index"))
        }

        val rotation = requireNotNull(rotate(retention))

        assertTrue("the volume cap must evict something", rotation.removedBySize > 0)
        assertTrue("the journal must fit the cap", journal.storedBytes() <= retention.maxBytes)
        val kept = shownIntakeIds()
        protectedIds.forEach { id ->
            assertTrue("the protected record $id must stay", kept.contains(id))
        }
        assertTrue("only the oldest records may go", kept.none { it.startsWith("old-") })
    }

    /**
     * Неприкосновенное окно важнее потолка: если журнал в последние 7 суток сам больше
     * двух мегабайт, ротация не удаляет ничего (ПМ-30, п. 2).
     */
    @Test
    fun theProtectedWindowIsNeverTrimmedEvenOverTheCap() = runTest {
        val retention = ReminderLogRetention(maxBytes = 200L)
        repeat(10) { index -> journal.record(paddedEntry(daysAgo = 1, intakeId = "protected-$index")) }

        assertNull("there is nothing outside the window to remove", rotate(retention))
        assertEquals(10, shownIntakeIds().size)
        assertTrue("the window outweighs the cap", journal.storedBytes() > retention.maxBytes)
    }

    /** Оценка объёма растёт вместе с журналом — иначе потолок не имел бы меры. */
    @Test
    fun theStoredBytesEstimateGrowsWithTheJournal() = runTest {
        val empty = journal.storedBytes()
        journal.record(paddedEntry(daysAgo = 1, intakeId = "grown"))

        assertTrue(journal.storedBytes() > empty)
    }

    private suspend fun rotate(retention: ReminderLogRetention = ReminderLogRetention.DEFAULT): ReminderLogRotation? =
        RotateReminderJournalUseCase(journal, clock, retention)()

    private fun paddedEntry(daysAgo: Long, intakeId: String): ReminderLogEntry = ReminderLogEntry(
        occurredAt = NOW.minus(Duration.ofDays(daysAgo)),
        event = ReminderEvent.SHOWN,
        intakeId = intakeId,
        reason = "x".repeat(80),
    )

    private suspend fun shownIntakeIds(): List<String> =
        journal.recent(limit = 1_000).filter { it.event == ReminderEvent.SHOWN }.mapNotNull { it.intakeId }

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

    /**
     * Среда измерения пишется один раз на изменение (SKL-85).
     *
     * Идентичная нагрузка подавляется, изменённая пишется: журнал — журнал изменений,
     * а записи среды задают границы окон A/B-прогона.
     */
    @Test
    fun anEnvironmentRecordIsWrittenOncePerChange() = runTest {
        journal.recordEnvironment(environmentEntry(AlarmKind.ALARM_CLOCK))
        journal.recordEnvironment(environmentEntry(AlarmKind.ALARM_CLOCK))
        assertEquals(
            "an unchanged environment is one entry",
            1,
            journal.recent(limit = 10).count { it.event == ReminderEvent.ENVIRONMENT },
        )

        journal.recordEnvironment(environmentEntry(AlarmKind.EXACT_IDLE))
        assertEquals(2, journal.recent(limit = 10).size)
        assertEquals(
            AlarmKind.EXACT_IDLE,
            ReminderEnvironment.parse(requireNotNull(journal.last(ReminderEvent.ENVIRONMENT)?.reason))
                ?.alarmKind,
        )
    }

    /** Две одновременные пересборки не удваивают запись: проверка и вставка — одна транзакция. */
    @Test
    fun twoSimultaneousEnvironmentWritesLeaveOneRow() = runTest {
        val entry = environmentEntry(AlarmKind.ALARM_CLOCK)

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(4) { launch { journal.recordEnvironment(entry) } }
            }
        }

        assertEquals(1, journal.recent(limit = 10).count { it.event == ReminderEvent.ENVIRONMENT })
    }

    private val environment = ReminderEnvironment(
        alarmKind = AlarmKind.ALARM_CLOCK,
        device = DeviceEnvironment(
            manufacturer = "HUAWEI",
            brand = "HUAWEI",
            osRelease = "12",
            buildDisplay = "HUAWEI Pura 80 Ultra",
            batteryUnrestricted = true,
        ),
    )

    private fun environmentEntry(kind: AlarmKind): ReminderLogEntry = ReminderLogEntry(
        occurredAt = NOW,
        event = ReminderEvent.ENVIRONMENT,
        reason = environment.copy(alarmKind = kind).encode(),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-25T11:00:00Z")
    }
}
