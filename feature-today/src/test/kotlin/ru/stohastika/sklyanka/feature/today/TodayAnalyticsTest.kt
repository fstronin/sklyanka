package ru.stohastika.sklyanka.feature.today

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.time.LocalTime

/**
 * События действий экрана «Сегодня» (SKL-165, ПМ-26).
 *
 * Проверяется запись, а не вызов: под рекордером настоящие подмены портов, поэтому
 * видно и состав события, и то, что без согласия в очередь не попадает ничего.
 * Ни названия лекарства, ни дозы, ни идентификатора приёма в записи нет — свойства
 * события состоят только из значений каталога (АН-3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayAnalyticsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a mark on today is one intake action without any details`() = runTest {
        val world = worldWithDay()
        val viewModel = world.viewModel()

        viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))
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
    fun `a skip is a skipped intake action`() = runTest {
        val world = worldWithDay()
        val viewModel = world.viewModel()

        viewModel.onIntent(TodayIntent.MarkSkipped("i-magnesium"))
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
    fun `an undo after a mark is a second event with the undo action`() = runTest {
        val world = worldWithDay()
        val viewModel = world.viewModel()

        viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))
        advanceUntilIdle()
        viewModel.onIntent(TodayIntent.UndoMark(listOf("i-magnesium")))
        advanceUntilIdle()

        assertEquals(
            listOf(AnalyticsEventName.INTAKE_ACTION, AnalyticsEventName.INTAKE_ACTION),
            world.analytics.queue.recorded.map { it.name },
        )
        assertEquals(
            mapOf(
                AnalyticsProperty.ACTION to "undo",
                AnalyticsProperty.SOURCE to "phone",
            ),
            world.analytics.queue.recorded.last().properties,
        )
        assertEquals(IntakeStatus.PENDING, world.intakes.stored.getValue("i-magnesium").status)
    }

    @Test
    fun `a group mark is one action for the whole group`() = runTest {
        val world = worldWithSimultaneous()
        val viewModel = world.viewModel()

        viewModel.onIntent(TodayIntent.MarkGroupTaken(listOf("i-a", "i-b", "i-c")))
        advanceUntilIdle()

        val record = world.analytics.queue.recorded.single()
        assertEquals("taken", record.properties[AnalyticsProperty.ACTION])
        assertEquals(2, record.properties.size)
    }

    @Test
    fun `taking an on demand medication is a taken intake action`() = runTest {
        val world = worldWithDay()
        world.medications.put(TodayTest.medication("m-prn", mode = ScheduleMode.PRN, maxPerDay = 3))
        val viewModel = world.viewModel()

        viewModel.onIntent(TodayIntent.TakePrn("m-prn"))
        advanceUntilIdle()

        assertEquals(
            mapOf(
                AnalyticsProperty.ACTION to "taken",
                AnalyticsProperty.SOURCE to "phone",
            ),
            world.analytics.queue.recorded.single().properties,
        )
    }

    @Test
    fun `undoing a deletion is a medication action`() = runTest {
        val world = worldWithDay()
        val viewModel = world.viewModel()

        viewModel.onIntent(TodayIntent.UndoDeletion("m-magnesium"))
        advanceUntilIdle()

        val record = world.analytics.queue.recorded.single()
        assertEquals(AnalyticsEventName.MEDICATION_ACTION, record.name)
        assertEquals(mapOf(AnalyticsProperty.ACTION to "undo"), record.properties)
    }

    @Test
    fun `a declined consent leaves the queue empty`() = runTest {
        val world = worldWithDay()
        world.analytics.store.decide(AnalyticsConsentState.DECLINED)
        val viewModel = world.viewModel()

        viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))
        viewModel.onIntent(TodayIntent.UndoMark(listOf("i-magnesium")))
        advanceUntilIdle()

        assertTrue(world.analytics.queue.recorded.isEmpty())
    }

    @Test
    fun `an unanswered consent leaves the queue empty`() = runTest {
        val world = worldWithDay()
        world.analytics.store.decide(AnalyticsConsentState.NOT_ASKED)
        val viewModel = world.viewModel()

        viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))
        advanceUntilIdle()

        assertTrue(world.analytics.queue.recorded.isEmpty())
    }

    /** День сценария: утренний, дневной и вечерний приём, день уже на середине. */
    private fun TestScope.worldWithDay(): TodayTestWorld {
        val world = TodayTestWorld(testScheduler, TodayTest.at(14, 5))
        val magnesium = TodayTest.medication(
            id = "m-magnesium",
            times = listOf(LocalTime.of(14, 0)),
            dose = "2",
            stockLeft = "9",
        )
        world.medications.put(TodayTest.medication("m-eutirox", times = listOf(LocalTime.of(8, 0))))
        world.medications.put(magnesium)
        world.intakes.put(TodayTest.intake("i-eutirox", "m-eutirox", TodayTest.at(8, 0)))
        world.intakes.put(TodayTest.intake("i-magnesium", magnesium.id, TodayTest.at(14, 0)))
        return world
    }

    /** Три приёма на одно время: групповая отметка считается одним действием. */
    private fun TestScope.worldWithSimultaneous(): TodayTestWorld {
        val world = TodayTestWorld(testScheduler, TodayTest.at(14, 5))
        listOf("a", "b", "c").forEach { suffix ->
            val medication = TodayTest.medication("m-$suffix", times = listOf(LocalTime.of(14, 0)), stockLeft = "20")
            world.medications.put(medication)
            world.intakes.put(TodayTest.intake("i-$suffix", medication.id, TodayTest.at(14, 0)))
        }
        return world
    }
}
