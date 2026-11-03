package ru.stohastika.sklyanka.feature.meds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import ru.stohastika.sklyanka.domain.medication.CourseEndPreset
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.DraftField
import ru.stohastika.sklyanka.domain.medication.DraftViolation
import ru.stohastika.sklyanka.domain.medication.SaveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.StrengthUnit
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.schedule.IntakeGenerationPolicy
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.stohastika.sklyanka.domain.schedule.PlanScope
import ru.stohastika.sklyanka.domain.stock.StockThresholds
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.stohastika.sklyanka.feature.meds.form.ScheduleKind
import java.math.BigDecimal
import java.time.DayOfWeek
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
@Suppress("LargeClass")
class MedicationFormViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = MedsTestData.clock()
    private val activeProfile = FakeActiveProfile()
    private val medications = FakeMedications()
    private val intakes = FakeIntakes()
    private val profiles = FakeProfiles()
    private val log = RecordingLog()

    /** Кэш подписки: доступ «Про» для гейта формы (Р-22, Р-28). */
    private val proCache = FakeProCache()

    /** Сколько раз сценарий сохранения переспрашивал план приёмов: дубль виден числом. */
    private var plannedSaves = 0

    /** Идентификаторы выдаются на каждый вызов сохранения, как у продукта (SKL-141). */
    private var issuedIds = 0

    private val model = MedicationFormViewModel(
        activeProfile = activeProfile,
        medications = medications,
        saveMedication = SaveMedicationUseCase(
            validate = ValidateMedicationDraft(),
            medications = medications,
            planner = MedicationPlanner { medicationId, reason ->
                plannedSaves += 1
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
            newId = { "med-${++issuedIds}" },
        ),
        time = clock,
        proStatus = ObserveProStatusUseCase(proCache, clock),
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

    // --- Грязный черновик (RT-1, Р-87 п. 1) --------------------------------------------------

    @Test
    fun `a fresh form is clean and turns dirty on the first edit`() = runTest(dispatcher) {
        open()
        assertFalse("a brand-new empty form is clean", model.state.value!!.isDirty)

        model.onIntent(MedFormIntent.NameChanged("Magnesium"))
        assertTrue("typing a name dirties the form", model.state.value!!.isDirty)
    }

    @Test
    fun `an edit form stays clean until changed and cleans again when reverted`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "magnesium", name = "med-a"))
        model.open("magnesium")
        advanceUntilIdle()
        assertFalse("an untouched edit form is clean", model.state.value!!.isDirty)

        model.onIntent(MedFormIntent.NameChanged("med-b"))
        assertTrue("renaming dirties the edit form", model.state.value!!.isDirty)

        model.onIntent(MedFormIntent.NameChanged("med-a"))
        assertFalse("reverting the name cleans the edit form again", model.state.value!!.isDirty)
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
        assertEquals(StockThresholds.DEFAULT_DAYS, model.state.value!!.draft.stockThresholdDays)

        model.onIntent(MedFormIntent.StockReminderToggled(false))

        assertNull(model.state.value!!.draft.stockThresholdDays)
        assertFalse(model.state.value!!.applicability.stockThresholdEnabled)
    }

    @Test
    fun `choosing a purchase threshold writes the chosen number`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.StockReminderToggled(true))

        model.onIntent(MedFormIntent.StockThresholdChanged(14))

        assertEquals(14, model.state.value!!.draft.stockThresholdDays)
    }

    @Test
    fun `a course end preset sets the end date from the start date`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.CourseEndPresetChosen(CourseEndPreset.MONTH_1))

        assertEquals(LocalDate.of(2026, 10, 25), model.state.value!!.draft.endDate)
    }

    @Test
    fun `changing the start date does not move the chosen end date`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.CourseEndPresetChosen(CourseEndPreset.MONTH_1))

        model.onIntent(MedFormIntent.StartDateChanged(LocalDate.of(2026, 9, 30)))

        assertEquals(LocalDate.of(2026, 10, 25), model.state.value!!.draft.endDate)
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
    fun `the strength unit appears with the value and the default is the milligram`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.StrengthChanged("48"))
        assertEquals(StrengthUnit.MILLIGRAM, model.state.value!!.draft.strengthUnit)

        model.onIntent(MedFormIntent.StrengthChanged(""))
        assertNull(model.state.value!!.draft.strengthUnit)
        assertNull(model.state.value!!.draft.strength)
    }

    @Test
    fun `the chosen strength unit survives editing the number`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.StrengthChanged("48"))
        model.onIntent(MedFormIntent.StrengthUnitChanged(StrengthUnit.MICROGRAM))
        model.onIntent(MedFormIntent.StrengthChanged("250"))

        assertEquals(StrengthUnit.MICROGRAM, model.state.value!!.draft.strengthUnit)
    }

    @Test
    fun `a unit without a value is a violation of the pair rule`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.NameChanged("med-a"))
        model.onIntent(MedFormIntent.DoseAmountChanged("1"))
        model.onIntent(MedFormIntent.StrengthUnitChanged(StrengthUnit.INTERNATIONAL_UNIT))

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        // Форма «по расписанию» без времён нарушает ещё и ИНВ-6: проверяется само правило
        // пары — единица без значения (ИНВ-5), а не полный список нарушений черновика.
        assertTrue(
            "the pair rule must fire: ${model.state.value!!.violations}",
            DraftViolation.UnitWithoutStrength in model.state.value!!.violations,
        )
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

        assertEquals(1, medications.snapshot().size)
        assertTrue(intakes.snapshot().isNotEmpty())
    }

    @Test
    fun `a second save while the first is in flight leaves one medication and one plan`() = runTest(dispatcher) {
        open()
        fillValidSchedule()

        // Два нажатия подряд, как их делает человек «на всякий случай»: второе приходит
        // раньше, чем запись успевает завершиться. Дубль у нового черновика не виден
        // по идентификатору — его нет, и каждый вызов сохранения выдаёт новый (SKL-141).
        model.onIntent(MedFormIntent.Save)
        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertEquals("a double press must leave exactly one medication", 1, medications.snapshot().size)
        assertEquals("a double press must not double the plan", 1, plannedSaves)
    }

    @Test
    fun `a saved course tells the form to close exactly once`() = runTest(dispatcher) {
        val closes = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.closed.toList(closes) }
        open()
        fillValidSchedule()

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertEquals(1, closes.size)
    }

    @Test
    fun `a refusal tells the form nothing about closing`() = runTest(dispatcher) {
        val closes = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.closed.toList(closes) }
        open()
        model.onIntent(MedFormIntent.NameChanged("med-a"))

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertTrue(closes.isEmpty())
        assertTrue(model.state.value!!.violations.isNotEmpty())
    }

    @Test
    fun `opening the form again does not replay the previous save`() = runTest(dispatcher) {
        val first = mutableListOf<Unit>()
        val firstJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.closed.toList(first) }
        open()
        fillValidSchedule()
        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()
        assertEquals(1, first.size)
        firstJob.cancel()

        val replayed = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.closed.toList(replayed) }
        model.open()
        advanceUntilIdle()

        assertTrue("a new opening must not close from the previous save", replayed.isEmpty())
        assertNotNull(model.state.value)
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

    // --- Вид расписания и платная граница (ПР-15…ПР-21, Р-22, Р-28) --------------------------

    @Test
    fun `an interval of one day becomes the daily rule but keeps the interval field`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.INTERVAL))

        model.onIntent(MedFormIntent.IntervalChanged("1"))

        val state = model.state.value!!
        assertEquals(ScheduleRule.Daily, state.draft.schedule)
        assertEquals(ScheduleKind.DAILY, state.shownScheduleKind)
        // Поле интервала остаётся на экране: иначе второе число набрать было бы негде.
        assertEquals(ScheduleKind.INTERVAL, state.scheduleKind)
    }

    @Test
    fun `choosing the stepped kind hides the dose field and starts one stage`() = runTest(dispatcher) {
        open()

        model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.STAGES))

        val state = model.state.value!!
        assertFalse(state.dosePerIntakeVisible)
        assertEquals(1, state.inputs.stageDoses.size)
        assertEquals(ScheduleKind.STAGES, state.scheduleKind)
    }

    @Test
    fun `the last stage cannot be removed`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.STAGES))

        model.onIntent(MedFormIntent.StageRemoved(0))

        assertEquals(1, model.state.value!!.inputs.stageDoses.size)
    }

    @Test
    fun `creating a complex schedule without a subscription does not save and asks for the showcase`() =
        runTest(dispatcher) {
            val showcases = mutableListOf<Unit>()
            // Unconfined: подписка встаёт сразу, и сигнал доходит до сборщика в момент
            // отправки, а не в следующий обход планировщика.
            backgroundScope.launch(Dispatchers.Unconfined) {
                model.showcase.collect { showcases += it }
            }
            open()
            fillValidSchedule()
            model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.WEEKDAYS))
            model.onIntent(MedFormIntent.WeekdayToggled(DayOfWeek.MONDAY))
            model.onIntent(MedFormIntent.WeekdayToggled(DayOfWeek.WEDNESDAY))

            model.onIntent(MedFormIntent.Save)
            advanceUntilIdle()

            assertTrue(
                "the course must not be saved",
                medications.snapshot().isEmpty(),
            )
            val state = model.state.value!!
            assertEquals("the showcase must be asked exactly once", 1, showcases.size)
            assertTrue(state.proReasonVisible)
        }

    @Test
    fun `creating a complex schedule with a subscription saves it`() = runTest(dispatcher) {
        proCache.grant(MedsTestData.TODAY.plusDays(30))
        open()
        fillValidSchedule()
        model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.CYCLES))
        model.onIntent(MedFormIntent.CycleOnChanged("21"))
        model.onIntent(MedFormIntent.CycleOffChanged("7"))

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        val stored = medications.snapshot().single()
        assertEquals(ScheduleRule.Cycles(onDays = 21, offDays = 7), stored.schedule)
        assertFalse(model.state.value!!.proReasonVisible)
    }

    @Test
    fun `editing an already complex course passes without a subscription`() = runTest(dispatcher) {
        medications.put(
            MedsTestData.medication(id = "magnesium", name = "med-a").copy(schedule = ScheduleRule.Cycles(21, 7)),
        )
        model.open("magnesium")
        advanceUntilIdle()

        model.onIntent(MedFormIntent.CycleOnChanged("14"))
        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertEquals(ScheduleRule.Cycles(onDays = 14, offDays = 7), medications.snapshot().single().schedule)
    }

    @Test
    fun `moving a daily course to a complex kind without a subscription is closed`() = runTest(dispatcher) {
        medications.put(MedsTestData.medication(id = "magnesium", name = "med-a"))
        model.open("magnesium")
        advanceUntilIdle()
        model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.CYCLES))
        model.onIntent(MedFormIntent.CycleOnChanged("21"))
        model.onIntent(MedFormIntent.CycleOffChanged("7"))

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertEquals("the daily rule must stay", ScheduleRule.Daily, medications.snapshot().single().schedule)
    }

    @Test
    fun `choosing a paid kind without a subscription asks for the showcase at once`() = runTest(dispatcher) {
        val showcases = collectShowcases()
        open()

        model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.CYCLES))
        advanceUntilIdle()

        // Точка выбора платной функции (Р-67 п. 3, §5.10): вид уже применён, параметры
        // видны, и витрина просится тут же — до нижней кнопки.
        assertEquals(1, showcases.size)
        assertEquals(ScheduleKind.CYCLES, model.state.value!!.scheduleKind)
    }

    @Test
    fun `the weekdays kind never asks for the showcase on its own days`() = runTest(dispatcher) {
        val showcases = collectShowcases()
        open()
        fillValidSchedule()

        model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.WEEKDAYS))
        model.onIntent(MedFormIntent.WeekdayToggled(DayOfWeek.MONDAY))
        advanceUntilIdle()
        assertTrue("the first weekday is free (decision R-24)", showcases.isEmpty())

        model.onIntent(MedFormIntent.WeekdayToggled(DayOfWeek.WEDNESDAY))
        advanceUntilIdle()
        assertTrue("the second day is not a point of choice by itself", showcases.isEmpty())

        // Плату называет только кнопка: она закрывает сохранение, а не выбор (§5.10).
        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertEquals(1, showcases.size)
        assertTrue(medications.snapshot().isEmpty())
    }

    @Test
    fun `a paid kind stays silent while the subscription is active`() = runTest(dispatcher) {
        proCache.grant(MedsTestData.TODAY.plusDays(30))
        val showcases = collectShowcases()
        open()

        model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.STAGES))
        advanceUntilIdle()

        assertTrue(showcases.isEmpty())
    }

    @Test
    fun `changing the kind of an already complex course never asks for the showcase`() = runTest(dispatcher) {
        medications.put(
            MedsTestData.medication(id = "magnesium", name = "med-a").copy(schedule = ScheduleRule.Cycles(21, 7)),
        )
        model.open("magnesium")
        advanceUntilIdle()
        val showcases = collectShowcases()

        model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.STAGES))
        advanceUntilIdle()

        // Заведённый сложный курс уже оплачен (Р-28): вид меняется внутри него.
        assertTrue(showcases.isEmpty())
    }

    @Test
    fun `buying from the form unlocks saving without leaving the draft behind`() = runTest(dispatcher) {
        val showcases = collectShowcases()
        open()
        fillValidSchedule()
        model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.CYCLES))
        model.onIntent(MedFormIntent.CycleOnChanged("21"))
        model.onIntent(MedFormIntent.CycleOffChanged("7"))
        val askedOnChoice = showcases.size

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertTrue("the complex course must not be saved for free", medications.snapshot().isEmpty())
        assertEquals("the blocked save must ask the showcase again", askedOnChoice + 1, showcases.size)
        assertTrue(model.state.value!!.proReasonVisible)

        // Магазин записал покупку в кэш подписки, а форма перечитывает доступ, не теряя
        // черновик: иначе человеку пришлось бы уйти из формы и завести курс заново.
        proCache.grant(MedsTestData.TODAY.plusDays(30))
        model.refreshProAccess()
        advanceUntilIdle()
        assertTrue(model.state.value!!.proIncluded)

        model.onIntent(MedFormIntent.Save)
        advanceUntilIdle()

        assertEquals(ScheduleRule.Cycles(onDays = 21, offDays = 7), medications.snapshot().single().schedule)
        assertFalse(model.state.value!!.proReasonVisible)
    }

    @Test
    fun `refreshing the pro access updates the snapshot and tolerates a closed form`() = runTest(dispatcher) {
        // Формы нет: перечитывание обязано тихо ничего не сделать, а не упасть.
        model.refreshProAccess()
        advanceUntilIdle()
        assertNull(model.state.value)

        open()
        assertFalse(model.state.value!!.proIncluded)

        proCache.grant(MedsTestData.TODAY.plusDays(30))
        model.refreshProAccess()
        advanceUntilIdle()

        assertTrue(model.state.value!!.proIncluded)
    }

    @Test
    fun `the paid reason waits for a draft the form would not refuse itself`() = runTest(dispatcher) {
        open()
        model.onIntent(MedFormIntent.NameChanged("med-a"))
        model.onIntent(MedFormIntent.DoseAmountChanged("2"))
        model.onIntent(MedFormIntent.ScheduleKindChanged(ScheduleKind.CYCLES))
        model.onIntent(MedFormIntent.CycleOnChanged("21"))
        model.onIntent(MedFormIntent.CycleOffChanged("7"))

        // Времени приёма ещё нет: нажатие «Сохранить» назовёт отказ формы, и подсказка
        // о подписке не имеет права обещать покупку вместо него (§5.10).
        assertFalse(model.state.value!!.proReasonVisible)

        model.onIntent(MedFormIntent.TimeAdded)

        assertTrue(model.state.value!!.proReasonVisible)
    }

    private fun open() {
        model.open()
        dispatcher.scheduler.advanceUntilIdle()
    }

    /**
     * Подписка на сигнал витрины.
     *
     * `Unconfined`: подписка встаёт сразу, и сигнал доходит до сборщика в момент отправки,
     * а не в следующий обход планировщика (как и в тесте про закрытое создание выше).
     */
    private fun TestScope.collectShowcases(): MutableList<Unit> {
        val showcases = mutableListOf<Unit>()
        backgroundScope.launch(Dispatchers.Unconfined) {
            model.showcase.collect { showcases += it }
        }
        return showcases
    }

    /** Минимально достаточный курс по расписанию: название, доза и два времени. */
    private fun fillValidSchedule() {
        model.onIntent(MedFormIntent.NameChanged("med-a"))
        model.onIntent(MedFormIntent.DoseAmountChanged("2"))
        model.onIntent(MedFormIntent.TimeAdded)
        model.onIntent(MedFormIntent.TimeAdded)
    }

    private companion object {
        const val WINDOW_DEPTH_DAYS = 7
    }
}
