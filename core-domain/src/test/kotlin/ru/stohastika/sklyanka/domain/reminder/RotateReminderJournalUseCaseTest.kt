package ru.stohastika.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Ротация журнала срабатываний (ПМ-30, НФТ-51).
 *
 * Проверяется правило целиком: возраст, объём, порядок вытеснения, неприкосновенное
 * окно, запись факта и молчание, когда удалять нечего. Хранилище здесь в памяти,
 * потому что проверяется решение домена, а не SQL; SQL проверяет `ReminderLogTest`
 * на настоящей базе.
 */
class RotateReminderJournalUseCaseTest {

    private val now: Instant = Instant.parse("2026-09-25T09:00:00Z")
    private val clock = FixedTimeSource(now, ZoneId.of("Europe/Moscow"))
    private val journal = RecordingReminderJournal()

    @Test
    fun `records older than the age limit are removed`() = runTest {
        journal.record(entry(daysAgo = 91))
        journal.record(entry(daysAgo = 10))

        val rotation = requireNotNull(rotate())

        assertEquals(1, rotation.removedByAge, "only the record beyond 90 days goes")
        assertEquals(0, rotation.removedBySize, "the journal fits in the budget")
        assertEquals(listOf("intake-10"), shownIntakeIds())
    }

    @Test
    fun `a record exactly at the age limit survives`() = runTest {
        journal.record(entry(daysAgo = 90))

        assertNull(rotate(), "strictly older than 90 days is evicted, exactly 90 is not")
        assertEquals(1, journal.entries.size)
    }

    @Test
    fun `the oldest are evicted first when the journal exceeds the budget`() = runTest {
        val retention = ReminderLogRetention(maxBytes = 250L * BYTES_PER_ENTRY)
        repeat(400) { index ->
            journal.record(entry(minutesAgo = MINUTES_IN_DAY * 8 + index.toLong() * 60L, intakeId = "intake-$index"))
        }

        val rotation = requireNotNull(rotate(retention))

        assertEquals(0, rotation.removedByAge, "all seeded records are younger than 90 days")
        assertTrue(rotation.removedBySize > 0, "the budget must evict something")
        assertTrue(journal.storedBytes() <= retention.maxBytes, "the journal must fit the cap")
        val kept = shownIntakeIds()
        assertEquals(
            (0 until 400 - rotation.removedBySize).map { "intake-$it" },
            kept,
            "the evicted records must be the oldest ones: ids grow with age",
        )
    }

    @Test
    fun `the protected window is never evicted even over the budget`() = runTest {
        val retention = ReminderLogRetention(maxBytes = 2L * BYTES_PER_ENTRY)
        val protectedIds = listOf("protected-1", "protected-2", "protected-3")
        protectedIds.forEachIndexed { index, id -> journal.record(entry(daysAgo = index + 1L, intakeId = id)) }
        repeat(5) { index -> journal.record(entry(daysAgo = 8L + index, intakeId = "old-$index")) }

        val rotation = requireNotNull(rotate(retention))

        assertEquals(5, rotation.removedBySize, "everything outside the window goes")
        assertEquals(protectedIds, shownIntakeIds(), "the last seven days stay whole")
        assertTrue(
            journal.storedBytes() > retention.maxBytes,
            "the window outweighs the cap: this is the documented priority of PM-30",
        )
    }

    @Test
    fun `the age cutoff never enters the protected window`() = runTest {
        // Непривычные пределы: возраст меньше окна. Удалять пятисуточную запись нельзя —
        // возрастная граница обязана отступать перед неприкосновенностью.
        val retention = ReminderLogRetention(maxAge = Duration.ofDays(3), protectedWindow = Duration.ofDays(7))
        journal.record(entry(daysAgo = 5))

        assertNull(rotate(retention))
        assertEquals(1, journal.entries.size)
    }

    @Test
    fun `both thresholds apply in one rotation`() = runTest {
        journal.record(entry(daysAgo = 100, intakeId = "ancient"))
        repeat(400) { index ->
            journal.record(entry(minutesAgo = MINUTES_IN_DAY * 8 + index.toLong() * 60L, intakeId = "intake-$index"))
        }

        val rotation = requireNotNull(rotate(ReminderLogRetention(maxBytes = 250L * BYTES_PER_ENTRY)))

        assertEquals(1, rotation.removedByAge)
        assertTrue(rotation.removedBySize > 0)
        assertEquals(rotation.removedByAge + rotation.removedBySize, rotation.removed)
    }

    @Test
    fun `the rotation fact carries counts and nothing else`() = runTest {
        journal.record(entry(daysAgo = 91, intakeId = "ancient"))
        journal.record(entry(daysAgo = 1))

        val rotation = requireNotNull(rotate())
        val fact = requireNotNull(journal.last(ReminderEvent.ROTATED))

        assertEquals(now, fact.occurredAt, "the fact is written at the rotation moment")
        assertEquals(rotation, ReminderLogRotation.parse(fact.reason), "the fact names how much was removed")
        assertNull(fact.intakeId, "the fact carries no intake identifiers (NFR-19)")
        assertNull(fact.medicationId, "the fact carries no medication identifiers (NFR-19)")
        assertNull(fact.profileId, "the fact carries no profile identifiers (NFR-19)")
        assertNull(fact.scheduledAt)
        assertNull(fact.firedAt)
    }

    @Test
    fun `nothing deleted means nothing recorded`() = runTest {
        journal.record(entry(daysAgo = 1))

        assertNull(rotate(), "a journal within its limits is left alone")
        assertEquals(
            listOf(ReminderEvent.SHOWN),
            journal.events(),
            "the journal is a journal of changes, not of housekeeping",
        )
    }

    @Test
    fun `an empty journal is left alone`() = runTest {
        assertNull(rotate())
        assertTrue(journal.entries.isEmpty())
    }

    @Test
    fun `a second rotation silences until something new to remove appears`() = runTest {
        journal.record(entry(daysAgo = 91, intakeId = "ancient"))
        journal.record(entry(daysAgo = 1))

        requireNotNull(rotate())
        assertNull(rotate(), "there is nothing to remove anymore")
        assertEquals(1, journal.of(ReminderEvent.ROTATED).size, "one cleanup is one fact")
    }

    private suspend fun rotate(
        retention: ReminderLogRetention = ReminderLogRetention(maxBytes = 1_000_000),
    ): ReminderLogRotation? = RotateReminderJournalUseCase(journal, clock, retention)()

    /** Записи срабатываний без служебной записи о ротации: проверки касаются их одних. */
    private fun shownIntakeIds(): List<String> =
        journal.entries.filter { it.event == ReminderEvent.SHOWN }.mapNotNull { it.intakeId }

    private fun entry(
        daysAgo: Long = 0,
        minutesAgo: Long = daysAgo * MINUTES_IN_DAY,
        intakeId: String = "intake-$daysAgo",
    ): ReminderLogEntry = ReminderLogEntry(
        occurredAt = now.minus(Duration.ofMinutes(minutesAgo)),
        event = ReminderEvent.SHOWN,
        intakeId = intakeId,
    )

    private companion object {
        const val MINUTES_IN_DAY = 24L * 60L
        const val BYTES_PER_ENTRY = 200L
    }
}
