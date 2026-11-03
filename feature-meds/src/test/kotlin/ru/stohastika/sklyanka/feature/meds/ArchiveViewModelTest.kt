package ru.stohastika.sklyanka.feature.meds

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty
import ru.stohastika.sklyanka.domain.medication.ArchiveCaption
import ru.stohastika.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.UndoDeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveIntent
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveNotice
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveUiState
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveViewModel
import java.time.Instant
import java.time.LocalDate

/**
 * Состояние вкладки «Архив».
 *
 * Проверяется на JVM: группировка, счётчики и возврат — правила по потокам, эмулятор
 * им не нужен и в среде задачи недоступен.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = MedsTestData.clock()
    private val activeProfile = FakeActiveProfile()
    private val medications = FakeMedications()
    private val intakes = FakeIntakes()
    private val log = RecordingLog()
    private val planner = RecordingPlanner(created = 4)
    private val transactor = DirectTransactor()
    private val undoActions = UndoableActionStore(clock)

    /** Аналитика в памяти: запись идёт в планировщике теста, видна после `advanceUntilIdle`. */
    private val analytics = AnalyticsTestKit(CoroutineScope(dispatcher))

    private fun viewModel() = ArchiveViewModel(
        activeProfile = activeProfile,
        medications = medications,
        intakes = intakes,
        restoreMedication = RestoreMedicationUseCase(medications, planner, transactor, clock, log),
        time = clock,
        undoDelete = UndoDeleteMedicationUseCase(medications, planner),
        undoActions = undoActions,
        analytics = analytics.recorder,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- Состав экрана --------------------------------------------------------------------

    @Test
    fun `the reason of archiving decides the group of the card`() = runTest(dispatcher) {
        medications.put(finished("finished-1"), manual("manual-1"))

        val content = requireNotNull(screen().content)

        assertEquals(listOf("finished-1"), content.finished.map { it.medicationId })
        assertEquals(listOf("manual-1"), content.manual.map { it.medicationId })
    }

    @Test
    fun `a finished course is captioned with its outcome and a manual one with its date`() = runTest(dispatcher) {
        medications.put(finished("finished-1"), manual("manual-1"))
        intakes.put(
            MedsTestData.intake("finished-1", COURSE_START.atTime(MedsTestData.MORNING), IntakeStatus.TAKEN),
            MedsTestData.intake("finished-1", COURSE_START.atTime(MedsTestData.EVENING), IntakeStatus.MISSED),
        )

        val content = requireNotNull(screen().content)

        assertTrue(content.finished.single().caption is ArchiveCaption.Outcome)
        assertTrue(content.manual.single().caption is ArchiveCaption.ArchivedOn)
    }

    @Test
    fun `both counters are computed from one value of the flow`() = runTest(dispatcher) {
        repeat(5) { index -> medications.put(MedsTestData.medication(id = "active-$index")) }
        medications.put(MedsTestData.medication(id = "prn", mode = ScheduleMode.PRN, maxPerDay = 3))
        repeat(7) { index -> medications.put(finished("finished-$index")) }
        repeat(5) { index -> medications.put(manual("manual-$index")) }

        val state = screen()

        // «Активные · 5» считает только курсы по расписанию (ЭКР-3); «Архив · 12» — обе причины.
        assertEquals(5, state.activeCount)
        assertEquals(12, state.archiveCount)
    }

    @Test
    fun `an empty group leaves an empty list, not a heading over nothing`() = runTest(dispatcher) {
        medications.put(finished("finished-1"))

        val content = requireNotNull(screen().content)

        assertTrue(content.manual.isEmpty())
        assertEquals(1, content.finished.size)
    }

    @Test
    fun `an empty archive is an empty archive, not an empty list`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "active-1"))

        val state = screen()

        assertNull("an empty archive shows an explanation instead of a list", state.content)
        assertEquals(0, state.archiveCount)
        assertEquals(1, state.activeCount)
    }

    @Test
    fun `the archive of another profile is not shown and not counted`() = runTest(dispatcher) {
        medications.put(finished("mine"))
        medications.put(finished("foreign", profileId = MedsTestData.OTHER_PROFILE_ID))

        val state = screen()

        assertEquals(listOf("mine"), requireNotNull(state.content).finished.map { it.medicationId })
        assertEquals(1, state.archiveCount)
    }

    // --- Возврат --------------------------------------------------------------------------

    @Test
    fun `restoring removes the card and recounts both segments at once`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "active-1"))
        medications.put(finished("finished-1"))
        val model = viewModel()
        advanceUntilIdle()

        model.onIntent(ArchiveIntent.Restore("finished-1"))
        advanceUntilIdle()

        val state = model.state.value
        assertNull(state.content)
        assertEquals(2, state.activeCount)
        assertEquals(0, state.archiveCount)
        assertEquals(listOf("finished-1" to PlanReason.MEDICATION_RESTORED), planner.calls)
    }

    @Test
    fun `restoring an open course does not grow an end date`() = runTest(dispatcher) {
        medications.put(manual("manual-1", endDate = null))
        val model = viewModel()
        advanceUntilIdle()

        model.onIntent(ArchiveIntent.Restore("manual-1"))
        advanceUntilIdle()

        val restored = medications.snapshot().single { it.id == "manual-1" }
        assertNull("an open course comes back open (DR-6)", restored.endDate)
        assertNull(restored.archivedAt)
        assertNull(restored.archiveReason)
    }

    @Test
    fun `restoring a course whose end has passed explains itself and changes nothing`() = runTest(dispatcher) {
        medications.put(finished("finished-1", endDate = MedsTestData.TODAY.minusDays(30)))
        val model = viewModel()
        advanceUntilIdle()

        model.onIntent(ArchiveIntent.Restore("finished-1"))
        advanceUntilIdle()

        assertEquals(ArchiveNotice.PeriodNeedsUpdate, model.state.value.notice)
        assertTrue(medications.snapshot().single().isArchived)
        assertEquals(emptyList<Pair<String, PlanReason>>(), planner.calls)
    }

    @Test
    fun `the history of a restored course stays where it was`() = runTest(dispatcher) {
        medications.put(manual("manual-1"))
        val history = listOf(
            MedsTestData.intake("manual-1", COURSE_START.atTime(MedsTestData.MORNING), IntakeStatus.TAKEN),
            MedsTestData.intake("manual-1", COURSE_START.atTime(MedsTestData.EVENING), IntakeStatus.TAKEN),
        )
        intakes.put(*history.toTypedArray())
        val model = viewModel()
        advanceUntilIdle()

        model.onIntent(ArchiveIntent.Restore("manual-1"))
        advanceUntilIdle()

        assertTrue("no record of an intake may be touched (DR-11)", intakes.snapshot().containsAll(history))
    }

    @Test
    fun `the explanation can be dismissed`() = runTest(dispatcher) {
        medications.put(finished("finished-1", endDate = MedsTestData.TODAY.minusDays(30)))
        val model = viewModel()
        advanceUntilIdle()
        model.onIntent(ArchiveIntent.Restore("finished-1"))
        advanceUntilIdle()

        model.onIntent(ArchiveIntent.NoticeDismissed)
        advanceUntilIdle()

        assertNull(model.state.value.notice)
    }

    private fun TestScope.screen(): ArchiveUiState {
        val model = viewModel()
        advanceUntilIdle()
        return model.state.value
    }

    private fun finished(id: String, profileId: String = MedsTestData.PROFILE_ID, endDate: LocalDate? = COURSE_END) =
        archived(id, profileId, endDate, ArchiveReason.COURSE_FINISHED)

    private fun manual(id: String, endDate: LocalDate? = COURSE_END) =
        archived(id, MedsTestData.PROFILE_ID, endDate, ArchiveReason.MANUAL)

    private fun archived(id: String, profileId: String, endDate: LocalDate?, reason: ArchiveReason) =
        MedsTestData.medication(
            id = id,
            profileId = profileId,
            times = listOf(MedsTestData.MORNING, MedsTestData.EVENING),
            startDate = COURSE_START,
            endDate = endDate,
            archivedAt = ARCHIVED_AT,
            archiveReason = reason,
        )

    private companion object {
        val COURSE_START: LocalDate = LocalDate.of(2026, 8, 15)
        val COURSE_END: LocalDate = LocalDate.of(2026, 9, 30)
        val ARCHIVED_AT: Instant = LocalDate.of(2026, 8, 22)
            .atTime(MedsTestData.MORNING)
            .atZone(MedsTestData.ZONE)
            .toInstant()
    }

    // --- Мягкое удаление (SKL-99, ОВ-124) ---------------------------------------------------

    @Test
    fun `a deletion window is shown in the archive and the undo returns the medication`() = runTest(dispatcher) {
        medications.put(manual("archived"))
        val model = viewModel()
        advanceUntilIdle()

        undoActions.publishDeletion(medicationId = "archived", medicationName = "med-archived")
        // runCurrent, а не advanceUntilIdle: таймер окна сжёг бы его вместе с временем.
        runCurrent()

        assertNotNull(model.state.value.deletion)
        model.onIntent(ArchiveIntent.UndoDeletion("archived"))
        runCurrent()

        assertNull(model.state.value.deletion)
        assertNull(undoActions.live())
    }

    // --- Аналитика (SKL-165) ----------------------------------------------------------------

    @Test
    fun `undoing a deletion in the archive is one medication undo`() = runTest(dispatcher) {
        medications.put(finished("finished-1"))
        val model = viewModel()
        advanceUntilIdle()
        undoActions.publishDeletion(medicationId = "finished-1", medicationName = "med-finished-1")
        runCurrent()

        model.onIntent(ArchiveIntent.UndoDeletion("finished-1"))
        runCurrent()

        val record = analytics.queue.recorded.single()
        assertEquals(AnalyticsEventName.MEDICATION_ACTION, record.name)
        assertEquals(mapOf(AnalyticsProperty.ACTION to "undo"), record.properties)
    }
}
