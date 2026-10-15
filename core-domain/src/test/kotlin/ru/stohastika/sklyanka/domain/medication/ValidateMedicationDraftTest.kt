package ru.stohastika.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.domain.model.Stage
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Девять правил формы по отдельности и вместе (`acceptance.feature`, группа `@validation`).
 *
 * Проверяется именно состав списка, а не сам факт отказа: правило, вернувшее «что-то не так»,
 * экрану бесполезно — ему нужно поле, к которому прокручиваться.
 */
@DisplayName("Form rules reject a meaningless course and name the field")
class ValidateMedicationDraftTest {

    private val validate = ValidateMedicationDraft()

    @Test
    fun `a filled in course passes every rule`() {
        assertEquals(emptyList<DraftViolation>(), validate(Drafts.valid()))
    }

    @Test
    fun `an as needed course with a limit passes every rule`() {
        assertEquals(emptyList<DraftViolation>(), validate(Drafts.validPrn()))
    }

    // --- Название -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\t"])
    fun `a name made of whitespace counts as empty`(name: String) {
        val violations = validate(Drafts.valid().copy(name = name))

        assertEquals(listOf(DraftViolation.NameEmpty), violations)
        assertEquals(DraftField.NAME, violations.first().field)
    }

    // --- Доза и дозировка -----------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = ["0", "-2"])
    fun `a dose of zero or below is rejected`(dose: String) {
        assertEquals(listOf(DraftViolation.DoseNotPositive), validate(dose(dose)))
    }

    @Test
    fun `a missing dose is rejected the same way as a zero one`() {
        assertEquals(listOf(DraftViolation.DoseNotPositive), validate(Drafts.valid().copy(doseAmount = null)))
    }

    @Test
    fun `a strength without its unit is rejected`() {
        assertEquals(
            listOf(DraftViolation.StrengthWithoutUnit),
            validate(Drafts.valid().copy(strengthUnit = null)),
        )
    }

    @Test
    fun `a unit without its strength is rejected`() {
        assertEquals(
            listOf(DraftViolation.UnitWithoutStrength),
            validate(Drafts.valid().copy(strength = null)),
        )
    }

    @Test
    fun `neither strength nor unit is a complete course`() {
        assertTrue(validate(Drafts.valid().copy(strength = null, strengthUnit = null)).isEmpty())
    }

    // --- Расписание -----------------------------------------------------------------------

    @Test
    fun `a scheduled course without a single time is rejected`() {
        val violations = validate(Drafts.valid().copy(times = emptyList()))

        assertEquals(listOf(DraftViolation.NoIntakeTimes), violations)
        assertEquals(DraftField.TIMES, violations.first().field)
    }

    @Test
    fun `a repeated time is rejected and the repeated value is named`() {
        // Два одинаковых времени породили бы два приёма на одну минуту и два напоминания подряд.
        val violations = validate(
            Drafts.valid().copy(times = listOf(Drafts.MORNING, Drafts.EVENING, Drafts.MORNING)),
        )

        assertEquals(listOf(DraftViolation.DuplicateTime(Drafts.MORNING)), violations)
    }

    // --- «По потребности» -----------------------------------------------------------------

    @Test
    fun `an as needed course without a daily limit is rejected`() {
        val violations = validate(Drafts.validPrn().copy(maxPerDay = null))

        assertEquals(listOf(DraftViolation.MaxPerDayMissing), violations)
        assertEquals(DraftField.MAX_PER_DAY, violations.first().field)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1])
    fun `a daily limit of zero or below is rejected`(limit: Int) {
        assertEquals(
            listOf(DraftViolation.MaxPerDayNotPositive),
            validate(Drafts.validPrn().copy(maxPerDay = limit)),
        )
    }

    // --- Период приёма --------------------------------------------------------------------

    @Test
    fun `an end date before the start date is rejected`() {
        val violations = validate(Drafts.valid().copy(endDate = LocalDate.of(2026, 9, 12)))

        assertEquals(listOf(DraftViolation.EndDateBeforeStart), violations)
        assertEquals(DraftField.PERIOD, violations.first().field)
    }

    @Test
    fun `an end date equal to the start date is a one day course and passes`() {
        assertTrue(validate(Drafts.valid().copy(endDate = Drafts.TODAY)).isEmpty())
    }

    @Test
    fun `an empty end date means an open ended course and is not a violation`() {
        assertTrue(validate(Drafts.valid().copy(endDate = null)).isEmpty())
    }

    // --- Остаток --------------------------------------------------------------------------

    @Test
    fun `a negative stock is rejected`() {
        val violations = validate(Drafts.valid().copy(stockLeft = BigDecimal("-5")))

        assertEquals(listOf(DraftViolation.StockNegative), violations)
        assertEquals(DraftField.STOCK, violations.first().field)
    }

    @Test
    fun `an empty stock means the person does not track it and is not a violation`() {
        assertTrue(validate(Drafts.valid().copy(stockLeft = null)).isEmpty())
    }

    @Test
    fun `a positive package size passes`() {
        assertTrue(validate(Drafts.valid().copy(packageSize = BigDecimal("60"))).isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-0.5"])
    fun `a non-positive package size is rejected`(size: String) {
        val violations = validate(Drafts.valid().copy(packageSize = BigDecimal(size)))

        assertEquals(listOf(DraftViolation.PackageSizeNotPositive), violations)
        assertEquals(DraftField.PACKAGE_SIZE, violations.first().field)
    }

    @Test
    fun `an empty package size means it is unknown and is not a violation`() {
        assertTrue(validate(Drafts.valid().copy(packageSize = null)).isEmpty())
    }

    @Test
    fun `a package size without any stock is not a violation`() {
        // Без остатка размер ни на что не влияет; блок остатка появится, когда появится
        // остаток (ОВ-45).
        assertTrue(validate(Drafts.valid().copy(stockLeft = null, packageSize = BigDecimal("60"))).isEmpty())
    }

    // --- Вместе ---------------------------------------------------------------------------

    @Test
    fun `every broken rule is reported, not only the first one`() {
        val violations = validate(
            Drafts.valid().copy(
                name = " ",
                doseAmount = BigDecimal.ZERO,
                times = listOf(Drafts.MORNING, Drafts.MORNING),
                stockLeft = BigDecimal("-1"),
            ),
        )

        assertEquals(4, violations.size)
        assertTrue(DraftViolation.NameEmpty in violations)
        assertTrue(DraftViolation.DoseNotPositive in violations)
        assertTrue(DraftViolation.DuplicateTime(Drafts.MORNING) in violations)
        assertTrue(DraftViolation.StockNegative in violations)
    }

    @Test
    fun `the first violation is the topmost block of the form`() {
        // Экран прокручивается к первому нарушению списка, поэтому порядок здесь — контракт,
        // а не случайность реализации.
        val violations = validate(
            Drafts.valid().copy(name = "", doseAmount = BigDecimal.ZERO, stockLeft = BigDecimal("-1")),
        )

        assertEquals(DraftField.NAME, violations.first().field)
        assertEquals(DraftField.STOCK, violations.last().field)
    }

    @Test
    fun `rules of the other schedule mode never fire`() {
        // У курса по расписанию ограничение приёмов неприменимо, у курса по потребности —
        // список времён. Правило, сработавшее на неприменимом поле, называет человеку
        // блок, которого он на экране не видит.
        val scheduled = validate(Drafts.valid().normalized())
        val asNeeded = validate(Drafts.validPrn().normalized())

        assertFalse(DraftViolation.MaxPerDayMissing in scheduled)
        assertFalse(DraftViolation.NoIntakeTimes in asNeeded)
    }

    // --- Вид расписания (ИНВ-23…ИНВ-25, ПР-16…ПР-19) -------------------------------------

    /**
     * Таблица примеров приёмки `SKL-117.feature`, группа `@validation`: каждый случай
     * настроен ровно одним способом и назван своим правилом.
     */
    @Test
    fun `weekdays without a single chosen day are rejected`() {
        val violations = validate(Drafts.valid().copy(schedule = ScheduleRule.Weekdays(emptySet())))

        assertEquals(listOf(DraftViolation.ScheduleDaysEmpty), violations)
        assertEquals(DraftField.SCHEDULE, violations.first().field)
    }

    @Test
    fun `weekdays with one chosen day are a valid base schedule`() {
        assertTrue(
            validate(Drafts.valid().copy(schedule = ScheduleRule.Weekdays(setOf(DayOfWeek.WEDNESDAY)))).isEmpty(),
        )
    }

    @Test
    fun `weekdays with several chosen days are a valid complex schedule`() {
        val rule = ScheduleRule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))

        assertTrue(validate(Drafts.valid().copy(schedule = rule)).isEmpty())
        assertTrue(rule.isComplex)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 0, -3])
    fun `an interval shorter than two days is rejected`(interval: Int) {
        val violations = validate(Drafts.valid().copy(schedule = ScheduleRule.EveryNDays(interval)))

        assertEquals(listOf(DraftViolation.ScheduleIntervalTooShort), violations)
        assertEquals(DraftField.SCHEDULE, violations.first().field)
    }

    @Test
    fun `an interval of two days is valid`() {
        assertTrue(validate(Drafts.valid().copy(schedule = ScheduleRule.EveryNDays(2))).isEmpty())
    }

    @ParameterizedTest
    @CsvSource("0,7", "21,0", "0,0", "-1,7")
    fun `a cycle without intake or without rest days is rejected`(onDays: Int, offDays: Int) {
        val violations = validate(Drafts.valid().copy(schedule = ScheduleRule.Cycles(onDays, offDays)))

        assertEquals(listOf(DraftViolation.ScheduleCycleLengthNotPositive), violations)
        assertEquals(DraftField.SCHEDULE, violations.first().field)
    }

    @Test
    fun `twenty one days on and seven off is a valid cycle`() {
        assertTrue(validate(Drafts.valid().copy(schedule = ScheduleRule.Cycles(onDays = 21, offDays = 7))).isEmpty())
    }

    @Test
    fun `stages without a single stage are rejected`() {
        val violations = validate(Drafts.valid().copy(schedule = ScheduleRule.Staged(emptyList())))

        assertEquals(listOf(DraftViolation.ScheduleStagesEmpty), violations)
        assertEquals(DraftField.SCHEDULE, violations.first().field)
    }

    @Test
    fun `a stage without a dose is rejected as a missing dose`() {
        val stages = listOf(Stage(BigDecimal.ONE, days = 7), Stage(BigDecimal.ZERO, days = 24))

        assertEquals(
            listOf(DraftViolation.ScheduleStageDoseNotPositive),
            validate(Drafts.valid().copy(schedule = ScheduleRule.Staged(stages))),
        )
    }

    @Test
    fun `a stage without days is rejected as a missing duration`() {
        val stages = listOf(Stage(BigDecimal.ONE, days = 0))

        assertEquals(
            listOf(DraftViolation.ScheduleStageDaysNotPositive),
            validate(Drafts.valid().copy(schedule = ScheduleRule.Staged(stages))),
        )
    }

    @Test
    fun `a stage missing both its dose and its duration is named twice`() {
        // Доза и длительность — два разных поля этапа, и человеку о них говорят разными словами.
        val stages = listOf(Stage(BigDecimal.ZERO, days = 0))

        assertEquals(
            listOf(DraftViolation.ScheduleStageDoseNotPositive, DraftViolation.ScheduleStageDaysNotPositive),
            validate(Drafts.valid().copy(schedule = ScheduleRule.Staged(stages))),
        )
    }

    @Test
    fun `a last stage that goes beyond the course end is rejected`() {
        val stages = listOf(Stage(BigDecimal("0.5"), days = 7), Stage(BigDecimal.ONE, days = 24))
        // Курс на тридцать дней, схема на тридцать один: последний день схемы за концом курса.
        val draft = Drafts.valid().copy(
            schedule = ScheduleRule.Staged(stages),
            endDate = Drafts.TODAY.plusDays(29),
        )

        assertEquals(listOf(DraftViolation.ScheduleStagesBeyondCourse), validate(draft))
    }

    @Test
    fun `a last stage that ends exactly on the course end is valid`() {
        val stages = listOf(Stage(BigDecimal("0.5"), days = 7), Stage(BigDecimal.ONE, days = 24))
        val draft = Drafts.valid().copy(
            schedule = ScheduleRule.Staged(stages),
            endDate = Drafts.TODAY.plusDays(30),
        )

        assertTrue(validate(draft).isEmpty())
    }

    @Test
    fun `stages of an open ended course are not compared with a course end`() {
        val stages = listOf(Stage(BigDecimal.ONE, days = 365))

        assertTrue(validate(Drafts.valid().copy(schedule = ScheduleRule.Staged(stages), endDate = null)).isEmpty())
    }

    @Test
    fun `the schedule rule of an as needed course is not judged`() {
        // Вида у «по потребности» нет (ПР-7): неприменимое значение в запись не уезжает
        // (его снимает effectiveSchedule), и правило вида не зовёт человека к блоку,
        // которого он не видит.
        val draft = Drafts.validPrn().copy(schedule = ScheduleRule.Weekdays(emptySet()))

        assertTrue(validate(draft.normalized()).isEmpty())
    }

    @Test
    fun `normalizing an as needed course keeps the chosen schedule in the draft`() {
        // §5.10: вид и его параметры сохраняются в черновике и возвращаются при возвращении
        // к «по расписанию» — та же конвенция, что у связи с едой.
        val normalized = Drafts.validPrn().copy(schedule = ScheduleRule.Cycles(21, 7)).normalized()

        assertEquals(ScheduleRule.Cycles(21, 7), normalized.schedule)
    }

    @Test
    fun `an as needed course never saves the chosen schedule into the record`() {
        // Но в запись вид не уезжает: у курса «по потребности» вида нет (ИНВ-25, ПР-7).
        val medication = Drafts.validPrn().copy(schedule = ScheduleRule.Cycles(21, 7)).normalized()
            .toMedication(id = "med")

        assertEquals(ScheduleRule.Daily, medication.schedule)
    }

    @Test
    fun `the schedule block is reported before the times block`() {
        // Блок вида расписания стоит в форме выше времён приёма, и экран прокручивается
        // к первому нарушению списка.
        val violations = validate(
            Drafts.valid().copy(times = emptyList(), schedule = ScheduleRule.Weekdays(emptySet())),
        )

        assertEquals(DraftField.SCHEDULE, violations.first().field)
        assertEquals(listOf(DraftField.SCHEDULE, DraftField.TIMES), violations.map { it.field })
    }

    private fun dose(value: String) = Drafts.valid().copy(doseAmount = BigDecimal(value))
}
