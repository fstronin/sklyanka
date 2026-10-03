package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.domain.medication.CourseEndPreset
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.DraftField
import ru.stohastika.sklyanka.domain.medication.DraftViolation
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.medication.applicability
import ru.stohastika.sklyanka.domain.medication.normalized
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.stock.StockThresholds
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Шаги приёмки SKL-09 — правила формы лекарства (группа `@validation`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг обязан
 * совпадать с документом дословно, иначе прогон перестаёт быть контрактом задачи.
 *
 * Общее состояние сценария этим шагам не нужно: они проверяют правила черновика формы,
 * а лекарства, приёмы и часы в них не участвуют, — поэтому мир параметром конструктора
 * не принимается.
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

    /**
     * Нажатие «Сохранить» на собранной форме — крючок экранного прогона (SKL-57).
     *
     * Строка «человек сохраняет лекарство» объявлена здесь один раз и ловит сценарии
     * девяти задач; второе объявление в модуле экрана словарь шагов запрещает. Шаг
     * «Тогда форма закрывается» проверял бы тогда не действие человека, а своё нажатие.
     * Поэтому домен остаётся один, а экранный прогон подставляет сюда нажатие на форме:
     * в доменном прогоне крючок пуст, и шаг работает как раньше.
     */
    var submitOnScreen: (() -> Unit)? = null

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

    @Given("^в блоке «Остаток в упаковке» размер упаковки указан как (\\d+)$")
    fun thePackageSizeIs(size: String) {
        draft = draft.copy(packageSize = BigDecimal(size))
    }

    @Given("^в блоке «Остаток в упаковке» размер упаковки указан как (\\d+), а «Сейчас есть» пусто$")
    fun thePackageSizeWithoutStockIs(size: String) {
        draft = draft.copy(stockLeft = null, packageSize = BigDecimal(size))
    }

    @Then("^размер упаковки в форме не задан$")
    fun thePackageSizeIsUnknown() {
        // Знаменатель не подставляется из «Сейчас есть»: подставленное число выдавалось бы
        // за измеренное (ОВ-45).
        assertNull(draft.packageSize)
    }

    @Then("^у сохранённого лекарства размер упаковки — (\\d+) таблет\\S+$")
    fun theSavedCourseHasThatPackageSize(size: String) {
        submit()
        assertEquals(emptyList<DraftViolation>(), violations)
        assertEquals(BigDecimal(size), draft.toMedication(MEDICATION_ID).packageSize)
    }

    // --- Вид таблетки ------------------------------------------------------------------------------

    /**
     * Текущий черновик формы для экранного стенда (SKL-66).
     *
     * Экранные шаги не могут объявить «он выбирает форму выпуска …» повторно: строка уже
     * поймана этим объявлением, а два объявления на одну строку валят словарь шагов.
     * Поэтому сценарный шаг меняет доменный черновик, а экранный стенд синхронизируется
     * с ним перед проверкой состава.
     */
    fun currentDraft(): MedicationDraft = draft

    /** Экранное действие «выбирает форму»: домен и стенд обязаны видеть одно правило. */
    fun chooseForm(form: MedicationForm) {
        draft = draft.copy(form = form).normalized()
    }

    /** Экранное действие «выбирает вид таблетки»: то же правило `normalized()`. */
    fun chooseShape(shape: TabletShape?) {
        draft = draft.copy(shape = shape).normalized()
    }

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

    /**
     * Форма нового лекарства с названием — доменный черновик сценариев SKL-74/SKL-75.
     *
     * Остальные поля берутся из [defaultDraft]: сценарий называет только название,
     * а черновик без дозы и времени не прошёл бы проверку сохранения. Предмет сценария —
     * порог и пресет, а не валидация SKL-09; та же оговорка, что у шага-стенда.
     */
    @Given("^человек заполняет форму нового лекарства с названием «([^»]+)»$")
    fun theNewMedicationFormIsFilledWith(name: String) {
        draft = defaultDraft().copy(name = name)
    }

    @Given("^дата начала курса — (.+)$")
    fun theCourseStartDateIs(date: String) {
        draft = draft.copy(startDate = RussianDates.date(date, REFERENCE_DATE))
    }

    @Given("^человек открыл форму нового лекарства$")
    fun thePersonHasOpenedTheNewMedicationForm() {
        // В доменном прогоне формы нет: строка Предыстории утверждает, что человек
        // на форме, а экранные прогоны собирают её сами.
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

    @Then("^человеку указано, что размер упаковки должен быть больше нуля$")
    fun theRefusalNamesThePackageSize() {
        assertTrue(DraftViolation.PackageSizeNotPositive in violations)
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

    // --- Порог покупки и пресет срока (SKL-74, SKL-75) ----------------------------------------------

    @Given("^человек включил «Напомнить о покупке» и выбрал порог (\\d+) дней$")
    fun theStockReminderIsOnWithThreshold(days: Int) {
        draft = draft.copy(stockThresholdDays = days)
    }

    @Then("^у сохранённого лекарства порог напоминания равен (\\d+) дням$")
    fun theSavedCourseKeepsThatThreshold(days: Int) {
        submit()
        assertEquals(emptyList<DraftViolation>(), violations)
        assertEquals(days, draft.toMedication(MEDICATION_ID).stockThresholdDays)
    }

    @Given("^человек выбирает пресет «([^»]+)» при начале (.+)$")
    fun thePresetIsChosenFromThatStart(preset: String, start: String) {
        val day = RussianDates.date(start, REFERENCE_DATE)
        draft = draft.copy(startDate = day, endDate = presetOf(preset).endDateFrom(day))
    }

    @Then("^у сохранённого лекарства дата окончания — (.+)$")
    fun theSavedCourseEndsOn(date: String) {
        submit()
        assertEquals(emptyList<DraftViolation>(), violations)
        assertEquals(RussianDates.date(date, REFERENCE_DATE), draft.toMedication(MEDICATION_ID).endDate)
    }

    @Then("^курс завершается (.+) включительно$")
    fun theCourseRunsThroughThatDay(date: String) {
        // Последний день курса включительный: дата окончания и есть день приёма (ПР-6).
        assertEquals(RussianDates.date(date, REFERENCE_DATE), draft.toMedication(MEDICATION_ID).endDate)
    }

    private fun presetOf(label: String): CourseEndPreset = when (label) {
        "1 неделя" -> CourseEndPreset.WEEK_1
        "2 недели" -> CourseEndPreset.WEEKS_2
        "1 месяц" -> CourseEndPreset.MONTH_1
        "2 месяца" -> CourseEndPreset.MONTHS_2
        "3 месяца" -> CourseEndPreset.MONTHS_3
        "6 месяцев" -> CourseEndPreset.MONTHS_6
        "1 год" -> CourseEndPreset.YEAR_1
        else -> error("unknown preset: $label")
    }

    private fun submit() {
        submitted = draft
        violations = validate(draft.normalized())
        submitOnScreen?.invoke()
    }

    /** Времена из перечисления в кавычках: «08:00», «20:00» и «08:00». */
    private fun quotedTimes(text: String): List<LocalTime> =
        QUOTED.findAll(text).map { LocalTime.parse(it.groupValues[1]) }.toList()

    /** Число из строки сценария: «−5», «0», «9 таблеток» — единица словами не мешает. */
    private fun decimal(text: String): BigDecimal {
        // Минус сценария типографский, а не дефис: «−5», не «-5».
        val cleaned = text.trim().trim('«', '»').replace(TYPOGRAPHIC_MINUS, '-')
        val number = requireNotNull(SIGNED_NUMBER.find(cleaned)) { "no number in «$text»" }.value
        return BigDecimal(number.replace(',', '.'))
    }

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

        /** Число в начале строки: «−5», «9», «9,5» — с единицей словами после него. */
        val SIGNED_NUMBER = Regex("""-?\d+(?:[.,]\d+)?""")

        val QUOTED = Regex("«(\\d{2}:\\d{2})»")
        const val TYPOGRAPHIC_MINUS = '−'

        /**
         * Черновик, проходящий все девять правил.
         *
         * Каждый сценарий ломает в нём ровно одно поле: иначе отказ не доказывает, какое
         * правило сработало.
         */
/** Значения черновика по умолчанию — те же, что в таблицах примеров SKL-09. */
        val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)

        fun defaultDraft(): MedicationDraft = MedicationDraft(
            id = null,
            profileId = PROFILE_ID,
            name = "med-a",
            form = MedicationForm.TABLET,
            doseAmount = BigDecimal("2"),
            strength = BigDecimal("48"),
            strengthUnit = "mg",
            scheduleMode = ScheduleMode.SCHEDULED,
            times = listOf(DEFAULT_TIME),
            startDate = REFERENCE_DATE,
            endDate = null,
            stockLeft = BigDecimal("60"),
            stockThresholdDays = StockThresholds.DEFAULT_DAYS,
        )
    }
}
