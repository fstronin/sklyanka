package ru.stohastika.sklyanka.data.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.room.DatabaseTestRule
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEnabled
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScope
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScreen
import ru.stohastika.sklyanka.domain.analytics.ConsentShown
import ru.stohastika.sklyanka.domain.analytics.ConsentShownSource
import ru.stohastika.sklyanka.domain.analytics.OnboardingStep
import ru.stohastika.sklyanka.domain.analytics.OnboardingStepResult
import ru.stohastika.sklyanka.domain.analytics.ScreenView
import ru.stohastika.sklyanka.domain.analytics.StepResult
import java.time.Instant

/**
 * Очередь событий на настоящей Room (SKL-165, АН-4, TDD §3.2).
 *
 * Проверяется то, что нельзя проверить фейком: порядок строк, лимит пачки, удаление
 * отправленного, два вытеснения и дедупликация по имени вместе со свойствами.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class AnalyticsQueueTest {

    @get:Rule
    val db = DatabaseTestRule()

    private fun queue() = RoomAnalyticsQueue(db.database.analyticsDao())

    private val dao get() = db.database.analyticsDao()

    @Test
    fun anEmptyQueueGivesNothingAndForgivesAnEmptyDeletion() = runTest {
        val queue = queue()

        assertTrue("a fresh queue holds no records", queue.pending(LIMIT).isEmpty())
        queue.delete(emptyList())
        queue.clear()
        assertFalse(queue.contains(CONSENT_SHOWN, ConsentShown(FIRST_LAUNCH).properties))
    }

    @Test
    fun theQueueIsDrainedByMomentThenByInsertion() = runTest {
        val queue = queue()
        queue.append(ScreenView(AnalyticsScreen.TODAY).toRecord(at(FIRST)))
        queue.append(AnalyticsEnabled.toRecord(at(SECOND)))
        queue.append(ScreenView(AnalyticsScreen.SETTINGS).toRecord(at(FIRST)))

        val drained = queue.pending(LIMIT).map { it.record.name }
        val expected = listOf(
            AnalyticsEventName.SCREEN_VIEW,
            AnalyticsEventName.SCREEN_VIEW,
            AnalyticsEventName.ANALYTICS_ENABLED,
        )

        assertEquals("the oldest goes first, and the same moment keeps insertion order", expected, drained)
    }

    @Test
    fun aRecordSurvivesTheRoundTripWithItsScope() = runTest {
        val record = ConsentShown(ConsentShownSource.SETTINGS).toRecord(at(FIRST))

        queue().append(record)

        val queued = queue().pending(LIMIT).single()
        assertEquals("name, properties and moment come back unchanged", record, queued.record)
        assertEquals(
            "the consent contour is kept apart from the product one",
            AnalyticsScope.CONSENT,
            queued.record.scope,
        )
    }

    @Test
    fun aBatchNeverExceedsTheLimit() = runTest {
        val queue = queue()
        (1..5).forEach { queue.append(ScreenView(AnalyticsScreen.TODAY).toRecord(at(it.toLong()))) }

        val batch = queue.pending(limit = 2)

        assertEquals("one pass takes no more than the limit", 2, batch.size)
        assertEquals("the oldest two leave first", listOf(at(1), at(2)), batch.map { it.record.occurredAt })
        assertEquals("the rest waits for the next pass", 5, queue.pending(LIMIT).size)
    }

    @Test
    fun deleteRemovesOnlyTheSentRecords() = runTest {
        val queue = queue()
        queue.append(ScreenView(AnalyticsScreen.TODAY).toRecord(at(FIRST)))
        queue.append(ScreenView(AnalyticsScreen.HISTORY).toRecord(at(SECOND)))
        queue.append(ScreenView(AnalyticsScreen.REPORT).toRecord(at(THIRD)))

        queue.delete(queue.pending(limit = 2).map { it.id })

        assertEquals(
            "the unsent record stays in the queue",
            listOf(at(THIRD)),
            queue.pending(LIMIT).map { it.record.occurredAt },
        )
    }

    @Test
    fun pruningByAgeDropsOnlyTheExpiredRecords() = runTest {
        val queue = queue()
        queue.append(ScreenView(AnalyticsScreen.TODAY).toRecord(at(FIRST)))
        queue.append(ScreenView(AnalyticsScreen.HISTORY).toRecord(at(SECOND)))
        queue.append(ScreenView(AnalyticsScreen.REPORT).toRecord(at(THIRD)))

        queue.prune(cutoff = at(SECOND), keep = LIMIT)

        assertEquals(
            "only the record older than the cutoff leaves",
            listOf(at(SECOND), at(THIRD)),
            queue.pending(LIMIT).map { it.record.occurredAt },
        )
    }

    @Test
    fun pruningByVolumeNeverLeavesMoreThanTheCeiling() = runTest {
        val queue = queue()
        (1..5).forEach { queue.append(ScreenView(AnalyticsScreen.TODAY).toRecord(at(it.toLong()))) }

        queue.prune(cutoff = Instant.EPOCH, keep = 2)

        val kept = queue.pending(LIMIT)
        assertEquals("the queue keeps exactly the ceiling", 2, kept.size)
        assertEquals("the oldest records are evicted first", listOf(at(4), at(5)), kept.map { it.record.occurredAt })
    }

    @Test
    fun clearingLeavesNothingBehind() = runTest {
        val queue = queue()
        (1..3).forEach { queue.append(ScreenView(AnalyticsScreen.TODAY).toRecord(at(it.toLong()))) }

        queue.clear()

        assertTrue("a declined consent leaves an empty queue", queue.pending(LIMIT).isEmpty())
    }

    @Test
    fun containsTellsTheSameEventApartByItsProperties() = runTest {
        val queue = queue()
        queue.append(ConsentShown(FIRST_LAUNCH).toRecord(at(FIRST)))
        queue.append(ConsentShown(ConsentShownSource.SETTINGS).toRecord(at(SECOND)))
        queue.append(ScreenView(AnalyticsScreen.TODAY).toRecord(at(THIRD)))

        assertTrue(
            "the first launch prompt is a fact of its own",
            queue.contains(CONSENT_SHOWN, ConsentShown(FIRST_LAUNCH).properties),
        )
        assertTrue(
            "the settings prompt is another fact (decision R-96)",
            queue.contains(CONSENT_SHOWN, ConsentShown(ConsentShownSource.SETTINGS).properties),
        )
        assertFalse(
            "a source that was never shown is not counted",
            queue.contains(CONSENT_SHOWN, ConsentShown(ConsentShownSource.POLICY_UPDATE).properties),
        )
        assertTrue(
            "the same property value under another name is another event",
            queue.contains(SCREEN_VIEW, ScreenView(AnalyticsScreen.TODAY).properties),
        )
        assertFalse(
            "a property value does not stand in for a name",
            queue.contains(CONSENT_SHOWN, ScreenView(AnalyticsScreen.TODAY).properties),
        )
    }

    /**
     * Формат строки свойств — один и канонический: пары `ключ=значение` через `&`, по возрастанию
     * проводного имени ключа. Иначе строковое сравнение в `contains` пропускало бы дубли.
     */
    @Test
    fun propertiesAreStoredCanonicallyRegardlessOfKeyOrder() = runTest {
        val queue = queue()
        val result = StepResult.GRANTED.wire
        val step = OnboardingStep.NOTIFICATIONS.wire
        queue.append(OnboardingStepResult(OnboardingStep.NOTIFICATIONS, StepResult.GRANTED).toRecord(at(FIRST)))

        assertEquals(
            "the stored string is written by the adapter, not by the caller",
            "result=$result&step=$step",
            dao.pending(LIMIT).single().properties,
        )
        assertTrue(
            "any key order in the query finds the same record",
            queue.contains(
                ONBOARDING_STEP_RESULT,
                mapOf(AnalyticsProperty.RESULT to result, AnalyticsProperty.STEP to step),
            ),
        )
        assertTrue(
            "the reversed key order finds it too",
            queue.contains(
                ONBOARDING_STEP_RESULT,
                mapOf(AnalyticsProperty.STEP to step, AnalyticsProperty.RESULT to result),
            ),
        )
    }

    private fun at(millis: Long): Instant = Instant.ofEpochMilli(millis)

    private companion object {
        const val LIMIT = 100
        const val FIRST = 1_000L
        const val SECOND = 2_000L
        const val THIRD = 3_000L

        val CONSENT_SHOWN = AnalyticsEventName.CONSENT_SHOWN
        val ONBOARDING_STEP_RESULT = AnalyticsEventName.ONBOARDING_STEP_RESULT
        val SCREEN_VIEW = AnalyticsEventName.SCREEN_VIEW

        val FIRST_LAUNCH = ConsentShownSource.FIRST_LAUNCH
    }
}
