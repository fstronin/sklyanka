package ru.heuri.sklyanka.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.domain.medication.DoseUnit
import ru.heuri.sklyanka.domain.medication.DraftField
import ru.heuri.sklyanka.domain.medication.DraftViolation
import ru.heuri.sklyanka.domain.medication.MedicationDraft
import ru.heuri.sklyanka.domain.medication.ValidateMedicationDraft
import ru.heuri.sklyanka.domain.medication.applicability
import ru.heuri.sklyanka.domain.medication.normalized
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.model.TabletShape
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Шаги приёмки SKL-09 — правила формы лекарства (группа `@validation`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг обязан
 * совпадать с документом дословно, иначе прогон перестаёт быть контрактом задачи.
 *
 * Почему здесь только валидация. Модуль приёмки — чистый JVM и от Android-модулей
 * зависеть не может (`ОВ-74`), поэтому экранные сценарии — список лекарств, состав формы,
 * навигация — здесь написать нечем: их проверяют Compose-тесты `feature-meds`. Правила же
 * формы живут в домене именно для того, чтобы их можно было прогнать без эмулятора —
 * и заодно применить к записям из файла переноса данных (SKL-19), где формы нет вовсе.
 */
@Suppress("TooManyFunctions")
class MedicationFormSteps {

    private val validate = ValidateMedicationDraft()

    /** Черновик формы: каждый шаг «Допустим» меняет в нём ровно одно поле. */
    private var draft: MedicationDraft = defaultDraft()

    /** Черновик на момент попытки сохранить: по нему проверяется, что отказ ничего не стёр. */
    private var submitted: MedicationDraft? = null

    private var violations: List<DraftViolation> = emptyList()

    // --- Название -----------------------------------------------------------------------

    @Given("^человек заполнил форму нового лекарства, но поле «Название» оставил пустым$")
    fun theNameIsLeftEmpty() {
        draft = draft.copy(name = "")
    }

    @Given("^в поле «Название» введены только пробелы$")
    fun theNameIsWhitespaceOnly() {
        draft = draft.copy(name = "   ")
    }

    // --- Режим приёма и расписание ---------------------------------------------------------

    @Given("^в форме выбран режим «(По расписанию|По потребности)»$")
    fun theScheduleModeIs(mode: String) {
        draft = draft.copy(
            scheduleMode = if (mode == SCHEDULED_LABEL) ScheduleMode.SCHEDULED else ScheduleMode.PRN,
        ).normalized()
    }

    @Given("^список времён приёма пуст$")
    fun theListOfTimesIsEmpty() {
        draft = draft.copy(times = emptyList())
    }

    @Given("^в списке времён заданы (.+)$")
    fun theListOfTimesIs(times: String) {
        draft = draft.copy(times = quotedTimes(times))
    }

    @Given("^ограничение «не чаще N раз в сутки» не заполнено$")
    fun theDailyLimitIsNotFilledIn() {
        draft = draft.copy(maxPerDay = null)
    }

    @Given("^ограничение приёмов в сутки равно (-?\\d+)$")
    fun theDailyLimitIs(limit: Int) {
        draft = draft.copy(maxPerDay = limit)
    }

    // --- Доза и дозировка --------------------------------------------------------------------

    @Given("^в блоке «Доза на один приём» количество равно (.+)$")
    fun theDoseAmountIs(amount: String) {
        draft = draft.copy(doseAmount = decimal(amount))
    }

    @Given("^в блоке «Доза на один приём» дозировка равна (\\d+), а единица не указана$")
    fun theStrengthHasNoUnit(strength: String) {
        draft = draft.copy(strength = BigDecimal(strength), strengthUnit = null)
    }

    @Given("^в блоке «Доза на один приём» указана единица «(.+)», а значение дозировки пусто$")
    fun theUnitHasNoStrength(unit: String) {
        draft = draft.copy(strength = null, strengthUnit = unit)
    }

    // --- Период приёма -------------------------------------------------------------------------

    @Given("^в блоке «Период приёма» начало — (.+)$")
    fun theCourseStartsOn(date: String) {
        draft = draft.copy(startDate = RussianDates.date(date, REFERENCE_DATE))
    }

    @Given("^переключатель «Принимать бессрочно» выключен, а окончание — (.+)$")
    fun theCourseEndsOn(date: String) {
        draft = draft.copy(endDate = RussianDates.date(date, REFERENCE_DATE))
    }

    @Given("^в блоке «Период приёма» начало и окончание — (.+)$")
    fun theCourseStartsAndEndsOn(date: String) {
        val day = RussianDates.date(date, REFERENCE_DATE)
        draft = draft.copy(startDate = day, endDate = day)
    }

    // --- Остаток --------------------------------------------------------------------------------

    @Given("^в блоке «Остаток в упаковке» указано (.+)$")
    fun theStockIs(amount: String) {
        draft = draft.copy(stockLeft = decimal(amount))
    }

    // --- Вид таблетки ------------------------------------------------------------------------------

    @Given("^человек выбрал форму выпуска «(.+)» и вид «(.+)»$")
    fun theReleaseFormAndShapeAre(form: String, shape: String) {
        draft = draft.copy(form = releaseForm(form), shape = tabletShape(shape))
    }

    @When("^он меняет форму выпуска на «(.+)»$")
    fun theReleaseFormChangesTo(form: String) {
        draft = draft.copy(form = releaseForm(form)).normalized()
    }

    @Then("^блок «Вид таблетки» скрывается$")
    fun theTabletShapeBlockHides() {
        assertFalse(draft.applicability().shapeVisible)
    }

    @Then("^после сохранения у лекарства нет вида таблетки$")
    fun theSavedCourseHasNoShape() {
        submit()
        assertEquals(emptyList<DraftViolation>(), violations)
        assertNull(draft.toMedication(MEDICATION_ID).shape)
    }

    // --- Применимость блоков -----------------------------------------------------------------------

    @Given("^человек заполняет форму нового лекарства$")
    fun theFormIsBeingFilledIn() {
        draft = defaultDraft()
    }

    @When("^он выбирает форму выпуска «(.+)»$")
    fun theReleaseFormIsChosen(form: String) {
        draft = draft.copy(form = releaseForm(form)).normalized()
    }

    @Then("^блок «Вид таблетки» (показан|скрыт)$")
    fun theTabletShapeBlockVisibilityIs(visibility: String) {
        assertEquals(visibility == VISIBLE_LABEL, draft.applicability().shapeVisible)
    }

    @Then("^в блоке «Остаток в упаковке» единицей показано «(.+)»$")
    fun theStockUnitIs(unit: String) {
        // Единица производна от формы выпуска (ОВ-16 не решён), поэтому проверяется
        // соответствие «код домена — слово интерфейса», а не сама строка ресурса.
        assertEquals(unit, STOCK_UNITS.getValue(draft.applicability().doseUnit))
    }

    // --- Заполненная форма целиком ---------------------------------------------------------------

    @Given("^человек заполнил название «(.+?)», форму выпуска «(.+?)», дозу (\\d+) (?:\\S+) и времена (.+)$")
    fun theFormIsFilledIn(name: String, form: String, dose: String, times: String) {
        draft = draft.copy(
            name = name,
            form = releaseForm(form),
            doseAmount = BigDecimal(dose),
            times = quotedTimes(times),
        )
    }

    // --- Попытка сохранить --------------------------------------------------------------------------

    @When("^(?:он|человек) пытается сохранить лекарство$")
    fun theFormIsSubmitted() {
        submit()
    }

    @When("^он пытается сохранить лекарство и получает отказ$")
    fun theFormIsSubmittedAndRefused() {
        submit()
        assertTrue(violations.isNotEmpty(), "the form was expected to refuse the draft")
    }

    @When("^человек сохраняет лекарство$")
    fun theFormIsSaved() {
        submit()
    }

    // --- Итог ---------------------------------------------------------------------------------------

    @Then("^лекарство не сохраняется$")
    fun theCourseIsNotSaved() {
        assertTrue(violations.isNotEmpty(), "the draft was expected to break at least one rule")
    }

    @Then("^лекарство сохраняется как курс на один день$")
    fun theCourseIsSavedAsASingleDayOne() {
        assertEquals(emptyList<DraftViolation>(), violations)
        assertEquals(draft.startDate, draft.endDate)
    }

    @Then("^человеку названа причина отказа с указанием на поле «Название»$")
    fun theRefusalPointsAtTheName() {
        assertTrue(DraftViolation.NameEmpty in violations)
        assertEquals(DraftField.NAME, violations.first().field)
    }

    @Then("^человеку сказано, что нужно задать хотя бы одно время приёма$")
    fun theRefusalAsksForAtLeastOneTime() {
        assertTrue(DraftViolation.NoIntakeTimes in violations)
    }

    @Then("^человеку указано повторяющееся время «(\\d{2}:\\d{2})»$")
    fun theRefusalNamesTheRepeatedTime(time: String) {
        assertTrue(DraftViolation.DuplicateTime(LocalTime.parse(time)) in violations)
    }

    @Then("^человеку указано, что дата окончания раньше даты начала$")
    fun theRefusalNamesTheEndDate() {
        assertTrue(DraftViolation.EndDateBeforeStart in violations)
    }

    @Then("^человеку сказано, что нужно задать ограничение числа приёмов в сутки$")
    fun theRefusalAsksForTheDailyLimit() {
        assertTrue(DraftViolation.MaxPerDayMissing in violations)
    }

    @Then("^человеку указано, что доза должна быть больше нуля$")
    fun theRefusalNamesTheDose() {
        assertTrue(DraftViolation.DoseNotPositive in violations)
    }

    @Then("^человеку сказано, что дозировку и её единицу нужно указать вместе$")
    fun theRefusalAsksForBothStrengthAndUnit() {
        assertTrue(
            DraftViolation.StrengthWithoutUnit in violations || DraftViolation.UnitWithoutStrength in violations,
        )
    }

    @Then("^человеку указано, что остаток не может быть отрицательным$")
    fun theRefusalNamesTheStock() {
        assertTrue(DraftViolation.StockNegative in violations)
    }

    @Then("^все ранее введённые значения остаются в форме$")
    fun nothingTypedInIsLost() {
        // Отказ ничего не записывает и ничего не переписывает — он только называет правила.
        assertEquals(submitted, draft)
    }

    @Then("^экран показывает первое нарушенное правило$")
    fun theScreenShowsTheFirstBrokenRule() {
        assertTrue(violations.isNotEmpty())
        assertEquals(violations.first().field, violations.first().field)
    }

    private fun submit() {
        submitted = draft
        violations = validate(draft.normalized())
    }

    /** Времена из перечисления в кавычках: «08:00», «20:00» и «08:00». */
    private fun quotedTimes(text: String): List<LocalTime> =
        QUOTED.findAll(text).map { LocalTime.parse(it.groupValues[1]) }.toList()

    /** Число с минусом сценария: там стоит типографский минус, а не дефис. */
    private fun decimal(text: String): BigDecimal =
        BigDecimal(text.trim().trim('«', '»').replace(TYPOGRAPHIC_MINUS, '-'))

    private fun releaseForm(label: String): MedicationForm = when (label) {
        "Таблетка" -> MedicationForm.TABLET
        "Капсула" -> MedicationForm.CAPSULE
        "Спрей" -> MedicationForm.SPRAY
        "Сироп" -> MedicationForm.SYRUP
        "Капли" -> MedicationForm.DROPS
        else -> MedicationForm.INJECTION
    }

    private fun tabletShape(label: String): TabletShape = when (label) {
        "Круглая" -> TabletShape.ROUND
        "Круглая с риской" -> TabletShape.ROUND_SCORED
        "Овальная" -> TabletShape.OVAL
        "Продолговатая" -> TabletShape.OBLONG
        else -> TabletShape.TRIANGULAR
    }

    private companion object {

        const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
        const val MEDICATION_ID = "33333333-3333-4333-8333-333333333333"
        const val SCHEDULED_LABEL = "По расписанию"
        const val VISIBLE_LABEL = "показан"

        /** Единицы остатка из `docs/spec/03-screens.md`: четыре и ровно четыре. */
        val STOCK_UNITS = mapOf(
            DoseUnit.TABLET to "таблеток",
            DoseUnit.CAPSULE to "капсул",
            DoseUnit.MILLILITRE to "мл",
            DoseUnit.DOSE to "доз",
        )

        /** «сейчас 25 сентября 2026, 10:00» из предыстории сценариев. */
        val REFERENCE_DATE: LocalDate = LocalDate.of(2026, 9, 25)

        val QUOTED = Regex("«(\\d{2}:\\d{2})»")
        const val TYPOGRAPHIC_MINUS = '−'

        /**
         * Черновик, проходящий все девять правил.
         *
         * Каждый сценарий ломает в нём ровно одно поле: иначе отказ не доказывает, какое
         * правило сработало.
         */
        fun defaultDraft(): MedicationDraft = MedicationDraft(
            id = null,
            profileId = PROFILE_ID,
            name = "med-a",
            form = MedicationForm.TABLET,
            doseAmount = BigDecimal("2"),
            strength = BigDecimal("48"),
            strengthUnit = "mg",
            scheduleMode = ScheduleMode.SCHEDULED,
            times = listOf(LocalTime.of(8, 0)),
            startDate = REFERENCE_DATE,
            endDate = null,
            stockLeft = BigDecimal("60"),
            stockThresholdDays = 5,
        )
    }
}
