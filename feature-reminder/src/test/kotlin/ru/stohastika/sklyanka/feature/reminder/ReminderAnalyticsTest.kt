package ru.stohastika.sklyanka.feature.reminder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty

/**
 * События карточки напоминания (SKL-165, ПМ-26).
 *
 * Проверяется запись, а не вызов: под рекордером подмены портов, поэтому видно и состав
 * события, и то, что при отказе от аналитики в очередь не попадает ничего. Имени
 * лекарства и его дозы в событии нет — свойства состоят только из значений каталога.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderAnalyticsTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a taken row is one intake action with the phone source`() = runTest {
        val world = ReminderTestWorld()
        val viewModel = world.viewModel()
        viewModel.open(world.profileId, world.scheduledAt)

        viewModel.onTaken("i-1")
        advanceUntilIdle()

        val record = world.analytics.queue.recorded.single()
        assertEquals(AnalyticsEventName.INTAKE_ACTION, record.name)
        assertEquals(
            mapOf(
                AnalyticsProperty.ACTION to "taken",
                AnalyticsProperty.SOURCE to "phone",
            ),
            record.properties,
        )
    }

    @Test
    fun `a skipped row is one skipped intake action`() = runTest {
        val world = ReminderTestWorld()
        val viewModel = world.viewModel()
        viewModel.open(world.profileId, world.scheduledAt)

        viewModel.onSkipped("i-1")
        advanceUntilIdle()

        assertEquals(
            mapOf(
                AnalyticsProperty.ACTION to "skipped",
                AnalyticsProperty.SOURCE to "phone",
            ),
            world.analytics.queue.recorded.single().properties,
        )
    }

    @Test
    fun `marking the whole card is one action, not one per intake`() = runTest {
        val world = ReminderTestWorld(withSecondRow = true)
        val viewModel = world.viewModel()
        viewModel.open(world.profileId, world.scheduledAt)

        viewModel.onTakenAll()
        advanceUntilIdle()

        val record = world.analytics.queue.recorded.single()
        assertEquals("taken", record.properties[AnalyticsProperty.ACTION])
        assertEquals(2, record.properties.size)
    }

    @Test
    fun `a snooze is a reminder event and not an intake one`() = runTest {
        val world = ReminderTestWorld(withSecondRow = true)
        val viewModel = world.viewModel()
        viewModel.open(world.profileId, world.scheduledAt)

        viewModel.onSnooze()
        advanceUntilIdle()

        val record = world.analytics.queue.recorded.single()
        assertEquals(AnalyticsEventName.REMINDER_SNOOZED, record.name)
        assertEquals(mapOf(AnalyticsProperty.SOURCE to "phone"), record.properties)
    }

    @Test
    fun `a declined consent leaves the queue empty`() = runTest {
        val world = ReminderTestWorld()
        world.analytics.store.decide(AnalyticsConsentState.DECLINED)
        val viewModel = world.viewModel()
        viewModel.open(world.profileId, world.scheduledAt)

        viewModel.onTaken("i-1")
        viewModel.onSnooze()
        advanceUntilIdle()

        assertTrue(world.analytics.queue.recorded.isEmpty())
    }
}
