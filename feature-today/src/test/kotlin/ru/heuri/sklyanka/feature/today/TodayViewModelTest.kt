package ru.heuri.sklyanka.feature.today

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.today.IntakePresentation
import ru.heuri.sklyanka.domain.today.PartOfDay
import ru.heuri.sklyanka.domain.today.TodayLayout
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Состояние экрана «Сегодня» и разбор намерений.
 *
 * Проверяется то, что увидит человек, а не то, что `ViewModel` кому-то позвонила:
 * под ней работают настоящие сценарии отметки поверх хранилища в памяти, и результат
 * приходит новым значением потока, как и в приложении (`TDD.md §3.1`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a profile without medications sees the empty state`() = runTest {
        val world = TodayTestWorld(testScheduler)

        world.viewModel().state.test {
            assertEquals(TodayUiState.Empty(TodayTest.DATE), awaitItem().afterOpening(this))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the day is laid out by time with the current group marked`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)

        world.viewModel().state.test {
            val content = awaitContent()

            assertEquals(
                listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(21, 0)),
                content.layout.groups.map { it.time },
            )
            assertEquals(LocalTime.of(14, 0), content.layout.groups.single { it.isNow }.time)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `marking an intake as taken changes the state and writes off the stock`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()

            viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))

            val content = awaitItem() as TodayUiState.Content
            val item = content.layout.groups.single { it.time == LocalTime.of(14, 0) }.items.single()
            assertEquals(IntakePresentation.TAKEN, item.presentation)
            assertEquals(LocalTime.of(14, 5), item.markedAt)
            assertEquals(BigDecimal("7"), world.medications.stored.getValue("m-magnesium").stockLeft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `skipping an intake leaves the stock alone`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 20)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()

            viewModel.onIntent(TodayIntent.MarkSkipped("i-magnesium"))

            val content = awaitItem() as TodayUiState.Content
            val item = content.layout.groups.single { it.time == LocalTime.of(14, 0) }.items.single()
            assertEquals(IntakePresentation.MISSED, item.presentation)
            assertEquals(BigDecimal("9"), world.medications.stored.getValue("m-magnesium").stockLeft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a repeated mark changes neither the record nor the state`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))
            val marked = awaitItem()

            viewModel.onIntent(TodayIntent.MarkSkipped("i-magnesium"))

            expectNoEvents()
            assertEquals(IntakeStatus.TAKEN, world.intakes.stored.getValue("i-magnesium").status)
            assertEquals(marked, viewModel.state.value)
            assertEquals(BigDecimal("7"), world.medications.stored.getValue("m-magnesium").stockLeft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the group mark takes every intake of the time at once`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()

            viewModel.onIntent(TodayIntent.MarkGroupTaken(listOf("i-a", "i-b", "i-c")))

            val content = awaitItem() as TodayUiState.Content
            val group = content.layout.groups.single { it.time == LocalTime.of(14, 0) }
            assertTrue(group.items.all { it.presentation == IntakePresentation.TAKEN })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `one by one expands the group without marking anything`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            val before = awaitContent()
            assertFalse(before.isGroupExpanded(before.layout.groups.single()))

            viewModel.onIntent(TodayIntent.ExpandGroup(LocalTime.of(14, 0)))

            val content = awaitItem() as TodayUiState.Content
            assertTrue(content.isGroupExpanded(content.layout.groups.single()))
            assertTrue(world.intakes.stored.values.all { it.status == IntakeStatus.PENDING })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `taking an on-demand medication creates an intake but not a plan`() = runTest {
        val world = worldWithDay(startAt = 16, minute = 40)
        world.medications.put(TodayTest.medication("m-prn", mode = ScheduleMode.PRN, maxPerDay = 3))
        val viewModel = world.viewModel()

        viewModel.state.test {
            val before = awaitContent()
            assertEquals(0, before.prn.single().takenToday)

            viewModel.onIntent(TodayIntent.TakePrn("m-prn"))

            val content = awaitItem() as TodayUiState.Content
            assertEquals(1, content.prn.single().takenToday)
            // Приём «по потребности» не входит ни в числитель, ни в знаменатель (ЭКР-1).
            assertEquals(0, content.progress.taken)
            assertEquals(3, content.progress.planned)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an exhausted daily limit closes the take button`() = runTest {
        val world = worldWithDay(startAt = 21, minute = 0)
        world.medications.put(TodayTest.medication("m-prn", mode = ScheduleMode.PRN, maxPerDay = 1))
        world.intakes.put(TodayTest.intake("i-prn", "m-prn", TodayTest.at(8, 10), IntakeStatus.TAKEN))
        val viewModel = world.viewModel()

        viewModel.state.test {
            assertFalse(awaitContent().prn.single().canTake)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a collapsed section opens on a tap and stays open after a mark`() = runTest {
        val world = denseWorld()
        val viewModel = world.viewModel(denseThreshold = 2)

        viewModel.state.test {
            val before = awaitContent()
            val evening = (before.layout as TodayLayout.Dense).sections.single { it.partOfDay == PartOfDay.EVENING }
            assertFalse(before.isExpanded(evening))

            viewModel.onIntent(TodayIntent.ToggleSection(PartOfDay.EVENING))
            val expanded = awaitItem() as TodayUiState.Content
            assertTrue(expanded.isExpanded(evening))

            viewModel.onIntent(TodayIntent.MarkTaken("i-day"))
            val afterMark = awaitItem() as TodayUiState.Content
            // Развёрнутая секция переживает отметку: она в состоянии экрана, а не
            // в локальном состоянии Compose (TDD §5.2).
            assertTrue(afterMark.isExpanded(evening))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigation intents do not touch the state`() = runTest {
        val world = worldWithDay(startAt = 9, minute = 0)
        val viewModel = world.viewModel()

        viewModel.state.test {
            val before = awaitContent()

            viewModel.onIntent(TodayIntent.OpenMedication("m-magnesium"))
            viewModel.onIntent(TodayIntent.OpenStock("m-magnesium"))
            viewModel.onIntent(TodayIntent.AddMedication)
            viewModel.onIntent(TodayIntent.OpenMedicationList)
            viewModel.onIntent(TodayIntent.OpenSettings)

            expectNoEvents()
            assertEquals(before, viewModel.state.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the stock plate shows one warning and disappears without a threshold`() = runTest {
        val world = TodayTestWorld(testScheduler)
        world.medications.put(
            TodayTest.medication(
                id = "m-magnesium",
                times = listOf(LocalTime.of(14, 0)),
                dose = "2",
                stockLeft = "9",
                thresholdDays = 5,
            ),
        )
        world.medications.put(TodayTest.medication("m-plain", stockLeft = "100", thresholdDays = 5))

        world.viewModel().state.test {
            val content = awaitContent()

            assertEquals("m-magnesium", content.stockWarning?.medicationId)
            assertEquals(4, content.stockWarning?.daysLeft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `medications without intakes today are not the first run empty state`() = runTest {
        val world = TodayTestWorld(testScheduler)
        world.medications.put(TodayTest.medication("m-tomorrow"))

        world.viewModel().state.test {
            val content = awaitContent()

            assertTrue(content.nothingToday)
            assertFalse(content.progress.hasPlan)
            assertNull(content.stockWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Обвязка ---------------------------------------------------------------------

    private suspend fun app.cash.turbine.TurbineTestContext<TodayUiState>.awaitContent(): TodayUiState.Content {
        var state = awaitItem()
        // Первый кадр — Opening: поток ещё не выдал ни одного значения.
        while (state is TodayUiState.Opening) state = awaitItem()
        return state as TodayUiState.Content
    }

    private suspend fun TodayUiState.afterOpening(
        turbine: app.cash.turbine.TurbineTestContext<TodayUiState>,
    ): TodayUiState {
        var state = this
        while (state is TodayUiState.Opening) state = turbine.awaitItem()
        return state
    }

    /** Обычный день сценария приёмки: 08:00, 14:00 и 21:00. */
    private fun TestScope.worldWithDay(startAt: Int, minute: Int): TodayTestWorld {
        val world = TodayTestWorld(testScheduler, TodayTest.at(startAt, minute))
        val eutirox = TodayTest.medication("m-eutirox", times = listOf(LocalTime.of(8, 0)))
        val magnesium = TodayTest.medication(
            id = "m-magnesium",
            times = listOf(LocalTime.of(14, 0)),
            dose = "2",
            stockLeft = "9",
        )
        val losartan = TodayTest.medication("m-losartan", times = listOf(LocalTime.of(21, 0)))
        listOf(eutirox, magnesium, losartan).forEach(world.medications::put)
        world.intakes.put(TodayTest.intake("i-eutirox", eutirox.id, TodayTest.at(8, 0)))
        world.intakes.put(TodayTest.intake("i-magnesium", magnesium.id, TodayTest.at(14, 0)))
        world.intakes.put(TodayTest.intake("i-losartan", losartan.id, TodayTest.at(21, 0)))
        return world
    }

    /** Три приёма на одно время — расклад групповой карточки. */
    private fun TestScope.worldWithSimultaneous(startAt: Int, minute: Int): TodayTestWorld {
        val world = TodayTestWorld(testScheduler, TodayTest.at(startAt, minute))
        listOf("a", "b", "c").forEach { suffix ->
            val medication = TodayTest.medication("m-$suffix", times = listOf(LocalTime.of(14, 0)), stockLeft = "20")
            world.medications.put(medication)
            world.intakes.put(TodayTest.intake("i-$suffix", medication.id, TodayTest.at(14, 0)))
        }
        return world
    }

    /** Три приёма в разное время суток: утро позади, день сейчас, вечер впереди. */
    private fun TestScope.denseWorld(): TodayTestWorld {
        val world = TodayTestWorld(testScheduler, TodayTest.at(14, 5))
        val medication = TodayTest.medication("m-dense", times = listOf(LocalTime.of(8, 0)))
        world.medications.put(medication)
        world.intakes.put(TodayTest.intake("i-morning", medication.id, TodayTest.at(8, 0), IntakeStatus.TAKEN))
        world.intakes.put(TodayTest.intake("i-day", medication.id, TodayTest.at(14, 0)))
        world.intakes.put(TodayTest.intake("i-evening", medication.id, TodayTest.at(21, 0)))
        return world
    }
}
