package ru.stohastika.sklyanka.feature.meds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.DraftField
import ru.stohastika.sklyanka.domain.medication.DraftViolation
import ru.stohastika.sklyanka.domain.medication.SaveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.schedule.IntakeGenerationPolicy
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.stohastika.sklyanka.domain.schedule.PlanScope
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Состояние формы лекарства: применимость блоков, отказы, сохранение.
 *
 * Прогон на JVM: форма — это черновик и правила над ним, а не разметка. То, что
 * действительно требует экрана (порядок блоков, подписи для чтения с экрана), проверяется
 * Compose-тестами, которые в этой среде только компилируются.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationFormViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = MedsTestData.clock()
    private val activeProfile = FakeActiveProfile()
    private val medications = FakeMedications()
    private val intakes = FakeIntakes()
    private val profiles = FakeProfiles()
    private val log = RecordingLog()

    private val model = MedicationFormViewModel(
        activeProfile = activeProfile,
        medications = medications,
        saveMedication = SaveMedicationUseCase(
            validate = ValidateMedicationDraft(),
            medications = medications,
            planner = MedicationPlanner { medicationId, reason ->
                PlanIntakesUseCase(
                    profiles = profiles,
                    medications = medications,
                    intakes = intakes,
                    time = clock,
                    log = log,
                    policy = IntakeGenerationPolicy(windowDepthDays = WINDOW_DEPTH_DAYS),
                )(PlanScope.Medication(medicationId), reason)
            },
            transactor = DirectTransactor(),
            log = log,
            newId = { NEW_ID },
        ),
        time = clock,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        profiles.put(
            Profile(id = MedsTestData.PROFILE_ID, name = "owner", color = 0, isSelf = true, mirrorReminders = false),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- Открытие -------------------------------------------------------------------------

    @Test
    fun `a new form starts empty, in the active profile and from today`() = runTest(dispatcher) {
        open()

        val state = model.state.value!!
        assertEquals(FormMode.Create, state.mode)
        assertEquals(MedsTestData.PROFILE_ID, state.draft.profileId)
        assertEquals(MedsTestData.TODAY, state.draft.startDate)
        assertFalse(state.saveEnabled)
    }

    @Test
    fun `editing fills the form from the stored course and titles the header with its name`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "magnesium", name = "med-a"))

        model.open("magnesium")
        advanceUntilIdle()

        val state = model.state.value!!
        assertEquals(FormMode.Edit("med-a"), state.mode)
        assertEquals("med-a", state.draft.name)
        assertEquals("magnesium", state.draft.id)
        assertEquals("2", state.inputs.doseAmount)
        // Профиль неизменяем (ИНВ-3): черновик правки несёт профиль курса и не даёт его сменить.
        assertEquals(MedsTestData.PROFILE_ID, state.draft.profileId)
    }

    // --- Применимость блоков ---------------------------------------------------------------

    @Test
    fun `choosing a spray hides the tablet shape block and clears the chosen shape`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.ShapeChanged(TabletShape.OVAL))
        assertEquals(TabletShape.OVAL, model.state.value!!.draft.shape)

        model.onIntent(MedFormIntent.ReleaseFormChanged(MedicationForm.SPRAY))

        assertFalse(model.state.value!!.applicability.shapeVisible)
        assertNull(model.state.value!!.draft.shape)
    }

    @Test
    fun `the dose unit follows the release form`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.ReleaseFormChanged(MedicationForm.SYRUP))

        assertEquals(DoseUnit.MILLILITRE, model.state.value!!.applicability.doseUnit)
    }

    @Test
    fun `as needed replaces the schedule blocks with the daily limit`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.TimeAdded)

        model.onIntent(MedFormIntent.ModeChanged(ScheduleMode.PRN))

        val state = model.state.value!!
        assertTrue(state.applicability.maxPerDayVisible)
        assertFalse(state.applicability.timesVisible)
        assertEquals(emptyList<LocalTime>(), state.draft.times)
    }

    @Test
    fun `as needed hides the food relation block but keeps the choice`() = runTest(dispatcher) {
        // ОВ-83: блок скрыт; выбранное значение остаётся в черновике (SKL-49).
        open()
        model.onIntent(MedFormIntent.FoodRelationChanged(FoodRelation.AFTER_MEAL))

        model.onIntent(MedFormIntent.ModeChanged(ScheduleMode.PRN))

        val state = model.state.value!!
        assertFalse(state.applicability.foodRelationVisible)
        assertEquals(FoodRelation.AFTER_MEAL, state.draft.foodRelation)
    }

    @Test
    fun `switching back to a schedule brings the food relation choice back`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.FoodRelationChanged(FoodRelation.AFTER_MEAL))
        model.onIntent(MedFormIntent.ModeChanged(ScheduleMode.PRN))

        model.onIntent(MedFormIntent.ModeChanged(ScheduleMode.SCHEDULED))

        val state = model.state.value!!
        assertTrue(state.applicability.foodRelationVisible)
        assertEquals(FoodRelation.AFTER_MEAL, state.draft.foodRelation)
    }

    // --- Счётчик приёмов и список времён ----------------------------------------------------

    @Test
    fun `the counter and the list of times move together`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.TimeAdded)
        model.onIntent(MedFormIntent.TimeAdded)
        assertEquals(2, model.state.value!!.draft.intakesPerDay)

        model.onIntent(MedFormIntent.TimeAdded)
        assertEquals(3, model.state.value!!.draft.times.size)

        model.onIntent(MedFormIntent.TimeRemoved(1))
        assertEquals(2, model.state.value!!.draft.intakesPerDay)

        model.onIntent(MedFormIntent.TimeRemovedFromEnd)
        assertEquals(1, model.state.value!!.draft.intakesPerDay)
    }

    @Test
    fun `the first added time is eight in the morning`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.TimeAdded)

        assertEquals(listOf(LocalTime.of(8, 0)), model.state.value!!.draft.times)
    }

    @Test
    fun `a time row can be changed in place`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.TimeAdded)

        model.onIntent(MedFormIntent.TimeChanged(0, LocalTime.of(21, 30)))

        assertEquals(listOf(LocalTime.of(21, 30)), model.state.value!!.draft.times)
    }

    @Test
    fun `the daily limit counter never goes below zero`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.ModeChanged(ScheduleMode.PRN))

        model.onIntent(MedFormIntent.MaxPerDayDecreased)

        assertEquals(0, model.state.value!!.draft.maxPerDay)
    }

    @Test
    fun `an untouched daily limit stays empty and is not zero`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.ModeChanged(ScheduleMode.PRN))

        assertNull(model.state.value!!.draft.maxPerDay)
    }

    // --- Переключатели ----------------------------------------------------------------------

    @Test
    fun `switching off the open ended course offers the start date as the end one`() = runTest(dispatcher) {
        open()
        assertNull(model.state.value!!.draft.endDate)

        model.onIntent(MedFormIntent.IndefiniteToggled(false))

        assertEquals(MedsTestData.TODAY, model.state.value!!.draft.endDate)
        assertTrue(model.state.value!!.applicability.endDateEnabled)
    }

    @Test
    fun `switching the purchase reminder off clears the threshold`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.StockReminderToggled(true))
        assertEquals(5, model.state.value!!.draft.stockThresholdDays)

        model.onIntent(MedFormIntent.StockReminderToggled(false))

        assertNull(model.state.value!!.draft.stockThresholdDays)
        assertFalse(model.state.value!!.applicability.stockThresholdEnabled)
    }

    // --- Числовые поля ------------------------------------------------------------------------

    @Test
    fun `a comma is a decimal separator, not a refusal`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.DoseAmountChanged("0,5"))

        assertEquals(BigDecimal("0.5"), model.state.value!!.draft.doseAmount)
        assertEquals("0,5", model.state.value!!.inputs.doseAmount)
    }

    @Test
    fun `the strength unit follows the value being present at all`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.StrengthChanged("48"))
        assertEquals("mg", model.state.value!!.draft.strengthUnit)

        model.onIntent(MedFormIntent.StrengthChanged(""))
        assertNull(model.state.value!!.draft.strengthUnit)
        assertNull(model.state.value!!.draft.strength)
    }

    @Test
    fun `the package size is typed in and kept as text and as a number`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.PackageSizeChanged("60"))

        val state = model.state.value!!
        assertEquals(BigDecimal("60"), state.draft.packageSize)
        assertEquals("60", state.inputs.packageSize)
    }

    @Test
    fun `the package size is not filled in from the stock and stays unknown`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.StockChanged("9"))

        val state = model.state.value!!
        assertEquals(BigDecimal("9"), state.draft.stockLeft)
        assertNull("the denominator must never be guessed from the stock", state.draft.packageSize)
    }

    @Test
    fun `a zero package size is refused with the field named`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.NameChanged("med-a"))
        model.onIntent(MedFormIntent.TimeAdded)
        model.onIntent(MedFormIntent.DoseAmountChanged("2"))
        model.onIntent(MedFormIntent.PackageSizeChanged("0"))

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        val state = model.state.value!!
        assertFalse(state.saved)
        assertTrue(DraftViolation.PackageSizeNotPositive in state.violations)
        assertEquals(DraftField.PACKAGE_SIZE, state.firstViolationField)
        assertEquals("what was typed must survive the refusal", "0", state.inputs.packageSize)
    }

    @Test
    fun `editing fills the package size text from the stored course`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "magnesium", name = "med-a").copy(packageSize = BigDecimal("60")))

        model.open("magnesium")
        advanceUntilIdle()

        assertEquals("60", model.state.value!!.inputs.packageSize)
    }

    // --- Сохранение и отказ -------------------------------------------------------------------

    @Test
    fun `saving is blocked until the name is filled in`() = runTest(dispatcher) {
        open()
        assertFalse(model.state.value!!.saveEnabled)

        model.onIntent(MedFormIntent.NameChanged("med-a"))

        assertTrue(model.state.value!!.saveEnabled)
    }

    @Test
    fun `a name of whitespace does not unlock saving`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.NameChanged("   "))

        assertFalse(model.state.value!!.saveEnabled)
    }

    @Test
    fun `a refusal keeps everything typed in and names the first broken rule`() = runTest(dispatcher) {
        open()
        fillValidSchedule()
        model.onIntent(MedFormIntent.TimeChanged(1, LocalTime.of(8, 0)))
        model.onIntent(MedFormIntent.StockChanged("-5"))

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        val state = model.state.value!!
        assertFalse(state.saved)
        assertEquals("med-a", state.draft.name)
        assertEquals("2", state.inputs.doseAmount)
        // Набранное в поле остатка не стирается — именно поэтому текст хранится рядом с числом.
        assertEquals("-5", state.inputs.stockLeft)
        assertEquals(DraftField.TIMES, state.firstViolationField)
        assertTrue(state.violations.any { it is DraftViolation.DuplicateTime })
        assertTrue(DraftViolation.StockNegative in state.violations)
        assertTrue(medications.snapshot().isEmpty())
    }

    @Test
    fun `a corrected field clears its message without another attempt to save`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.NameChanged("med-a"))
        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()
        assertNotNull(model.state.value!!.violationOf(DraftField.DOSE))

        model.onIntent(MedFormIntent.DoseAmountChanged("2"))

        assertNull(model.state.value!!.violationOf(DraftField.DOSE))
    }

    @Test
    fun `the form is silent about rules until the first attempt to save`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.NameChanged("med-a"))

        assertEquals(emptyList<DraftViolation>(), model.state.value!!.violations)
    }

    @Test
    fun `a saved schedule closes the form and produces the intakes of the window`() = runTest(dispatcher) {
        open()
        fillValidSchedule()

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertTrue(model.state.value!!.saved)
        assertEquals(1, medications.snapshot().size)
        assertTrue(intakes.snapshot().isNotEmpty())
    }

    @Test
    fun `an as needed course is saved without a single pending intake`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.NameChanged("med-b"))
        model.onIntent(MedFormIntent.DoseAmountChanged("1"))
        model.onIntent(MedFormIntent.ModeChanged(ScheduleMode.PRN))
        repeat(3) { model.onIntent(MedFormIntent.MaxPerDayIncreased) }

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertTrue(model.state.value!!.saved)
        assertEquals(3, medications.snapshot().single().maxPerDay)
        assertTrue(intakes.snapshot().isEmpty())
    }

    @Test
    fun `an as needed course without a limit is refused and points at the limit`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.NameChanged("med-b"))
        model.onIntent(MedFormIntent.DoseAmountChanged("1"))
        model.onIntent(MedFormIntent.ModeChanged(ScheduleMode.PRN))

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertEquals(DraftField.MAX_PER_DAY, model.state.value!!.firstViolationField)
        assertTrue(medications.snapshot().isEmpty())
    }

    @Test
    fun `a shape chosen before an inapplicable release form never reaches the stored course`() = runTest(dispatcher) {
        open()
        fillValidSchedule()
        model.onIntent(MedFormIntent.ShapeChanged(TabletShape.OVAL))
        model.onIntent(MedFormIntent.ReleaseFormChanged(MedicationForm.SPRAY))

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertNull(medications.snapshot().single().shape)
    }

    @Test
    fun `an end date before the start date is refused and points at the period`() = runTest(dispatcher) {
        open()
        fillValidSchedule()
        model.onIntent(MedFormIntent.IndefiniteToggled(false))
        model.onIntent(MedFormIntent.EndDateChanged(LocalDate.of(2026, 9, 12)))

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertEquals(DraftField.PERIOD, model.state.value!!.firstViolationField)
    }

    @Test
    fun `editing a course keeps its identifier and its profile`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "magnesium", name = "med-a"))
        model.open("magnesium")
        advanceUntilIdle()

        model.onIntent(MedFormIntent.FoodRelationChanged(FoodRelation.BEFORE_MEAL))
        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        val stored = medications.snapshot().single()
        assertEquals("magnesium", stored.id)
        assertEquals(MedsTestData.PROFILE_ID, stored.profileId)
        assertEquals(FoodRelation.BEFORE_MEAL, stored.foodRelation)
    }

    private fun open() {
        model.open()
        dispatcher.scheduler.advanceUntilIdle()
    }

    /** Минимально достаточный курс по расписанию: название, доза и два времени. */
    private fun fillValidSchedule() {
        model.onIntent(MedFormIntent.NameChanged("med-a"))
        model.onIntent(MedFormIntent.DoseAmountChanged("2"))
        model.onIntent(MedFormIntent.TimeAdded)
        model.onIntent(MedFormIntent.TimeAdded)
    }

    private companion object {
        const val NEW_ID = "55555555-5555-4555-8555-555555555555"
        const val WINDOW_DEPTH_DAYS = 7
    }
}
