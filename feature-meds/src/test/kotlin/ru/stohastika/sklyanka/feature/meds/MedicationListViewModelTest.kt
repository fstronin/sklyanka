package ru.stohastika.sklyanka.feature.meds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.domain.medication.TrailingLine
import ru.stohastika.sklyanka.domain.medication.UndoDeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.feature.meds.list.MedicationListViewModel
import ru.stohastika.sklyanka.feature.meds.list.MedsIntent
import ru.stohastika.sklyanka.feature.meds.list.MedsTab
import ru.stohastika.sklyanka.feature.meds.list.TakePrnAction
import java.time.LocalDate

/**
 * Состояние экрана «Лекарства».
 *
 * Всё, что здесь проверяется, проверяется на JVM: эмулятора в среде задачи нет,
 * а счётчики, состав карточек и доступность кнопки «Принять» — арифметика по потокам,
 * и эмулятор ей не нужен.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = MedsTestData.clock()
    private val activeProfile = FakeActiveProfile()
    private val medications = FakeMedications()
    private val intakes = FakeIntakes()
    private val taken = mutableListOf<String>()
    private val undoActions = UndoableActionStore(clock)

    private fun viewModel() = MedicationListViewModel(
        activeProfile = activeProfile,
        medications = medications,
        intakes = intakes,
        time = clock,
        takePrn = TakePrnAction { medicationId -> taken += medicationId },
        undoDelete = UndoDeleteMedicationUseCase(medications, RecordingPlanner()),
        undoActions = undoActions,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- Счётчики -------------------------------------------------------------------------

    @Test
    fun `the active counter ignores as needed courses but the list shows them`() = runTest(dispatcher) {
        repeat(5) { index -> medications.put(MedsTestData.medication(id = "scheduled-$index")) }
        medications.put(MedsTestData.medication(id = "prn", mode = ScheduleMode.PRN, maxPerDay = 3))
        repeat(12) { index ->
            medications.put(
                MedsTestData.medication(id = "archived-$index", archivedAt = java.time.Instant.EPOCH),
            )
        }

        val model = viewModel()
        advanceUntilIdle()

        assertEquals(5, model.state.value.activeCount)
        assertEquals(12, model.state.value.archiveCount)
        assertEquals(6, model.state.value.items.size)
    }

    @Test
    fun `the list is ordered by the nearest next intake`() = runTest(dispatcher) {
        // Сейчас 25 сентября, 10:00: вечернее время ещё впереди, утреннее переходит на завтра.
        medications.put(
            MedsTestData.medication(id = "evening", times = listOf(MedsTestData.EVENING)),
            MedsTestData.medication(id = "morning", times = listOf(MedsTestData.MORNING)),
            MedsTestData.medication(id = "prn", mode = ScheduleMode.PRN, maxPerDay = 3),
        )

        val model = viewModel()
        advanceUntilIdle()

        assertEquals(
            listOf("evening", "morning", "prn"),
            model.state.value.items.map { it.medicationId },
        )
        // Порядок применяется после счётчика: «Активные» считает только курсы по расписанию.
        assertEquals(2, model.state.value.activeCount)
    }

    @Test
    fun `an archived course leaves the active list and moves the counters`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "a"), MedsTestData.medication(id = "b"))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(2, model.state.value.activeCount)

        medications.archive("b", ru.stohastika.sklyanka.domain.model.ArchiveReason.MANUAL)
        advanceUntilIdle()

        assertEquals(1, model.state.value.activeCount)
        assertEquals(1, model.state.value.archiveCount)
        assertTrue(model.state.value.items.none { it.medicationId == "b" })
    }

    // --- Профили --------------------------------------------------------------------------

    @Test
    fun `switching the profile rebuilds the list`() = runTest(dispatcher) {
        medications.put(
            MedsTestData.medication(id = "mine"),
            MedsTestData.medication(id = "hers", profileId = MedsTestData.OTHER_PROFILE_ID),
        )
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("mine"), model.state.value.items.map { it.medicationId })

        activeProfile.switchTo(MedsTestData.OTHER_PROFILE_ID)
        advanceUntilIdle()

        assertEquals(listOf("hers"), model.state.value.items.map { it.medicationId })
    }

    @Test
    fun `the tablet shape reaches the card state`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "scored", shape = TabletShape.ROUND_SCORED))
        medications.put(MedsTestData.medication(id = "capsule", form = MedicationForm.CAPSULE))

        val model = viewModel()
        advanceUntilIdle()

        val cards = model.state.value.items.associateBy { it.medicationId }
        assertEquals(TabletShape.ROUND_SCORED, cards.getValue("scored").shape)
        assertNull(cards.getValue("capsule").shape)
    }

    // --- Строка расписания ------------------------------------------------------------------

    @Test
    fun `a triggered stock threshold replaces the period with the stock left`() = runTest(dispatcher) {
        medications.put(
            MedsTestData.medication(
                id = "magnesium",
                times = listOf(MedsTestData.MORNING, MedsTestData.EVENING),
                stockLeft = "9",
                thresholdDays = 5,
            ),
        )

        val model = viewModel()
        advanceUntilIdle()

        val line = model.state.value.items.single().trailingLine
        assertTrue(line is TrailingLine.Stock)
    }

    @Test
    fun `an untracked stock keeps the course period`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "thyroid", startDate = LocalDate.of(2026, 3, 12)))

        val model = viewModel()
        advanceUntilIdle()

        assertEquals(
            TrailingLine.Period(LocalDate.of(2026, 3, 12), null),
            model.state.value.items.single().trailingLine,
        )
    }

    // --- Карточка «по потребности» ----------------------------------------------------------

    @Test
    fun `an as needed card carries the limit and the date of the last intake`() = runTest(dispatcher) {
        medications.put(
            MedsTestData.medication(
                id = "ibuprofen",
                form = MedicationForm.CAPSULE,
                mode = ScheduleMode.PRN,
                maxPerDay = 3,
            ),
        )
        intakes.put(MedsTestData.taken("ibuprofen", LocalDate.of(2026, 9, 19).atTime(12, 0)))

        val model = viewModel()
        advanceUntilIdle()

        val card = model.state.value.items.single().asNeeded
        assertNotNull(card)
        assertEquals(3, card?.maxPerDay)
        assertEquals(LocalDate.of(2026, 9, 19), card?.lastIntakeDate)
        assertTrue(card?.takeEnabled == true)
    }

    @Test
    fun `a scheduled card carries no as needed part at all`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "thyroid"))

        val model = viewModel()
        advanceUntilIdle()

        assertNull(model.state.value.items.single().asNeeded)
    }

    @Test
    fun `an exhausted daily limit disables the take button`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "ibuprofen", mode = ScheduleMode.PRN, maxPerDay = 3))
        listOf(8, 12, 16).forEach { hour ->
            intakes.put(MedsTestData.taken("ibuprofen", MedsTestData.TODAY.atTime(hour, 0)))
        }

        val model = viewModel()
        advanceUntilIdle()

        assertFalse(model.state.value.items.single().asNeeded?.takeEnabled ?: true)
    }

    @Test
    fun `intakes of previous days do not count against today's limit`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "ibuprofen", mode = ScheduleMode.PRN, maxPerDay = 3))
        listOf(8, 12, 16).forEach { hour ->
            intakes.put(MedsTestData.taken("ibuprofen", MedsTestData.TODAY.minusDays(1).atTime(hour, 0)))
        }

        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.state.value.items.single().asNeeded?.takeEnabled == true)
    }

    // --- Намерения ---------------------------------------------------------------------------

    @Test
    fun `taking an as needed medication goes through the shared use case`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "ibuprofen", mode = ScheduleMode.PRN, maxPerDay = 3))
        val model = viewModel()
        advanceUntilIdle()

        model.onIntent(MedsIntent.TakeNow("ibuprofen"))
        advanceUntilIdle()

        assertEquals(listOf("ibuprofen"), taken)
    }

    @Test
    fun `selecting a tab changes only the tab`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "thyroid"))
        val model = viewModel()
        advanceUntilIdle()

        model.onIntent(MedsIntent.SelectTab(MedsTab.ARCHIVE))
        advanceUntilIdle()

        assertEquals(MedsTab.ARCHIVE, model.state.value.tab)
        assertEquals(1, model.state.value.items.size)
    }

    @Test
    fun `an empty profile shows the empty state and not a blank screen`() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.state.value.isEmpty)
    }

    @Test
    fun `nothing is shown as empty before the first read completes`() {
        val model = viewModel()

        assertFalse(model.state.value.isEmpty)
        assertTrue(model.state.value.loading)
    }

    // --- Мягкое удаление (SKL-99, ОВ-124) ---------------------------------------------------

    @Test
    fun `a deletion window is shown and the undo returns the medication`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "scheduled"))
        val model = viewModel()
        advanceUntilIdle()

        undoActions.publishDeletion(medicationId = "scheduled", medicationName = "med-scheduled")
        // runCurrent, а не advanceUntilIdle: окно живёт пять секунд, и «до простоя»
        // виртуальное время сожгло бы его вместе с таймером.
        runCurrent()

        assertNotNull(model.state.value.deletion)
        model.onIntent(MedsIntent.UndoDeletion("scheduled"))
        runCurrent()

        assertNull(model.state.value.deletion)
        assertNull(undoActions.live())
    }

    @Test
    fun `leaving the screen closes the deletion window`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "scheduled"))
        val model = viewModel()
        advanceUntilIdle()
        undoActions.publishDeletion(medicationId = "scheduled", medicationName = "med-scheduled")
        // runCurrent, а не advanceUntilIdle: окно живёт пять секунд, и «до простоя»
        // виртуальное время сожгло бы его вместе с таймером.
        runCurrent()
        assertNotNull(model.state.value.deletion)

        model.onScreenLeft()
        runCurrent()

        assertNull(model.state.value.deletion)
        assertNull(undoActions.live())
    }
}
