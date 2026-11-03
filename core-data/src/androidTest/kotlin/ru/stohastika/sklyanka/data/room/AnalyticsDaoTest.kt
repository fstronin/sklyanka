package ru.stohastika.sklyanka.data.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.room.entity.AnalyticsEventEntity

/**
 * Очередь аналитики: порядок, лимит пачки и вытеснение (SKL-165, АН-4).
 *
 * Порядок — не удобство, а смысл: согласие, показанное до решения, обязано уйти раньше
 * выключения аналитики. Лимит пачки отделяет «сколько накопилось» от «сколько отправляем
 * за один проход», а вытеснение не даёт очереди расти бесконечно.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class AnalyticsDaoTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val analytics get() = db.database.analyticsDao()

    @Test
    fun anEmptyQueueGivesNeitherRecordsNorDuplicates() = runTest {
        assertTrue("a fresh queue must be empty", analytics.pending(LIMIT).isEmpty())
        assertEquals(
            "a fresh queue has no events of any kind",
            0,
            analytics.countByNameAndProperties(CONSENT_SHOWN, FIRST_LAUNCH),
        )
    }

    @Test
    fun pendingSendsTheOldestFirstEvenWithinOneMillisecond() = runTest {
        analytics.insertAll(
            listOf(
                event(name = LATER, occurredAt = SECOND),
                event(name = SAME_MOMENT_FIRST, occurredAt = FIRST),
                event(name = SAME_MOMENT_SECOND, occurredAt = FIRST),
            ),
        )

        val queued = analytics.pending(LIMIT)

        assertEquals(
            "the queue is drained by moment, then by insertion",
            listOf(SAME_MOMENT_FIRST, SAME_MOMENT_SECOND, LATER),
            queued.map { it.name },
        )
    }

    @Test
    fun pendingNeverExceedsTheBatchLimit() = runTest {
        analytics.insertAll((1..5).map { event(name = "event_$it", occurredAt = it.toLong()) })

        val batch = analytics.pending(limit = 3)

        assertEquals("one pass must take no more than the limit", 3, batch.size)
        assertEquals("the oldest three leave first", listOf(1L, 2L, 3L), batch.map { it.occurredAt })
        assertEquals("the rest waits for the next pass", 5, analytics.pending(LIMIT).size)
    }

    @Test
    fun deleteRemovesExactlyTheSentRecords() = runTest {
        analytics.insertAll((1..3).map { event(name = "event_$it", occurredAt = it.toLong()) })
        val sent = analytics.pending(limit = 2).map { it.id }

        val removed = analytics.delete(sent)

        assertEquals("both sent records leave the queue", 2, removed)
        assertEquals("the unsent record stays", listOf(3L), analytics.pending(LIMIT).map { it.occurredAt })
    }

    @Test
    fun pruningByAgeDropsOnlyTheExpiredRecords() = runTest {
        analytics.insertAll(
            listOf(
                event(name = EXPIRED, occurredAt = FIRST),
                event(name = EXPIRED_TOO, occurredAt = SECOND),
                event(name = FRESH, occurredAt = THIRD),
            ),
        )

        val removed = analytics.pruneOlderThan(SECOND)

        assertEquals("only the expired record goes", 1, removed)
        assertEquals("the rest stays in order", listOf(SECOND, THIRD), analytics.pending(LIMIT).map { it.occurredAt })
    }

    @Test
    fun pruningByVolumeKeepsTheNewestRecords() = runTest {
        analytics.insertAll((1..5).map { event(name = "event_$it", occurredAt = it.toLong()) })

        val removed = analytics.pruneToLast(keep = 2)

        assertEquals("the oldest records are evicted first", 3, removed)
        assertEquals(
            "the queue keeps exactly the ceiling",
            listOf(4L, 5L),
            analytics.pending(LIMIT).map { it.occurredAt },
        )
    }

    @Test
    fun clearingLeavesNothingBehind() = runTest {
        analytics.insertAll((1..3).map { event(name = "event_$it", occurredAt = it.toLong()) })

        val removed = analytics.clear()

        assertEquals("clearing wipes everything", 3, removed)
        assertTrue("a declined consent leaves an empty queue", analytics.pending(LIMIT).isEmpty())
    }

    @Test
    fun countingTellsEventsApartByPropertiesNotByThemAlone() = runTest {
        analytics.insertAll(
            listOf(
                event(name = CONSENT_SHOWN, occurredAt = FIRST, properties = FIRST_LAUNCH),
                event(name = CONSENT_SHOWN, occurredAt = SECOND, properties = SETTINGS),
                event(name = "analytics_enabled", occurredAt = THIRD, properties = FIRST_LAUNCH),
            ),
        )

        assertEquals(
            "the same name from another source is another fact (decision R-96)",
            1,
            analytics.countByNameAndProperties(CONSENT_SHOWN, FIRST_LAUNCH),
        )
        assertEquals(
            "the same name from settings is counted on its own",
            1,
            analytics.countByNameAndProperties(CONSENT_SHOWN, SETTINGS),
        )
        assertEquals(
            "an absent pair counts as none",
            0,
            analytics.countByNameAndProperties(CONSENT_SHOWN, UNKNOWN_SOURCE),
        )
    }

    private fun event(name: String, occurredAt: Long, properties: String = "", scope: String = "product") =
        AnalyticsEventEntity(
            name = name,
            properties = properties,
            scope = scope,
            occurredAt = occurredAt,
        )

    private companion object {
        const val LIMIT = 100
        const val FIRST = 1_000L
        const val SECOND = 2_000L
        const val THIRD = 3_000L
        const val CONSENT_SHOWN = "consent_shown"
        const val FIRST_LAUNCH = "source=first_launch"
        const val SETTINGS = "source=settings"
        const val UNKNOWN_SOURCE = "source=nowhere"
        const val SAME_MOMENT_FIRST = "onboarding_started"
        const val SAME_MOMENT_SECOND = "screen_view"
        const val LATER = "medication_action"
        const val EXPIRED = "intake_action"
        const val EXPIRED_TOO = "reminder_snoozed"
        const val FRESH = "report_exported"
    }
}
