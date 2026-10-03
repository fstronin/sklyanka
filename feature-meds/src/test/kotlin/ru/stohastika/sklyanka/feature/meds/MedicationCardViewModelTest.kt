package ru.stohastika.sklyanka.feature.meds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.CourseProgress
import ru.stohastika.sklyanka.domain.medication.DeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ToggleWatchMirrorUseCase
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.stock.RefillStockUseCase
import ru.stohastika.sklyanka.domain.stock.StockPanel
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import ru.stohastika.sklyanka.feature.meds.card.MedCardIntent
import ru.stohastika.sklyanka.feature.meds.card.MedCardNotice
import ru.stohastika.sklyanka.feature.meds.card.MedCardOperations
import ru.stohastika.sklyanka.feature.meds.card.MedCardUiState
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardViewModel
import ru.stohastika.sklyanka.feature.meds.card.ScheduleBlockUi
import java.time.LocalDate

/**
 * Состояние карточки лекарства.
 *
 * Проверяется на JVM: правила карточки — арифметика по потокам, эмулятор ей не нужен,
 * а в среде задачи его и нет.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationCardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = MedsTestData.clock()
    private val activeProfile = FakeActiveProfile()
    private val medications = FakeMedications()
    private val intakes = FakeIntakes()
    private val log = RecordingLog()
    private val planner = RecordingPlanner(created = 4, deleted = 2)
    private val undoActions = UndoableActionStore(clock)
    private val transactor = DirectTransactor()

    private fun viewModel() = MedicationCardViewModel(
        activeProfile = activeProfile,
        medications = medications,
        intakes = intakes,
        operations = MedCardOperations(
            archive = ArchiveMedicationUseCase(medications, planner),
            restore = RestoreMedicationUseCase(medications, planner, transactor, clock, log),
            watchMirror = ToggleWatchMirrorUseCase(medications, log),
            refillStock = RefillStockUseCase(medications, log),
            delete = DeleteMedicationUseCase(medications, intakes, planner),
        ),
        time = clock,
        undoActions = undoActions,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- Состав карточки ------------------------------------------------------------------

    @Test
    fun `the card carries the schedule, the course and the stock of its medication`() = runTest(dispatcher) {
        medications.put(scheduled())

        val state = requireNotNull(open())

        val schedule = state.schedule as ScheduleBlockUi.Scheduled
        assertEquals(2, schedule.timesPerDay)
        assertEquals(listOf(MedsTestData.MORNING, MedsTestData.EVENING), schedule.times)
        assertEquals(CourseProgress.Bounded::class, requireNotNull(state.course)::class)
        assertTrue(state.stock is StockPanel.Visible)
        assertFalse(state.isArchived)
    }

    @Test
    fun `an open course has no bar and no days left`() = runTest(dispatcher) {
        medications.put(scheduled(endDate = null))

        val state = requireNotNull(open())

        assertEquals(CourseProgress.Open(START_DATE), state.course)
    }

    @Test
    fun `a medication without stock tracking hides the block`() = runTest(dispatcher) {
        medications.put(scheduled(stockLeft = null))

        assertEquals(StockPanel.Hidden, requireNotNull(open()).stock)
    }

    @Test
    fun `a medication taken as needed shows a limit, no course and no watch row`() = runTest(dispatcher) {
        medications.put(
            MedsTestData.medication(id = MEDICATION_ID, mode = ScheduleMode.PRN, maxPerDay = 3, stockLeft = "9"),
        )

        val state = requireNotNull(open())

        assertEquals(ScheduleBlockUi.AsNeeded(maxPerDay = 3), state.schedule)
        assertNull("an as needed medication has no course (DR-8)", state.course)
        assertNull("an as needed intake never goes to the watch (WATCH-17)", state.watchMirror)
    }

    @Test
    fun `the week counts finished days only`() = runTest(dispatcher) {
        medications.put(scheduled())
        (1..6).forEach { back ->
            val date = MedsTestData.TODAY.minusDays(back.toLong())
            intakes.put(
                MedsTestData.intake(MEDICATION_ID, date.atTime(MedsTestData.MORNING), IntakeStatus.TAKEN),
                MedsTestData.intake(MEDICATION_ID, date.atTime(MedsTestData.EVENING), IntakeStatus.TAKEN),
            )
        }
        intakes.put(
            MedsTestData.intake(MEDICATION_ID, MedsTestData.TODAY.atTime(MedsTestData.MORNING), IntakeStatus.TAKEN),
            MedsTestData.intake(MEDICATION_ID, MedsTestData.TODAY.atTime(MedsTestData.EVENING), IntakeStatus.PENDING),
        )

        val week = requireNotNull(open()).week

        // Ровно счётчик макета «12 из 14 приёмов» (ЭКР-6).
        assertEquals(12, week.taken)
        assertEquals(14, week.planned)
    }

    @Test
    fun `a medication of another profile does not open`() = runTest(dispatcher) {
        medications.put(scheduled(profileId = MedsTestData.OTHER_PROFILE_ID))

        assertNull("the card of a foreign profile must not open (DR-10)", open())
    }

    // --- Часы -----------------------------------------------------------------------------

    @Test
    fun `the watch switch reflects what is written`() = runTest(dispatcher) {
        medications.put(scheduled(mirrorToWatch = false))
        val model = viewModel()
        advanceUntilIdle()
        model.open(MEDICATION_ID)
        advanceUntilIdle()

        assertEquals(false, model.state.value?.watchMirror?.checked)

        model.onIntent(MedCardIntent.WatchMirrorToggled(enabled = true))
        advanceUntilIdle()

        assertEquals(true, model.state.value?.watchMirror?.checked)
        assertEquals(true, medications.snapshot().single().mirrorToWatch)
    }

    @Test
    fun `the watch switch of an archived course is unavailable`() = runTest(dispatcher) {
        medications.put(scheduled(archivedAt = clock.now()))

        val state = requireNotNull(open())

        assertEquals(false, state.watchMirror?.enabled)
        assertTrue(state.isArchived)
    }

    // --- Архивация ------------------------------------------------------------------------

    @Test
    fun `the archive action removes the course with the manual reason and closes the card`() = runTest(dispatcher) {
        medications.put(scheduled())
        val model = viewModel()
        advanceUntilIdle()
        model.open(MEDICATION_ID)
        advanceUntilIdle()

        model.onIntent(MedCardIntent.Archive)
        advanceUntilIdle()

        val stored = medications.snapshot().single()
        assertNotNull(stored.archivedAt)
        assertEquals(ArchiveReason.MANUAL, stored.archiveReason)
        assertEquals(listOf(MEDICATION_ID to PlanReason.MEDICATION_ARCHIVED), planner.calls)
        assertEquals(true, model.state.value?.closed)
    }

    @Test
    fun `the confirmed offer archives the finished course with its own reason`() = runTest(dispatcher) {
        medications.put(scheduled(endDate = MedsTestData.TODAY.minusDays(1)))
        val model = viewModel()
        advanceUntilIdle()
        model.open(MEDICATION_ID)
        advanceUntilIdle()

        assertEquals(true, model.state.value?.courseFinishedOffer)

        model.onIntent(MedCardIntent.ArchiveFinishedCourse)
        advanceUntilIdle()

        assertEquals(ArchiveReason.COURSE_FINISHED, medications.snapshot().single().archiveReason)
    }

    @Test
    fun `a running course is not offered for the archive`() = runTest(dispatcher) {
        medications.put(scheduled())

        assertEquals(false, requireNotNull(open()).courseFinishedOffer)
    }

    @Test
    fun `the archived card offers to restore the course`() = runTest(dispatcher) {
        medications.put(scheduled(archivedAt = clock.now()))
        val model = viewModel()
        advanceUntilIdle()
        model.open(MEDICATION_ID)
        advanceUntilIdle()

        model.onIntent(MedCardIntent.Restore)
        advanceUntilIdle()

        val stored = medications.snapshot().single()
        assertNull(stored.archivedAt)
        assertNull(stored.archiveReason)
        assertEquals(false, model.state.value?.isArchived)
    }

    @Test
    fun `restoring a course whose end has passed explains itself instead of doing nothing`() = runTest(dispatcher) {
        medications.put(scheduled(endDate = MedsTestData.TODAY.minusDays(5), archivedAt = clock.now()))
        val model = viewModel()
        advanceUntilIdle()
        model.open(MEDICATION_ID)
        advanceUntilIdle()

        model.onIntent(MedCardIntent.Restore)
        advanceUntilIdle()

        assertEquals(MedCardNotice.PeriodNeedsUpdate, model.state.value?.notice)
        assertTrue("the course stays archived until its period is fixed", medications.snapshot().single().isArchived)
    }

    // --- Остаток --------------------------------------------------------------------------

    @Test
    fun `refilling replaces the value of the stock`() = runTest(dispatcher) {
        medications.put(scheduled())
        val model = viewModel()
        advanceUntilIdle()
        model.open(MEDICATION_ID)
        advanceUntilIdle()

        model.onIntent(MedCardIntent.RefillRequested)
        model.onIntent(MedCardIntent.RefillValueChanged("60"))
        model.onIntent(MedCardIntent.RefillConfirmed)
        advanceUntilIdle()

        assertEquals("60", medications.snapshot().single().stockLeft?.toPlainString())
        assertNull("the input closes once the value is written", model.state.value?.refill)
    }

    @Test
    fun `a value that is not a number keeps the input open`() = runTest(dispatcher) {
        medications.put(scheduled())
        val model = viewModel()
        advanceUntilIdle()
        model.open(MEDICATION_ID)
        advanceUntilIdle()

        model.onIntent(MedCardIntent.RefillRequested)
        model.onIntent(MedCardIntent.RefillValueChanged("-5"))
        model.onIntent(MedCardIntent.RefillConfirmed)
        advanceUntilIdle()

        assertEquals(true, model.state.value?.refill?.invalid)
        assertEquals("9", medications.snapshot().single().stockLeft?.toPlainString())
    }

    @Test
    fun `refilling replaces the stock and leaves the package size alone`() = runTest(dispatcher) {
        medications.put(scheduled(packageSize = "60"))
        val model = viewModel()
        advanceUntilIdle()
        model.open(MEDICATION_ID)
        advanceUntilIdle()

        model.onIntent(MedCardIntent.RefillRequested)
        model.onIntent(MedCardIntent.RefillValueChanged("6"))
        model.onIntent(MedCardIntent.RefillConfirmed)
        advanceUntilIdle()

        // Размер упаковки — свойство упаковки, а не остатка (ОВ-13, ОВ-45):
        // пополнение его не трогает, а строка «N из M» пересчитывается от нового остатка.
        assertEquals("6", medications.snapshot().single().stockLeft?.toPlainString())
        assertEquals("60", medications.snapshot().single().packageSize?.toPlainString())
    }

    /** Открыть карточку и дать потокам дойти до первого значения. */
    private fun TestScope.open(): MedCardUiState? {
        val model = viewModel()
        advanceUntilIdle()
        model.open(MEDICATION_ID)
        advanceUntilIdle()
        return model.state.value
    }

    @Suppress("LongParameterList")
    private fun scheduled(
        profileId: String = MedsTestData.PROFILE_ID,
        endDate: LocalDate? = END_DATE,
        stockLeft: String? = "9",
        packageSize: String? = null,
        archivedAt: java.time.Instant? = null,
        mirrorToWatch: Boolean = true,
    ) = MedsTestData.medication(
        id = MEDICATION_ID,
        profileId = profileId,
        times = listOf(MedsTestData.MORNING, MedsTestData.EVENING),
        stockLeft = stockLeft,
        packageSize = packageSize,
        thresholdDays = 5,
        startDate = START_DATE,
        endDate = endDate,
        archivedAt = archivedAt,
        mirrorToWatch = mirrorToWatch,
    )

    // --- Мягкое удаление (SKL-99, ОВ-124) ---------------------------------------------------

    @Test
    fun `the delete action disappears once an intake is taken`() = runTest(dispatcher) {
        medications.put(scheduled())
        intakes.put(MedsTestData.taken(MEDICATION_ID, MedsTestData.TODAY.atTime(MedsTestData.MORNING)))

        assertEquals(false, requireNotNull(open()).canDelete)
    }

    @Test
    fun `an empty history keeps the delete action available`() = runTest(dispatcher) {
        medications.put(scheduled())

        assertEquals(true, requireNotNull(open()).canDelete)
    }

    @Test
    fun `confirming the deletion closes the card and opens the undo window`() = runTest(dispatcher) {
        medications.put(scheduled())
        val model = viewModel()
        advanceUntilIdle()
        model.open(MEDICATION_ID)
        advanceUntilIdle()

        model.onIntent(MedCardIntent.DeleteRequested)
        advanceUntilIdle()
        assertEquals(true, model.state.value?.deleteConfirmation)

        model.onIntent(MedCardIntent.DeleteConfirmed)
        advanceUntilIdle()

        assertTrue(medications.snapshot().single().isDeleted)
        assertEquals(listOf(MEDICATION_ID to PlanReason.MEDICATION_DELETED), planner.calls)
        assertTrue("the undo window must be published", undoActions.live() is UndoableDeletion)
        // Запись уходит из выборок, и состояние карточки может стать `null` раньше, чем
        // маршрут прочитает `closed`; оба исхода значат одно — открытой карточки нет
        // (закрытие маршрутом проверяет устройство, `MedicationDeletionDeviceTest`).
        val state = model.state.value
        assertTrue("the deleted card must not stay open: $state", state == null || state.closed)
        assertTrue("the sheet must not stay open: $state", state?.deleteConfirmation != true)
    }

    @Test
    fun `dismissing the sheet changes nothing`() = runTest(dispatcher) {
        medications.put(scheduled())
        val model = viewModel()
        advanceUntilIdle()
        model.open(MEDICATION_ID)
        advanceUntilIdle()

        model.onIntent(MedCardIntent.DeleteRequested)
        model.onIntent(MedCardIntent.DeleteDismissed)
        advanceUntilIdle()

        assertEquals(false, model.state.value?.deleteConfirmation)
        assertFalse(medications.snapshot().single().isDeleted)
    }

    private companion object {
        const val MEDICATION_ID = "medication-1"
        val START_DATE: LocalDate = LocalDate.of(2026, 9, 12)
        val END_DATE: LocalDate = LocalDate.of(2026, 10, 12)
    }
}
